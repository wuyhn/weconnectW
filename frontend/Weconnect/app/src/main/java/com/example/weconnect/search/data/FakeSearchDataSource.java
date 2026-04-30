package com.example.weconnect.search.data;

import com.example.weconnect.post.data.Post;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FakeSearchDataSource implements SearchDataSource {

    private final List<Post> postList;

    public FakeSearchDataSource(List<Post> postList) {
        this.postList = postList;
    }

    @Override
    public List<Post> searchPosts(String keyword) {
        List<Post> result = new ArrayList<>();

        if (keyword == null) {
            return result;
        }

        String normalizedKeyword = keyword.trim().toLowerCase();

        if (normalizedKeyword.isEmpty()) {
            return result;
        }

        for (Post post : postList) {
            String username = post.getUsername() != null ? post.getUsername().toLowerCase() : "";
            String content = post.getContent() != null ? post.getContent().toLowerCase() : "";
            String interestTag = post.getInterestTag() != null ? post.getInterestTag().toLowerCase() : "";
            String location = post.getLocation() != null ? post.getLocation().toLowerCase() : "";

            boolean isMatched =
                    username.contains(normalizedKeyword) ||
                            content.contains(normalizedKeyword) ||
                            interestTag.contains(normalizedKeyword) ||
                            location.contains(normalizedKeyword);

            if (isMatched && post.isActive()) {
                result.add(post);
            }
        }

        return result;
    }

    @Override
    public List<String> searchUsers(String keyword) {
        List<String> result = new ArrayList<>();

        if (keyword == null) {
            return result;
        }

        String normalizedKeyword = keyword.trim().toLowerCase();

        if (normalizedKeyword.isEmpty()) {
            return result;
        }

        Set<String> uniqueUsers = new HashSet<>();

        for (Post post : postList) {
            String username = post.getUsername() != null ? post.getUsername() : "";
            if (username.toLowerCase().contains(normalizedKeyword) && !uniqueUsers.contains(username)) {
                uniqueUsers.add(username);
                result.add(username);
            }
        }

        return result;
    }
}