package com.example.weconnect.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.weconnect.R;
import com.example.weconnect.adapters.PendingRequestAdapter;
import com.example.weconnect.api.FirebaseManager;
import com.example.weconnect.api.FirestorePostRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PendingListActivity extends AppCompatActivity
        implements PendingRequestAdapter.OnMemberActionListener {

    private RecyclerView rvPendingRequests;
    private TextView tvNoPending;
    private String postId;
    private List<Map<String, Object>> pendingMembers = new ArrayList<>();
    private PendingRequestAdapter adapter;
    private FirestorePostRepository postRepo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pending_list);

        postId = getIntent().getStringExtra("post_id");
        if (postId == null) {
            // backward compat: long → String
            long pid = getIntent().getLongExtra("post_id_long", -1);
            if (pid > 0) postId = String.valueOf(pid);
        }

        postRepo = new FirestorePostRepository();

        ImageView ivBack = findViewById(R.id.ivBackPending);
        rvPendingRequests = findViewById(R.id.rvPendingRequests);
        tvNoPending       = findViewById(R.id.tvNoPending);

        ivBack.setOnClickListener(v -> finish());
        loadPendingMembers();
    }

    private void loadPendingMembers() {
        if (postId == null || postId.isEmpty()) {
            showEmpty();
            return;
        }

        postRepo.getPendingMembers(postId, new FirestorePostRepository.MembersCallback() {
            @Override public void onSuccess(List<Map<String, Object>> members) {
                pendingMembers = members;
                runOnUiThread(() -> {
                    if (members.isEmpty()) showEmpty();
                    else displayPendingList();
                });
            }
            @Override public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(PendingListActivity.this, "Lỗi: " + error, Toast.LENGTH_SHORT).show();
                    showEmpty();
                });
            }
        });
    }

    private void displayPendingList() {
        tvNoPending.setVisibility(View.GONE);
        rvPendingRequests.setVisibility(View.VISIBLE);
        // PendingRequestAdapter hiện dùng Long postId — truyền 0 tạm thời
        // TODO: update adapter để nhận String postId
        adapter = new PendingRequestAdapter(this, pendingMembers, "", this);
        rvPendingRequests.setLayoutManager(new LinearLayoutManager(this));
        rvPendingRequests.setAdapter(adapter);
    }

    private void showEmpty() {
        tvNoPending.setVisibility(View.VISIBLE);
        rvPendingRequests.setVisibility(View.GONE);
    }

    @Override public void onApproved(int position) { loadPendingMembers(); }
    @Override public void onRejected(int position) { loadPendingMembers(); }
}
