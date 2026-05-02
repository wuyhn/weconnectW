package com.example.weconnect.presentation.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.weconnect.presentation.adapter.PendingRequestAdapter;
import com.example.weconnect.databinding.ActivityPendingListBinding;
import com.example.weconnect.presentation.viewmodel.PostViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PendingListActivity extends AppCompatActivity
        implements PendingRequestAdapter.OnMemberActionListener {

    private ActivityPendingListBinding binding;
    private PostViewModel postViewModel;
    private String postId;
    private List<Map<String, Object>> pendingMembers = new ArrayList<>();
    private PendingRequestAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPendingListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        postViewModel = new ViewModelProvider(this).get(PostViewModel.class);

        postId = getIntent().getStringExtra("post_id");
        if (postId == null) {
            // backward compat: long → String
            long pid = getIntent().getLongExtra("post_id_long", -1);
            if (pid > 0) postId = String.valueOf(pid);
        }

        binding.ivBackPending.setOnClickListener(v -> finish());
        
        setupObservers();
        loadPendingMembers();
    }

    private void setupObservers() {
        postViewModel.pendingMembers.observe(this, members -> {
            pendingMembers = members != null ? members : new ArrayList<>();
            if (pendingMembers.isEmpty()) {
                showEmpty();
            } else {
                displayPendingList();
            }
        });

        postViewModel.error.observe(this, err -> {
            if (err != null && !err.isEmpty()) {
                Toast.makeText(PendingListActivity.this, "Lỗi: " + err, Toast.LENGTH_SHORT).show();
                showEmpty();
            }
        });
    }

    private void loadPendingMembers() {
        if (postId == null || postId.isEmpty()) {
            showEmpty();
            return;
        }
        postViewModel.loadPendingMembers(postId);
    }

    private void displayPendingList() {
        binding.tvNoPending.setVisibility(View.GONE);
        binding.rvPendingRequests.setVisibility(View.VISIBLE);
        adapter = new PendingRequestAdapter(this, pendingMembers, "", this);
        binding.rvPendingRequests.setLayoutManager(new LinearLayoutManager(this));
        binding.rvPendingRequests.setAdapter(adapter);
    }

    private void showEmpty() {
        binding.tvNoPending.setVisibility(View.VISIBLE);
        binding.rvPendingRequests.setVisibility(View.GONE);
    }

    @Override public void onApproved(int position) { loadPendingMembers(); }
    @Override public void onRejected(int position) { loadPendingMembers(); }
}
