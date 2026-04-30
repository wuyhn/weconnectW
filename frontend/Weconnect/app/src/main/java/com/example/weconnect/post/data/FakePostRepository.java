package com.example.weconnect.post.data;

import com.example.weconnect.R;
import com.example.weconnect.post.data.Post;

import java.util.ArrayList;
import java.util.List;

public class FakePostRepository {

    private static final long ONE_HOUR = 60L * 60L * 1000L;
    private static final long ONE_DAY = 24L * ONE_HOUR;
    private String currentUsername = "Quỳnh Nguyễn";

    private static FakePostRepository instance;

    private final List<Post> allPosts = new ArrayList<>();
    private List<String> userInterests = new ArrayList<>();

    public void setUserInterests(List<String> interests) {
        this.userInterests = new ArrayList<>(interests);
    }

    public List<String> getUserInterests() {
        return new ArrayList<>(userInterests);
    }

    private FakePostRepository() {
        seedPosts();
    }

    public static synchronized FakePostRepository getInstance() {
        if (instance == null) {
            instance = new FakePostRepository();
        }
        return instance;
    }

    public static synchronized void resetInstance() {
        instance = null;
    }

    public List<Post> getActivePosts() {
        List<Post> activePosts = new ArrayList<>();
        for (Post post : allPosts) {
            if (post.isActive()) {
                activePosts.add(post);
            }
        }
        return activePosts;
    }

    public List<Post> getAllPosts() {
        return new ArrayList<>(allPosts);
    }

    public List<Post> getArchivedPostsForUser(String username) {
        List<Post> archivedPosts = new ArrayList<>();
        for (Post post : allPosts) {
            boolean sameUser = post.getUsername() != null
                    && post.getUsername().equalsIgnoreCase(username);
            if (sameUser && (post.isArchived() || post.isExpired())) {
                archivedPosts.add(post);
            }
        }
        return archivedPosts;
    }

    public void addPost(Post post) {
        allPosts.add(0, post);
    }

    public boolean removePost(String id) {
        java.util.Iterator<Post> iterator = allPosts.iterator();
        while (iterator.hasNext()) {
            Post post = iterator.next();
            if (post.getId() != null && post.getId().equals(id)) {
                iterator.remove();
                return true;
            }
        }
        return false;
    }

    public String getCurrentUsername() {
        return currentUsername;
    }

    public void setCurrentUsername(String username) {
        this.currentUsername = username;
    }

    public List<Post> getActivePostsForUser(String username) {
        List<Post> activePosts = new ArrayList<>();
        for (Post post : allPosts) {
            boolean sameUser = post.getUsername() != null
                    && post.getUsername().equalsIgnoreCase(username);
            if (sameUser && post.isActive()) {
                activePosts.add(post);
            }
        }
        return activePosts;
    }

    private void seedPosts() {
        // Không seed fake data - chỉ dùng dữ liệu thật từ backend
    }
}
