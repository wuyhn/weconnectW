package com.example.weconnect.activities;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.weconnect.R;
import com.example.weconnect.adapters.ParticipantAdapter;
import com.example.weconnect.api.PostApiService;
import com.example.weconnect.api.RetrofitClient;
import com.example.weconnect.models.ApiResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ParticipantsActivity extends AppCompatActivity {

    private RecyclerView rvParticipants;
    private TextView tvCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_participants);

        ImageView ivClose = findViewById(R.id.ivCloseParticipants);
        tvCount = findViewById(R.id.tvParticipantsCount);
        rvParticipants = findViewById(R.id.rvParticipants);

        int memberCount = getIntent().getIntExtra("member_count", 0);
        int maxMembers = getIntent().getIntExtra("max_members", 0);
        String postAuthor = getIntent().getStringExtra("post_author");
        String postId = getIntent().getStringExtra("post_id");

        tvCount.setText("👥 " + memberCount + "/" + maxMembers);
        rvParticipants.setLayoutManager(new LinearLayoutManager(this));

        ivClose.setOnClickListener(v -> finish());

        // Load real members from API
        loadMembersFromApi(postId, postAuthor, memberCount);
    }

    private void loadMembersFromApi(String postId, String postAuthor, int memberCount) {
        if (postId == null || postId.isEmpty()) {
            showFallback(postAuthor, memberCount);
            return;
        }

        long id;
        try {
            id = Long.parseLong(postId);
        } catch (NumberFormatException e) {
            showFallback(postAuthor, memberCount);
            return;
        }

        RetrofitClient.loadToken(this);
        PostApiService postApi = RetrofitClient.getClient().create(PostApiService.class);

        postApi.getMembers(id).enqueue(new Callback<ApiResponse<List<Map<String, Object>>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<Map<String, Object>>>> call,
                                   Response<ApiResponse<List<Map<String, Object>>>> response) {
                if (response.isSuccessful() && response.body() != null
                        && response.body().getResult() != null) {
                    List<Map<String, Object>> membersData = response.body().getResult();
                    List<ParticipantAdapter.Participant> participants = new ArrayList<>();
                    android.util.Log.d("Participants", "Members count from API: " + membersData.size());

                    // Always show post author first as organizer
                    boolean authorAdded = false;
                    for (Map<String, Object> member : membersData) {
                        android.util.Log.d("Participants", "Member raw data keys: " + member.keySet() + " values: " + member);
                        String name = member.get("fullName") != null
                                ? member.get("fullName").toString()
                                : (member.get("username") != null ? member.get("username").toString() : "Người dùng");
                        String status = member.get("status") != null
                                ? member.get("status").toString() : "";

                        // Extract userId from API response (try both "userId" and "id" keys)
                        long memberId = -1;
                        try {
                            Object rawId = member.get("userId");
                            if (rawId == null) rawId = member.get("id");
                            if (rawId != null) {
                                memberId = ((Number) rawId).longValue();
                            }
                        } catch (Exception ignored) {}

                        // Only show APPROVED members
                        if (!"APPROVED".equalsIgnoreCase(status)) continue;

                        boolean isAuthor = (postAuthor != null && name.equalsIgnoreCase(postAuthor));
                        if (isAuthor) {
                            participants.add(0, new ParticipantAdapter.Participant(
                                    name + " (Người tổ chức)", R.drawable.ic_user_placeholder, memberId));
                            authorAdded = true;
                        } else {
                            participants.add(new ParticipantAdapter.Participant(
                                    name, R.drawable.ic_user_placeholder, memberId));
                        }
                    }

                    // If author wasn't in the members list, add them at the top
                    if (!authorAdded && postAuthor != null && !postAuthor.isEmpty()) {
                        // Try to get author's userId from intent
                        long authorUserId = getIntent().getLongExtra("author_user_id", -1);
                        participants.add(0, new ParticipantAdapter.Participant(
                                postAuthor + " (Người tổ chức)", R.drawable.ic_user_placeholder, authorUserId));
                    }

                    // Update count with real data
                    int totalMembers = participants.size();
                    int maxMembers = getIntent().getIntExtra("max_members", 0);
                    tvCount.setText("👥 " + totalMembers + "/" + maxMembers);

                    if (participants.isEmpty()) {
                        participants.add(new ParticipantAdapter.Participant(
                                "Chưa có thành viên nào", R.drawable.ic_user_placeholder));
                    }

                    rvParticipants.setAdapter(new ParticipantAdapter(ParticipantsActivity.this, participants));
                } else {
                    showFallback(postAuthor, getIntent().getIntExtra("member_count", 0));
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<Map<String, Object>>>> call, Throwable t) {
                showFallback(postAuthor, getIntent().getIntExtra("member_count", 0));
            }
        });
    }

    private void showFallback(String postAuthor, int memberCount) {
        List<ParticipantAdapter.Participant> participants = new ArrayList<>();

        if (postAuthor != null && !postAuthor.isEmpty()) {
            participants.add(new ParticipantAdapter.Participant(
                    postAuthor + " (Người tổ chức)", R.drawable.ic_user_placeholder));
        }

        if (participants.isEmpty()) {
            participants.add(new ParticipantAdapter.Participant(
                    "Chưa có thành viên nào", R.drawable.ic_user_placeholder));
        }

        rvParticipants.setAdapter(new ParticipantAdapter(this, participants));
    }
}