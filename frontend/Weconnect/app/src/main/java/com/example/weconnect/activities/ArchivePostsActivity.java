package com.example.weconnect.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.weconnect.R;
import com.example.weconnect.adapters.PostAdapter;
import com.example.weconnect.api.FirebaseManager;
import com.example.weconnect.api.FirestorePostRepository;
import com.example.weconnect.models.Post;
import com.google.firebase.Timestamp;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ArchivePostsActivity extends AppCompatActivity {

    private TextView tvArchiveEmpty;
    private RecyclerView rvArchivedPosts;
    private FirestorePostRepository postRepo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_archive_posts);

        postRepo = new FirestorePostRepository();

        ImageView ivBack   = findViewById(R.id.ivBackArchive);
        TextView tvTitle   = findViewById(R.id.tvArchiveTitle);
        tvArchiveEmpty     = findViewById(R.id.tvArchiveEmpty);
        rvArchivedPosts    = findViewById(R.id.rvArchivedPosts);

        tvTitle.setText("Kho lưu trữ");
        ivBack.setOnClickListener(v -> finish());

        loadArchivedPosts();
    }

    private void loadArchivedPosts() {
        // Lấy uid từ intent, hoặc dùng uid hiện tại
        String uid = getIntent().getStringExtra("user_uid");
        if (uid == null) uid = FirebaseManager.getCurrentUserId();
        if (uid == null) { showEmpty(); return; }

        final String finalUid = uid;
        postRepo.getUserArchivedPosts(finalUid, new FirestorePostRepository.PostsCallback() {
            @Override public void onSuccess(List<Map<String, Object>> posts) {
                List<Post> list = new ArrayList<>();
                Timestamp now = Timestamp.now();
                for (Map<String, Object> p : posts) {
                    try {
                        String id = (String) p.get("id");
                        String name = (String) p.get("authorName");
                        String content = (String) p.get("content");
                        String tag = (String) p.get("interestTag");
                        String location = (String) p.get("location");
                        int max = p.get("maxMembers") instanceof Number ? ((Number)p.get("maxMembers")).intValue() : 10;
                        int cnt = p.get("memberCount") instanceof Number ? ((Number)p.get("memberCount")).intValue() : 1;
                        Timestamp endTs = (Timestamp) p.get("endTime");
                        long endMs = endTs != null ? endTs.toDate().getTime() : 0;
                        boolean archived = Boolean.TRUE.equals(p.get("archived"));
                        list.add(new Post(id, name, "", content, tag, location,
                                0, 0, cnt, 0, 0, max, false,
                                System.currentTimeMillis(), endMs, archived));
                    } catch (Exception ignored) {}
                }
                runOnUiThread(() -> showPosts(list));
            }
            @Override public void onError(String err) {
                runOnUiThread(() -> showEmpty());
            }
        });
    }

    private void showPosts(List<Post> posts) {
        rvArchivedPosts.setLayoutManager(new LinearLayoutManager(this));
        rvArchivedPosts.setAdapter(new PostAdapter(this, posts));
        tvArchiveEmpty.setVisibility(posts.isEmpty() ? View.VISIBLE : View.GONE);
        rvArchivedPosts.setVisibility(posts.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void showEmpty() {
        tvArchiveEmpty.setVisibility(View.VISIBLE);
        rvArchivedPosts.setVisibility(View.GONE);
    }
}
