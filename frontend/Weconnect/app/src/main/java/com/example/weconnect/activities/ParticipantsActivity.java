package com.example.weconnect.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.weconnect.R;
import com.example.weconnect.adapters.ParticipantAdapter;
import com.example.weconnect.api.FirestorePostRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ParticipantsActivity extends AppCompatActivity {

    private RecyclerView rvParticipants;
    private TextView tvCount;
    private FirestorePostRepository postRepo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_participants);

        postRepo = new FirestorePostRepository();

        ImageView ivClose = findViewById(R.id.ivCloseParticipants);
        tvCount        = findViewById(R.id.tvParticipantsCount);
        rvParticipants = findViewById(R.id.rvParticipants);

        int memberCount = getIntent().getIntExtra("member_count", 0);
        int maxMembers  = getIntent().getIntExtra("max_members", 0);
        String postAuthor = getIntent().getStringExtra("post_author");
        String postId   = getIntent().getStringExtra("post_id");

        tvCount.setText("👥 " + memberCount + "/" + maxMembers);
        rvParticipants.setLayoutManager(new LinearLayoutManager(this));
        ivClose.setOnClickListener(v -> finish());

        loadMembersFromFirestore(postId, postAuthor, memberCount, maxMembers);
    }

    private void loadMembersFromFirestore(String postId, String postAuthor,
                                           int memberCount, int maxMembers) {
        if (postId == null || postId.isEmpty()) {
            showFallback(postAuthor, memberCount, maxMembers);
            return;
        }

        postRepo.getApprovedMembers(postId, new FirestorePostRepository.MembersCallback() {
            @Override public void onSuccess(List<Map<String, Object>> members) {
                List<ParticipantAdapter.Participant> participants = new ArrayList<>();
                boolean authorAdded = false;

                for (Map<String, Object> m : members) {
                    String name   = m.get("fullName") != null ? m.get("fullName").toString()
                                  : (m.get("userName") != null ? m.get("userName").toString() : "Người dùng");
                    String uid    = m.get("userId") != null ? m.get("userId").toString() : "";
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

                final List<ParticipantAdapter.Participant> finalList = participants;
                runOnUiThread(() -> {
                    tvCount.setText("👥 " + finalList.size() + "/" + maxMembers);
                    rvParticipants.setAdapter(new ParticipantAdapter(ParticipantsActivity.this, finalList));
                });
            }

            @Override public void onError(String err) {
                runOnUiThread(() -> showFallback(postAuthor, memberCount, maxMembers));
            }
        });
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
        rvParticipants.setAdapter(new ParticipantAdapter(this, list));
    }
}