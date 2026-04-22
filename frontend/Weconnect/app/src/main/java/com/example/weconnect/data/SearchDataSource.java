package com.example.weconnect.data;

import com.example.weconnect.models.Post;
import java.util.List;

public interface SearchDataSource {
    List<Post> searchPosts(String keyword);
    List<String> searchUsers(String keyword);
}
