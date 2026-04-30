package com.example.weconnect.search.data;

import com.example.weconnect.post.data.Post;
import java.util.List;

public interface SearchDataSource {
    List<Post> searchPosts(String keyword);
    List<String> searchUsers(String keyword);
}
