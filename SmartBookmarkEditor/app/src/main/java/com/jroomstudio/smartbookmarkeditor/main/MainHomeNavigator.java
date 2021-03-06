package com.jroomstudio.smartbookmarkeditor.main;

import com.jroomstudio.smartbookmarkeditor.data.bookmark.Bookmark;
import com.jroomstudio.smartbookmarkeditor.data.category.Category;

import java.util.List;

/**
 * 아이템 추가 화면에서 호출 할 수있는 탐색 동작을 정의
 **/
public interface MainHomeNavigator {
    void addNewItems(List<Category> categories);
    void setToolbarTitle(String title);
    void editCategory(Category category, List<Category> categories);
    void editBookmark(Bookmark bookmark, List<Category> categories);

}
