package com.example.weconnect.data;

import com.example.weconnect.domain.model.Post;

import java.util.List;

public class SearchRepository {

    private final SearchDataSource searchDataSource;

    public SearchRepository(SearchDataSource searchDataSource) {
        this.searchDataSource = searchDataSource;
    }

    public List<Post> searchPosts(String keyword) {
        return searchDataSource.searchPosts(keyword);
    }

    public List<String> searchUsers(String keyword) {
        return searchDataSource.searchUsers(keyword);
    }
}