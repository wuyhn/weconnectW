package com.example.weconnect.presentation.ui;
import com.example.weconnect.presentation.ui.*;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.weconnect.R;
import com.example.weconnect.presentation.adapter.PostAdapter;
import com.example.weconnect.data.repository.FirebaseManager;
import com.example.weconnect.data.repository.FirestoreFriendRepository;
import com.example.weconnect.data.repository.FirestoreNotificationRepository;
import com.example.weconnect.data.repository.FirestorePostRepository;
import com.example.weconnect.domain.model.Post;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import androidx.lifecycle.ViewModelProvider;
import com.example.weconnect.databinding.ActivityMainBinding;
import com.example.weconnect.presentation.viewmodel.MainViewModel;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private ActivityMainBinding binding;
    private MainViewModel mainViewModel;
    private PostAdapter postAdapter;
    private List<Post> postList;

    private ActivityResultLauncher<Intent> createPostLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Kiểm tra đăng nhập
        if (!FirebaseManager.isLoggedIn()) {
            startActivity(new Intent(this, LoginActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            finish();
            return;
        }

        mainViewModel = new ViewModelProvider(this).get(MainViewModel.class);

        setupActivityResultLauncher();
        initViews();
        setupClickListeners();
        setupRecyclerView();
        observeViewModel();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mainViewModel.loadFriendIdsAndPosts();
        mainViewModel.startNotifBadgeListener();
        highlightTab(binding.btnHome);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mainViewModel.stopNotifBadgeListener();
    }

    private void observeViewModel() {
        mainViewModel.posts.observe(this, posts -> {
            postList.clear();
            if (posts != null) {
                postList.addAll(posts);
            }
            postAdapter.notifyDataSetChanged();
            binding.swipeRefreshLayout.setRefreshing(false);
        });

        mainViewModel.unreadNotifCount.observe(this, count -> {
            if (count != null && count > 0) {
                binding.tvNotifBadge.setVisibility(View.VISIBLE);
                binding.tvNotifBadge.setText(count > 99 ? "99+" : String.valueOf(count));
            } else {
                binding.tvNotifBadge.setVisibility(View.GONE);
            }
        });

        mainViewModel.error.observe(this, errorMsg -> {
            if (errorMsg != null) {
                Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show();
                binding.swipeRefreshLayout.setRefreshing(false);
            }
        });

        mainViewModel.postCreated.observe(this, created -> {
            if (created != null && created) {
                Toast.makeText(this, "Đã tạo bài đăng!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupActivityResultLauncher() {
        createPostLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Intent data = result.getData();
                    String content   = data.getStringExtra("post_content");
                    String tag       = data.getStringExtra("post_tag");
                    String location  = data.getStringExtra("post_location");
                    int maxMembers   = data.getIntExtra("post_max_members", 10);
                    String imageUri  = data.getStringExtra("post_image_uri");
                    long endTimeMs   = data.getLongExtra("post_end_time",
                            System.currentTimeMillis() + 86400000L);

                    mainViewModel.createPostWithFirebase(content, tag, location, maxMembers, imageUri, endTimeMs);
                }
            }
        );
    }

    private void initViews() {
        binding.swipeRefreshLayout.setColorSchemeColors(0xFFFF4D6D);
        binding.swipeRefreshLayout.setOnRefreshListener(() -> {
            mainViewModel.loadPostsFromFirestore();
            binding.swipeRefreshLayout.postDelayed(() -> binding.swipeRefreshLayout.setRefreshing(false), 3000);
        });
    }

    private void setupClickListeners() {
        binding.ivAdd.setOnClickListener(v ->
            createPostLauncher.launch(new Intent(this, CreatePostActivity.class))
        );
        binding.ivSearch.setOnClickListener(v ->
            startActivity(new Intent(this, SearchActivity.class))
        );
        binding.statusHeader.getRoot().setOnClickListener(v ->
            createPostLauncher.launch(new Intent(this, CreatePostActivity.class))
        );
        binding.btnHome.setOnClickListener(v -> {
            highlightTab(binding.btnHome);
            Toast.makeText(this, "Trang chủ", Toast.LENGTH_SHORT).show();
        });
        binding.btnMessages.setOnClickListener(v -> {
            highlightTab(binding.btnMessages);
            startActivity(new Intent(this, ChatListActivity.class));
        });
        binding.btnNotifications.setOnClickListener(v -> {
            highlightTab(binding.btnNotifications);
            startActivity(new Intent(this, NotificationsActivity.class));
        });
        binding.btnProfile.setOnClickListener(v -> {
            highlightTab(binding.btnProfile);
            Intent intent = new Intent(this, UserProfileActivity.class);
            intent.putExtra("user_uid", FirebaseManager.getCurrentUserId());
            startActivity(intent);
        });
    }

    private void setupRecyclerView() {
        binding.rvPosts.setLayoutManager(new LinearLayoutManager(this));
        postList    = new ArrayList<>();
        postAdapter = new PostAdapter(this, postList);
        binding.rvPosts.setAdapter(postAdapter);
    }

    private void highlightTab(FrameLayout tab) {
        setTabTint(binding.btnHome, R.color.text_secondary);
        setTabTint(binding.btnMessages, R.color.text_secondary);
        setTabTint(binding.btnNotifications, R.color.text_secondary);
        setTabTint(binding.btnProfile, R.color.text_secondary);
        setTabTint(tab, R.color.primary_pink);
    }

    private void setTabTint(FrameLayout tab, int colorResId) {
        tab.setAlpha(1.0f);
        if (tab.getChildAt(0) instanceof ImageView) {
            ((ImageView) tab.getChildAt(0)).setImageTintList(
                android.content.res.ColorStateList.valueOf(
                    getResources().getColor(colorResId, getTheme())));
        }
    }
}
