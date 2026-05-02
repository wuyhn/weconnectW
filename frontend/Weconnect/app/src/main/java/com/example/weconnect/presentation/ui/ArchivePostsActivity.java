package com.example.weconnect.presentation.ui;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.weconnect.presentation.adapter.PostAdapter;
import com.example.weconnect.databinding.ActivityArchivePostsBinding;
import com.example.weconnect.data.repository.FirebaseManager;
import com.example.weconnect.domain.model.Post;
import com.example.weconnect.presentation.viewmodel.ProfileViewModel;

import java.util.List;

public class ArchivePostsActivity extends AppCompatActivity {

    private ActivityArchivePostsBinding binding;
    private ProfileViewModel profileViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityArchivePostsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        profileViewModel = new ViewModelProvider(this).get(ProfileViewModel.class);

        binding.tvArchiveTitle.setText("Kho lưu trữ");
        binding.ivBackArchive.setOnClickListener(v -> finish());

        setupObservers();
        loadArchivedPosts();
    }

    private void setupObservers() {
        profileViewModel.archivedPosts.observe(this, posts -> {
            if (posts != null && !posts.isEmpty()) {
                showPosts(posts);
            } else {
                showEmpty();
            }
        });
    }

    private void loadArchivedPosts() {
        String uid = getIntent().getStringExtra("user_uid");
        if (uid == null) uid = FirebaseManager.getCurrentUserId();
        if (uid == null) { 
            showEmpty(); 
            return; 
        }

        profileViewModel.loadUserArchivedPosts(uid);
    }

    private void showPosts(List<Post> posts) {
        binding.rvArchivedPosts.setLayoutManager(new LinearLayoutManager(this));
        binding.rvArchivedPosts.setAdapter(new PostAdapter(this, posts));
        binding.tvArchiveEmpty.setVisibility(posts.isEmpty() ? View.VISIBLE : View.GONE);
        binding.rvArchivedPosts.setVisibility(posts.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void showEmpty() {
        binding.tvArchiveEmpty.setVisibility(View.VISIBLE);
        binding.rvArchivedPosts.setVisibility(View.GONE);
    }
}
