package com.jroomstudio.smartbookmarkeditor.data.bookmark.source.remote;

import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;

import com.jroomstudio.smartbookmarkeditor.data.bookmark.Bookmark;
import com.jroomstudio.smartbookmarkeditor.data.category.Category;
import com.jroomstudio.smartbookmarkeditor.data.member.JsonWebToken;
import com.jroomstudio.smartbookmarkeditor.data.member.Member;
import com.jroomstudio.smartbookmarkeditor.util.AppExecutors;
import com.jroomstudio.smartbookmarkeditor.util.NetRetrofitService;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * 네트워크 지연시간 시뮬레이션을 테스트하는 데이터소스 구현
 **/
public class BookmarksRemoteRepository implements BookmarksRemoteDataSource {

    /**
     * - BookmarksRemoteDataSource 의 INSTANCE
     **/
    private static BookmarksRemoteRepository INSTANCE;

    // 레트로핏 인스턴스
    // private NetRetrofit mNetRetrofit;
    private NetRetrofitService mNetRetrofitService;

    // 쓰레드
    private AppExecutors mAppExecutors;

    // 액티비티 상태저장 Shared Preferences
    private SharedPreferences spActStatus;

    // 다이렉트 인스턴스 방지
    private BookmarksRemoteRepository(@NonNull AppExecutors appExecutors,
                                      @NonNull SharedPreferences sharedPreferences) {
        mAppExecutors = appExecutors;
        spActStatus = sharedPreferences;
        // 레트로핏 인스턴스 생성
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(NetRetrofitService.SERVER_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        mNetRetrofitService = retrofit.create(NetRetrofitService.class);
    }

    /**
     * - TabsRemoteDataSource 인스턴스 생성 메소드
     * - INSTANCE null 체크
     * - null 이면 new 키워드로 private 생성자로 인스턴스를 생성한다.
     * - 생성된 인스턴스를 반환한다.
     **/
    public static BookmarksRemoteRepository getInstance(@NonNull AppExecutors appExecutors,
                                                        @NonNull SharedPreferences sharedPreferences) {
        if(INSTANCE == null){
            INSTANCE = new BookmarksRemoteRepository(appExecutors,sharedPreferences);
        }
        return INSTANCE;
    }
    /**
     * 토큰 기간 만료 시 재발행
    **/
    @Override
    public void refreshToken(@NonNull RefreshTokenCallback callback) {
        Runnable runnable = () ->{
            String email = spActStatus.getString("member_email","");
            String password = spActStatus.getString("auto_password","");
            Member member = new Member(
                    email,"","",
                    password,false,false,
                    0
            );
            mNetRetrofitService.refreshTokenCallback("application/json",member)
                    .enqueue(new Callback<JsonWebToken>() {
                        @Override
                        public void onResponse(Call<JsonWebToken> call, Response<JsonWebToken> response) {
                            // JWT 콜백
                            //Log.e("jwt 리프래쉬","성공");
                            Log.e("JWT Refresh",response.code()+"");
                            if(response.code()==200){
                                // 로그인 성공
                                // 토큰을 전달하여 저장한다.
                                SharedPreferences.Editor editor = spActStatus.edit();
                                editor.putString("jwt",response.body().getJwt());
                                editor.apply();
                                callback.onRefreshTokenCallback();
                            }
                        }

                        @Override
                        public void onFailure(Call<JsonWebToken> call, Throwable t) {
                            // 사용안함
                        }
                    });
        };
        mAppExecutors.getNetworkIO().execute(runnable);
    }

    /**
     * 북마크 리스트 가져오기
     **/
    @Override
    public void getBookmarks(@NonNull String category,
                                          @NonNull LoadBookmarksCallback callback) {
        Runnable runnable = () ->
            mNetRetrofitService.getBookmarks(
                    "Bearer "+spActStatus.getString("jwt",""),
                    spActStatus.getString("member_email",""),
                    category)
                    .enqueue(new Callback<List<Bookmark>>() {
                        @Override
                        public void onResponse(Call<List<Bookmark>> call,
                                               Response<List<Bookmark>> response) {
                            // 성공
                            Log.e("getBookmarks()",response.code()+"");
                            if(response.code()==200){
                                // Log.e("header", response.headers()+"");
                                // Log.e("body", response.body()+"");
                                callback.onBookmarksLoaded(response.body());
                            } else if(response.code()==401){
                                // 실패시 jwt 재발급 받고 다시시도
                                refreshToken(new RefreshTokenCallback() {
                                    @Override
                                    public void onRefreshTokenCallback() {
                                        mNetRetrofitService.getBookmarks(
                                                "Bearer "+spActStatus.getString("jwt",""),
                                                spActStatus.getString("member_email",""),
                                                category).enqueue(new Callback<List<Bookmark>>() {
                                            @Override
                                            public void onResponse(Call<List<Bookmark>> call,
                                                                   Response<List<Bookmark>> response) {
                                                // 성공
                                                Log.e("reGetBookmarks()",response.code()+"");
                                                if(response.code()==200){
                                                    //Log.e("header", response.headers()+"");
                                                    //Log.e("body", response.body()+"");
                                                    callback.onBookmarksLoaded(response.body());
                                                }else if(response.code()==402){
                                                    // bookmark 리스트에 아무것도 없을 때
                                                    callback.onDataNotAvailable();
                                                }
                                            }

                                            @Override
                                            public void onFailure(Call<List<Bookmark>> call, Throwable t) {
                                                // 가져오기 실패
                                                Log.e("Get Bookmarks onFailure",t.getMessage());
                                                callback.onDataNotAvailable();
                                            }
                                        });
                                    }

                                    @Override
                                    public void refreshTokenFailed() {
                                        // 데이터 없음
                                        //callback.onDataNotAvailable();
                                        callback.onDataNotAvailable();
                                    }
                                });
                            } else if(response.code()==402){
                                // bookmark 리스트에 아무것도 없을 때
                                callback.onDataNotAvailable();
                            }
                        }

                        @Override
                        public void onFailure(Call<List<Bookmark>> call, Throwable t) {
                            // 가져오기 실패
                            Log.e("Get Bookmarks onFailure",t.getMessage());
                            callback.onDataNotAvailable();
                        }
                    });
        mAppExecutors.getNetworkIO().execute(runnable);
    }

    /**
     * 북마크 저장
     **/
    @Override
    public void saveBookmark(@NonNull Bookmark bookmark) {
        Runnable runnable = () -> {
            mNetRetrofitService.saveBookmark(
                    "Bearer "+spActStatus.getString("jwt",""),
                    "application/json",
                    spActStatus.getString("member_email",""),
                    bookmark)
                    .enqueue(new Callback<Void>() {
                        @Override
                        public void onResponse(Call<Void> call, Response<Void> response) {
                            // 저장 성공
                            Log.e("saveBookmark()1",response.code()+"");
                            if(response.code()==200){
                                Log.e("save success", "북마크 저장 성공");
                                //Log.e("body", response.body()+"");
                            } else if(response.code()==401){
                                // 실패시 jwt 재발급 받고 다시시도
                                refreshToken(new RefreshTokenCallback() {
                                    @Override
                                    public void onRefreshTokenCallback() {
                                        saveBookmark(bookmark);
                                    }

                                    @Override
                                    public void refreshTokenFailed() {
                                        //
                                    }
                                });
                            }
                        }

                        @Override
                        public void onFailure(Call<Void> call, Throwable t) {
                            // 저장 실패
                            Log.e("Save onFailure",t.getMessage());
                        }
                    });
        };
        mAppExecutors.getNetworkIO().execute(runnable);
    }

    /**
     * 북마크 삭제
     **/
    @Override
    public void deleteBookmark(@NonNull String id,
                               @NonNull String category,
                               @NonNull UpdateCallback callback) {
        Runnable runnable = () ->{
            mNetRetrofitService.deleteBookmark(
                    "Bearer "+spActStatus.getString("jwt",""),
                    spActStatus.getString("member_email",""),
                    id,
                    category)
                    .enqueue(new Callback<Void>() {
                        @Override
                        public void onResponse(Call<Void> call, Response<Void> response) {
                            // 삭제 성공
                            if(response.code()==200){
                                Log.e("delete success", "북마크 삭제 성공");
                                callback.onCompletedUpdate();
                                //Log.e("body", response.body()+"");
                            } else if(response.code()==401){
                                // 실패시 jwt 재발급 받고 다시시도
                                refreshToken(new RefreshTokenCallback() {
                                    @Override
                                    public void onRefreshTokenCallback() {
                                        mNetRetrofitService.deleteBookmark(
                                                "Bearer "+spActStatus.getString("jwt",""),
                                                spActStatus.getString("member_email",""),
                                                id,
                                                category
                                        );
                                        callback.onCompletedUpdate();
                                    }

                                    @Override
                                    public void refreshTokenFailed() {
                                        // 삭제실패
                                        callback.onFailedUpdate();
                                    }
                                });
                            }
                        }

                        @Override
                        public void onFailure(Call<Void> call, Throwable t) {
                            // 삭제 실패
                            Log.e("Delete onFailure",t.getMessage());
                            callback.onFailedUpdate();
                        }
                    });
        };
        mAppExecutors.getNetworkIO().execute(runnable);
    }

    /**
    * 카테고리 삭제
    **/
    @Override
    public void deleteCategory(@NonNull String category, @NonNull UpdateCallback callback) {
        Runnable runnable = () ->
            mNetRetrofitService.deleteCategory(
                    "Bearer "+spActStatus.getString("jwt",""),
                    spActStatus.getString("member_email",""),
                    category)
                    .enqueue(new Callback<Void>() {
                        @Override
                        public void onResponse(Call<Void> call, Response<Void> response) {
                            // 삭제 성공
                            if(response.code()==200){
                                Log.e("delete success", "카테고리 삭제 성공");
                                callback.onCompletedUpdate();
                                //Log.e("body", response.body()+"");
                            } else if(response.code()==401){
                                // 실패시 jwt 재발급 받고 다시시도
                                refreshToken(new RefreshTokenCallback() {
                                    @Override
                                    public void onRefreshTokenCallback() {
                                        mNetRetrofitService.deleteCategory(
                                                "Bearer "+spActStatus.getString("jwt",""),
                                                spActStatus.getString("member_email",""),
                                                category
                                        );
                                        callback.onCompletedUpdate();
                                    }

                                    @Override
                                    public void refreshTokenFailed() {
                                        // 삭제실패
                                        callback.onFailedUpdate();
                                    }
                                });
                            }
                        }

                        @Override
                        public void onFailure(Call<Void> call, Throwable t) {
                            // 삭제 실패
                            Log.e("Delete onFailure",t.getMessage());
                            callback.onFailedUpdate();
                        }
                    });
        mAppExecutors.getNetworkIO().execute(runnable);
    }

    /**
     * 카테고리 저장
     */
    @Override
    public void saveCategory(@NonNull Category category) {
        Runnable runnable = () ->
            mNetRetrofitService.saveCategory(
                    "Bearer "+spActStatus.getString("jwt",""),
                    "application/json",
                    spActStatus.getString("member_email",""),
                    category).enqueue(new Callback<Void>() {
                @Override
                public void onResponse(Call<Void> call, Response<Void> response) {
                    Log.e("saveCategory()1",response.code()+"");
                    if(response.code()==200){
                        Log.e("save success", "카테고리 저장 성공");
                        //Log.e("body", response.body()+"");
                    } else if(response.code()==401){
                        // 실패시 jwt 재발급 받고 다시시도
                        refreshToken(new RefreshTokenCallback() {
                            @Override
                            public void onRefreshTokenCallback() {
                                saveCategory(category);
                            }

                            @Override
                            public void refreshTokenFailed() {
                                //
                            }
                        });
                    }
                }

                @Override
                public void onFailure(Call<Void> call, Throwable t) {
                    // 저장실패
                    Log.e("SaveCategoryFailure",t.getMessage());
                }
            });
        mAppExecutors.getNetworkIO().execute(runnable);
    }

    /**
     * 카테고리 데이터 가져오기
     **/
    @Override
    public void getAllCategories(@NonNull LoadCategoriesCallback callback) {
        Runnable runnable = () ->
            mNetRetrofitService.getAllCategories(
                    "Bearer "+spActStatus.getString("jwt",""),
                    spActStatus.getString("member_email",""))
                    .enqueue(new Callback<List<Category>>() {
                        @Override
                        public void onResponse(Call<List<Category>> call,
                                               Response<List<Category>> response) {
                            Log.e("getAllCategory()",response.code()+"");
                            //성공
                            if(response.code()==200){
                                //Log.e("header", response.headers()+"");
                                //Log.e("body", response.body()+"");
                                callback.onCategoriesLoaded(response.body());
                            } else if(response.code()==401){
                                // 실패시 jwt 재발급 받고 다시시도
                                refreshToken(new RefreshTokenCallback() {
                                    @Override
                                    public void onRefreshTokenCallback() {
                                        //Log.e("refreshAllCategory","refresh");
                                        mNetRetrofitService.getAllCategories(
                                                "Bearer "+spActStatus.getString("jwt",""),
                                                spActStatus.getString("member_email",""))
                                                .enqueue(new Callback<List<Category>>() {
                                                    @Override
                                                    public void onResponse(Call<List<Category>> call,
                                                                           Response<List<Category>> response) {
                                                        Log.e("reAllCategory()",response.code()+"");
                                                        if(response.code()==200){
                                                            //Log.e("header", response.headers()+"");
                                                            //Log.e("body", response.body()+"");
                                                            callback.onCategoriesLoaded(response.body());
                                                        } else if(response.code()==402){
                                                            // bookmark 리스트에 아무것도 없을 때
                                                            Log.e("getAllCategory","category null");
                                                            callback.onDataNotAvailable();
                                                        }
                                                    }

                                                    @Override
                                                    public void onFailure(Call<List<Category>> call, Throwable t) {
                                                        Log.e("getAllCategoryFailure",t.getMessage());
                                                        callback.onDataNotAvailable();
                                                    }
                                                });
                                    }

                                    @Override
                                    public void refreshTokenFailed() {
                                        // 데이터 없음
                                        //callback.onDataNotAvailable();
                                        callback.onDataNotAvailable();
                                    }
                                });
                            } else if(response.code()==402){
                                // bookmark 리스트에 아무것도 없을 때
                                Log.e("getAllCategory","category null");
                                callback.onDataNotAvailable();
                            }
                        }

                        @Override
                        public void onFailure(Call<List<Category>> call, Throwable t) {
                            // 실패
                            Log.e("getAllCategoryFailure",t.getMessage());
                            callback.onDataNotAvailable();
                        }
                    });
        mAppExecutors.getNetworkIO().execute(runnable);
    }

    /**
     * 카테고리 셀렉트
     **/
    @Override
    public void selectedCategory(@NonNull String category, @NonNull UpdateCallback callback) {
        Runnable runnable = () ->
            mNetRetrofitService.selectedCategory(
                    "Bearer "+spActStatus.getString("jwt",""),
                    spActStatus.getString("member_email",""),
                    category
            ).enqueue(new Callback<Void>() {
                @Override
                public void onResponse(Call<Void> call, Response<Void> response) {
                    // 성공
                    Log.e("selectedCategory()",response.code()+"");
                    if(response.code()==200){
                        Log.e("update success", "카테고리 선택 변경");
                        callback.onCompletedUpdate();
                    }else if(response.code()==401){
                        // 실패시 jwt 재발급 받고 다시시도
                        refreshToken(new RefreshTokenCallback() {
                            @Override
                            public void onRefreshTokenCallback() {
                               mNetRetrofitService.selectedCategory(
                                       "Bearer "+spActStatus.getString("jwt",""),
                                       spActStatus.getString("member_email",""),
                                       category
                               );
                               callback.onCompletedUpdate();
                            }

                            @Override
                            public void refreshTokenFailed() {
                                // 데이터 없음
                                //callback.onDataNotAvailable();
                                callback.onFailedUpdate();
                            }
                        });
                    }
                }

                @Override
                public void onFailure(Call<Void> call, Throwable t) {
                    // 실패
                    Log.e("selectCategoryFailure",t.getMessage());
                    callback.onFailedUpdate();
                }
            });
        mAppExecutors.getNetworkIO().execute(runnable);
    }

    /**
     * 카테고리 업데이트
     **/
    @Override
    public void updateCategory(@NonNull String id, @NonNull String title, @NonNull UpdateCallback callback) {
        Runnable runnable = () -> {
            mNetRetrofitService.updateCategory(
                    "Bearer "+spActStatus.getString("jwt",""),
                    spActStatus.getString("member_email",""),
                    id,title).enqueue(new Callback<Void>() {
                @Override
                public void onResponse(Call<Void> call, Response<Void> response) {
                    // 성공
                    Log.e("updateCategory()",response.code()+"");
                    if(response.code()==200){
                        Log.e("update success", "카테고리 업데이트");
                        callback.onCompletedUpdate();
                    }else if(response.code()==401){
                        // 실패시 jwt 재발급 받고 다시시도
                        refreshToken(new RefreshTokenCallback() {
                            @Override
                            public void onRefreshTokenCallback() {
                                mNetRetrofitService.updateCategory(
                                        "Bearer "+spActStatus.getString("jwt",""),
                                        spActStatus.getString("member_email",""),
                                        id,title);
                                callback.onCompletedUpdate();
                            }

                            @Override
                            public void refreshTokenFailed() {
                                // 데이터 없음
                                //callback.onDataNotAvailable();
                                callback.onFailedUpdate();
                            }
                        });
                    }
                }

                @Override
                public void onFailure(Call<Void> call, Throwable t) {
                    // 실패
                    Log.e("updateCategoryFailure",t.getMessage());
                    callback.onFailedUpdate();
                }
            });
        };
        mAppExecutors.getNetworkIO().execute(runnable);
    }

    /**
     * 북마크 업데이트
    **/
    @Override
    public void updateBookmark(@NonNull String updateCategory,
                               @NonNull String id,
                               @NonNull String title,
                               @NonNull String url,
                               @NonNull String category,
                               @NonNull String favicon,@NonNull UpdateCallback callback) {
        Runnable runnable = () ->{
            mNetRetrofitService.updateBookmark(
                    "Bearer "+spActStatus.getString("jwt",""),
                    spActStatus.getString("member_email",""),
                    updateCategory,
                    id,title,url,category,favicon
            ).enqueue(new Callback<Void>() {
                @Override
                public void onResponse(Call<Void> call, Response<Void> response) {
                    Log.e("updateBookmark()",response.code()+"");
                    if(response.code()==200){
                        Log.e("update success", "북마크 업데이트");
                        callback.onCompletedUpdate();
                        //Log.e("body", response.body()+"");
                    } else if(response.code()==401){
                        // 실패시 jwt 재발급 받고 다시시도
                        refreshToken(new RefreshTokenCallback() {
                            @Override
                            public void onRefreshTokenCallback() {
                                mNetRetrofitService.updateBookmark(
                                        "Bearer "+spActStatus.getString("jwt",""),
                                        spActStatus.getString("member_email",""),
                                        updateCategory,
                                        id,title,url,category,favicon);
                                callback.onCompletedUpdate();
                            }

                            @Override
                            public void refreshTokenFailed() {
                                //
                                callback.onFailedUpdate();
                            }
                        });
                    }
                }

                @Override
                public void onFailure(Call<Void> call, Throwable t) {
                    // 실패
                    Log.e("updateBookmarkFailure",t.getMessage());
                    callback.onFailedUpdate();
                }
            });
        };
        mAppExecutors.getNetworkIO().execute(runnable);
    }

    @Override
    public void updateBookmarkPosition(@NonNull List<Bookmark> bookmarks, @NonNull UpdateCallback callback) {
        Runnable runnable = () -> {
            mNetRetrofitService.updateBookmarkPosition(
                    "Bearer "+spActStatus.getString("jwt",""),
                    "application/json",
                    spActStatus.getString("member_email",""),
                    bookmarks
            ).enqueue(new Callback<Void>() {
                @Override
                public void onResponse(Call<Void> call, Response<Void> response) {
                    Log.e("bookmarkUpdate",response.code()+"");
                    if(response.code()==200){
                        Log.e("update success", "북마크 포지션 업데이트");
                        callback.onCompletedUpdate();
                    } else if(response.code()==401){
                        // 실패시 jwt 재발급 받고 다시시도
                        refreshToken(new RefreshTokenCallback() {
                            @Override
                            public void onRefreshTokenCallback() {
                                mNetRetrofitService.updateBookmarkPosition(
                                        "Bearer "+spActStatus.getString("jwt",""),
                                        "application/json",
                                        spActStatus.getString("member_email",""),
                                        bookmarks
                                );
                                callback.onCompletedUpdate();
                            }

                            @Override
                            public void refreshTokenFailed() {
                                //
                                callback.onFailedUpdate();
                            }
                        });
                    }
                }

                @Override
                public void onFailure(Call<Void> call, Throwable t) {
                    // 실패
                    Log.e("updateFailure",t.getMessage());
                }
            });
        };
        mAppExecutors.getNetworkIO().execute(runnable);
    }

    @Override
    public void updateCategoryPosition(@NonNull List<Category> categories, @NonNull UpdateCallback callback) {
        Runnable runnable = () -> {
            mNetRetrofitService.updateCategoryPosition(
                    "Bearer "+spActStatus.getString("jwt",""),
                    "application/json",
                    spActStatus.getString("member_email",""),
                    categories
            ).enqueue(new Callback<Void>() {
                @Override
                public void onResponse(Call<Void> call, Response<Void> response) {
                    Log.e("getBookmark()",response.code()+"");
                    if(response.code()==200){
                        Log.e("update success", "카테고리 포지션 업데이트");
                        callback.onCompletedUpdate();
                        //Log.e("body", response.body()+"");
                    } else if(response.code()==401){
                        // 실패시 jwt 재발급 받고 다시시도
                        refreshToken(new RefreshTokenCallback() {
                            @Override
                            public void onRefreshTokenCallback() {
                                mNetRetrofitService.updateCategoryPosition(
                                        "Bearer "+spActStatus.getString("jwt",""),
                                        "application/json",
                                        spActStatus.getString("member_email",""),
                                        categories
                                );
                                callback.onCompletedUpdate();
                            }

                            @Override
                            public void refreshTokenFailed() {
                                //
                                callback.onFailedUpdate();
                            }
                        });
                    }
                }

                @Override
                public void onFailure(Call<Void> call, Throwable t) {
                    // 실패
                    Log.e("updateFailure",t.getMessage());
                }
            });
        };
        mAppExecutors.getNetworkIO().execute(runnable);
    }

    /**
     * 북마크 가져오기
     **/
    @Override
    public void getBookmark(@NonNull String categoryTitle, @NonNull String bookmarkId, @NonNull GetBookmarkCallback callback) {
        Runnable runnable = () -> {
            mNetRetrofitService.getBookmark(
                    "Bearer "+spActStatus.getString("jwt",""),
                    spActStatus.getString("member_email",""),
                    categoryTitle,
                    bookmarkId
            ).enqueue(new Callback<Bookmark>() {
                @Override
                public void onResponse(Call<Bookmark> call, Response<Bookmark> response) {
                    Log.e("getBookmark()",response.code()+"");
                    if(response.code()==200){
                        Log.e("get success", "북마크 져오기");
                        callback.onBookmarkLoaded(response.body());
                        //Log.e("body", response.body()+"");
                    } else if(response.code()==401){
                        // 실패시 jwt 재발급 받고 다시시도
                        refreshToken(new RefreshTokenCallback() {
                            @Override
                            public void onRefreshTokenCallback() {
                                mNetRetrofitService.getBookmark(
                                        "Bearer "+spActStatus.getString("jwt",""),
                                        spActStatus.getString("member_email",""),
                                        categoryTitle,
                                        bookmarkId
                                ).enqueue(new Callback<Bookmark>() {
                                    @Override
                                    public void onResponse(Call<Bookmark> call, Response<Bookmark> response) {
                                        if(response.code()==200){
                                            Log.e("get success", "북마크 져오기");
                                            callback.onBookmarkLoaded(response.body());
                                        }else{
                                            callback.onDataNotAvailable();
                                        }
                                    }

                                    @Override
                                    public void onFailure(Call<Bookmark> call, Throwable t) {
                                        // 실패
                                        Log.e("getBookmarkFailure",t.getMessage());
                                        callback.onDataNotAvailable();
                                    }
                                });
                            }

                            @Override
                            public void refreshTokenFailed() {
                                //
                                callback.onDataNotAvailable();
                            }
                        });
                    }
                }

                @Override
                public void onFailure(Call<Bookmark> call, Throwable t) {
                    // 실패
                    Log.e("getBookmarkFailure",t.getMessage());
                    callback.onDataNotAvailable();
                }
            });
        };
        mAppExecutors.getNetworkIO().execute(runnable);
    }

}
