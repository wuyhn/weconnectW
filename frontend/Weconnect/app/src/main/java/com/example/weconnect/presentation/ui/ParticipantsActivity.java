package com.example.weconnect.presentation.ui;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.weconnect.R;
import com.example.weconnect.presentation.adapter.ParticipantAdapter;
import com.example.weconnect.databinding.ActivityParticipantsBinding;
import com.example.weconnect.presentation.viewmodel.PostViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ParticipantsActivity extends AppCompatActivity {

    private ActivityParticipantsBinding binding;
    private PostViewModel postViewModel;

    private int memberCount = 0;
    private int maxMembers = 0;
    private String postAuthor;
    private String postId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityParticipantsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        postViewModel = new ViewModelProvider(this).get(PostViewModel.class);

        memberCount = getIntent().getIntExtra("member_count", 0);
        maxMembers  = getIntent().getIntExtra("max_members", 0);
        postAuthor = getIntent().getStringExtra("post_author");
        postId   = getIntent().getStringExtra("post_id");

        binding.tvParticipantsCount.setText("👥 " + memberCount + "/" + maxMembers);
        binding.rvParticipants.setLayoutManager(new LinearLayoutManager(this));
        binding.ivCloseParticipants.setOnClickListener(v -> finish());

        setupObservers();
        loadMembersFromFirestore();
    }

    private void setupObservers() {
        postViewModel.approvedMembers.observe(this, members -> {
            if (members != null && !members.isEmpty()) {
                displayMembers(members);
            } else {
                showFallback(postAuthor, memberCount, maxMembers);
            }
        });

        postViewModel.error.observe(this, err -> {
            if (err != null && !err.isEmpty()) {
                showFallback(postAuthor, memberCount, maxMembers);
            }
        });
    }

    private void loadMembersFromFirestore() {
        if (postId == null || postId.isEmpty()) {
            showFallback(postAuthor, memberCount, maxMembers);
            return;
        }
        postViewModel.loadApprovedMembers(postId);
    }

    private void displayMembers(List<Map<String, Object>> members) {
        List<ParticipantAdapter.Participant> participants = new ArrayList<>();
        boolean authorAdded = false;

        for (Map<String, Object> m : members) {
            String name   = m.get("fullName") != null ? m.get("fullName").toString()
                          : (m.get("userName") != null ? m.get("userName").toString() : "Người dùng");
            boolean isAuthor = postAuthor != null && name.equalsIgnoreCase(postAuthor);

            if (isAuthor) {
                participants.add(0, new ParticipantAdapter.Participant(
                    name + " (Người tổ chức)", R.drawable.ic_user_placeholder, 0L));
                authorAdded = true;
            } else {
                participants.add(new ParticipantAdapter.Participant(
                    name, R.drawable.ic_user_placeholder, 0L));
            }
        }

        if (!authorAdded && postAuthor != null && !postAuthor.isEmpty()) {
            participants.add(0, new ParticipantAdapter.Participant(
                postAuthor + " (Người tổ chức)", R.drawable.ic_user_placeholder, 0L));
        }

        if (participants.isEmpty()) {
            participants.add(new ParticipantAdapter.Participant(
                "Chưa có thành viên nào", R.drawable.ic_user_placeholder));
        }

        binding.tvParticipantsCount.setText("👥 " + participants.size() + "/" + maxMembers);
        binding.rvParticipants.setAdapter(new ParticipantAdapter(ParticipantsActivity.this, participants));
    }

    private void showFallback(String postAuthor, int memberCount, int maxMembers) {
        List<ParticipantAdapter.Participant> list = new ArrayList<>();
        if (postAuthor != null && !postAuthor.isEmpty()) {
            list.add(new ParticipantAdapter.Participant(
                postAuthor + " (Người tổ chức)", R.drawable.ic_user_placeholder));
        }
        if (list.isEmpty()) {
            list.add(new ParticipantAdapter.Participant(
                "Chưa có thành viên nào", R.drawable.ic_user_placeholder));
        }
        binding.rvParticipants.setAdapter(new ParticipantAdapter(this, list));
    }
}