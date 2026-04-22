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
import com.example.weconnect.api.PostApiService;
import com.example.weconnect.api.RetrofitClient;
import com.example.weconnect.data.FakePostRepository;
import com.example.weconnect.models.ApiResponse;
import com.example.weconnect.models.Post;
import com.example.weconnect.models.PostResponse;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ArchivePostsActivity extends AppCompatActivity {

    private ImageView ivBackArchive;
    private TextView tvArchiveTitle;
    private TextView tvArchiveEmpty;
    private RecyclerView rvArchivedPosts;
    private PostApiService postApiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_archive_posts);

        postApiService = RetrofitClient.getClient().create(PostApiService.class);
        initViews();
        setupClickListeners();
        bindArchivePosts();
    }

    private void initViews() {
        ivBackArchive = findViewById(R.id.ivBackArchive);
        tvArchiveTitle = findViewById(R.id.tvArchiveTitle);
        tvArchiveEmpty = findViewById(R.id.tvArchiveEmpty);
        rvArchivedPosts = findViewById(R.id.rvArchivedPosts);
    }

    private void setupClickListeners() {
        ivBackArchive.setOnClickListener(v -> finish());
    }

    private void bindArchivePosts() {
        String username = getIntent().getStringExtra("username");
        if (username == null || username.trim().isEmpty()) {
            username = FakePostRepository.getInstance().getCurrentUsername();
        }
        tvArchiveTitle.setText("Kho lưu trữ");

        long userId = getIntent().getLongExtra("user_id", -1);
        if (userId <= 0) {
            // Fallback: try shared prefs
            android.content.SharedPreferences prefs = getSharedPreferences("weconnect_prefs", MODE_PRIVATE);
            userId = prefs.getLong("user_id", -1);
        }

        if (userId <= 0) {
            // No valid user ID, fallback to fake data
            showArchivedPosts(FakePostRepository.getInstance().getArchivedPostsForUser(username));
            return;
        }

        final String finalUsername = username;
        postApiService.getUserArchivedPosts(userId).enqueue(new Callback<ApiResponse<List<PostResponse>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<PostResponse>>> call,
                                   Response<ApiResponse<List<PostResponse>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    List<PostResponse> responses = response.body().getResult();
                    List<Post> archivedPosts = new ArrayList<>();
                    if (responses != null) {
                        for (PostResponse pr : responses) {
                            archivedPosts.add(pr.toPost());
                        }
                    }
                    showArchivedPosts(archivedPosts);
                } else {
                    showArchivedPosts(FakePostRepository.getInstance().getArchivedPostsForUser(finalUsername));
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<PostResponse>>> call, Throwable t) {
                showArchivedPosts(FakePostRepository.getInstance().getArchivedPostsForUser(finalUsername));
            }
        });
    }

    private void showArchivedPosts(List<Post> archivedPosts) {
        rvArchivedPosts.setLayoutManager(new LinearLayoutManager(this));
        rvArchivedPosts.setAdapter(new PostAdapter(this, archivedPosts));
        tvArchiveEmpty.setVisibility(archivedPosts.isEmpty() ? View.VISIBLE : View.GONE);
        rvArchivedPosts.setVisibility(archivedPosts.isEmpty() ? View.GONE : View.VISIBLE);
    }
}
