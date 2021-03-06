package com.jroomstudio.smartbookmarkeditor.data.bookmark.source.local;


import androidx.annotation.NonNull;

import com.jroomstudio.smartbookmarkeditor.data.bookmark.Bookmark;

import java.util.List;

/**
 * Bookmark 데이터에 액세스 하기 위한 진입점
 **/
public interface BookmarksLocalDataSource {

    /**
     * bookmark 데이터베이스에서 리스트 전체를 가져올 때 callback 하는 메소드
     * - getBookmarks() 호출할때 2가지 메소드를 오버라이딩 하게된다.
     * onBookmarksLoaded(List<Bookmark>)
     * - 데이터베이스에서 Bookmark 리스트를 로드하는데 성공했을 경우를 리스트로 입력해 반환한다.
     * onDataNotAvailable()
     * - 어떠한 이유로 로드에 실패했을 경우 구현한다.
     **/
    interface LoadBookmarksCallback {
        void onBookmarksLoaded(List<Bookmark> bookmarks);
        void onDataNotAvailable();
    }

    /**
     * bookmark 데이터베이스에서 하나의 객체를 가져올 때 callback 하는 메소드
     * - getBookmark() 를 구현할때 2가지 메소드를 오버라이딩 해야된다.
     * onBookmarkLoaded(Bookmark)
     * - 데이터베이스에서 Bookmark 객체를 로드하는데 성공했을 경우 객체를 담아 반환한다.
     * onDataNotAvailable()
     * - 어떠한 이유로 로드에 실패했을 경우 구현한다.
     **/
    interface GetBookmarkCallback {
        void onBookmarkLoaded(Bookmark bookmark);
        void onDataNotAvailable();
    }

    // 입력된 카테고리의 Bookmark 리스트를 데이터베이스에서 가져온다.
    void getBookmarks(@NonNull String category, @NonNull LoadBookmarksCallback callback);

    // Bookmark 객체를 데이터베이스에서 가져온다
    void getBookmark(@NonNull String id, @NonNull GetBookmarkCallback callback);

    // Bookmark 객체를 데이터베이스에 저장
    void saveBookmark(@NonNull Bookmark bookmark);

    // 입력된 id의 Bookmark 객체를 찾아서 제거
    void deleteBookmark(@NonNull String id);

    // 입력된 카테고리의 bookmark 전부 제거
    void deleteAllInCategory(@NonNull String category);

    // 입력된 Bookmark 객체의 position 값을 입력된 position 값으로 변경
    void updatePosition(@NonNull Bookmark bookmark, int position);

}
