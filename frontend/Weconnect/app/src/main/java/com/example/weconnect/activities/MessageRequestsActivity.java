package com.example.weconnect.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.weconnect.R;
import com.example.weconnect.adapters.ChatRoomAdapter;
import com.example.weconnect.api.ChatApiService;
import com.example.weconnect.api.RetrofitClient;
import com.example.weconnect.models.ApiResponse;
import com.example.weconnect.models.ChatMessage;
import com.example.weconnect.models.ChatRoom;
import com.example.weconnect.models.ChatRoomApiResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MessageRequestsActivity extends AppCompatActivity {

    private ImageView ivBack;
    private RecyclerView rvMessageRequests;
    private LinearLayout layoutEmptyRequests;
    private ChatRoomAdapter adapter;
    private ChatApiService chatApi;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_message_requests);

        RetrofitClient.loadToken(this);
        chatApi = RetrofitClient.getClient().create(ChatApiService.class);
        initViews();
        setupRecyclerView();
        ivBack.setOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadMessageRequests();
    }

    private void initViews() {
        ivBack = findViewById(R.id.ivBackMessageRequests);
        rvMessageRequests = findViewById(R.id.rvMessageRequests);
        layoutEmptyRequests = findViewById(R.id.layoutEmptyRequests);
    }

    private void setupRecyclerView() {
        adapter = new ChatRoomAdapter(this::openRoom);
        rvMessageRequests.setLayoutManager(new LinearLayoutManager(this));
        rvMessageRequests.setAdapter(adapter);
    }

    private void loadMessageRequests() {
        chatApi.getMessageRequests().enqueue(new Callback<ApiResponse<List<ChatRoomApiResponse>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<ChatRoomApiResponse>>> call,
                                   Response<ApiResponse<List<ChatRoomApiResponse>>> response) {
                if (response.isSuccessful() && response.body() != null
                        && response.body().getResult() != null) {
                    List<ChatRoom> rooms = convertRooms(response.body().getResult());
                    Collections.sort(rooms, (a, b) ->
                            b.getLastMessageTimeRaw().compareTo(a.getLastMessageTimeRaw()));
                    adapter.submitList(rooms);
                    layoutEmptyRequests.setVisibility(rooms.isEmpty() ? View.VISIBLE : View.GONE);
                    rvMessageRequests.setVisibility(rooms.isEmpty() ? View.GONE : View.VISIBLE);
                } else {
                    showEmpty();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<ChatRoomApiResponse>>> call, Throwable t) {
                Toast.makeText(MessageRequestsActivity.this,
                        "Lỗi kết nối: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                showEmpty();
            }
        });
    }

    private void showEmpty() {
        adapter.submitList(new ArrayList<>());
        layoutEmptyRequests.setVisibility(View.VISIBLE);
        rvMessageRequests.setVisibility(View.GONE);
    }

    private List<ChatRoom> convertRooms(List<ChatRoomApiResponse> data) {
        List<ChatRoom> rooms = new ArrayList<>();
        for (ChatRoomApiResponse item : data) {
            String id = String.valueOf(item.getId());
            String title = item.getTitle() != null ? item.getTitle() : "Phòng chat";
            String type = item.getType() != null ? item.getType() : ChatRoom.TYPE_DIRECT;
            String lastPreview = item.getLastMessagePreview() != null
                    ? item.getLastMessagePreview() : "";
            String lastTime = item.getLastMessageTime() != null
                    ? item.getLastMessageTime() : "";

            List<ChatMessage> messages = new ArrayList<>();
            if (!lastPreview.isEmpty() && !"Chưa có tin nhắn".equals(lastPreview)) {
                messages.add(new ChatMessage("0", "", lastPreview, formatTime(lastTime), false));
            }

            ChatRoom room = new ChatRoom(
                    id, title, item.getSubtitle(), item.getPostStatusLabel(), type,
                    R.drawable.ic_user_placeholder,
                    item.isActive(),
                    item.getInactiveStatusLabel(),
                    messages,
                    item.getOwnerName(),
                    collectMemberNames(item),
                    new ArrayList<>()
            );
            room.setLastMessageTimeRaw(lastTime);
            room.setFriend(item.isFriend());
            room.setMessageRequest(item.isMessageRequest());
            room.setOtherUserId(item.getOtherUserId());
            room.setOtherUserName(item.getOtherUserName());
            room.setOtherUserAvatarUrl(item.getOtherUserAvatarUrl());
            room.setBlockedByMe(item.isBlockedByMe());
            room.setHasBlockedMe(item.hasBlockedMe());
            room.setBlockedBetweenUsers(item.isBlockedBetweenUsers());

            // Nếu user đã chặn mình thì không load avatar thật
            if (item.hasBlockedMe()) {
                rooms.add(room);
                continue;
            }

            String avatarUrl = item.getOtherUserAvatarUrl();
            if ((avatarUrl == null || avatarUrl.isEmpty()) && item.getMembers() != null) {
                long myId = RetrofitClient.getUserId(this);
                for (ChatRoomApiResponse.MemberInfo member : item.getMembers()) {
                    if (member.getId() != myId) {
                        avatarUrl = member.getAvatarUrl();
                        break;
                    }
                }
            }
            if (avatarUrl != null && !avatarUrl.isEmpty()) {
                if (avatarUrl.startsWith("/")) {
                    avatarUrl = RetrofitClient.getBaseUrl() + avatarUrl.substring(1);
                }
                room.setAvatarUrl(avatarUrl);
            }

            rooms.add(room);
        }
        return rooms;
    }

    private List<String> collectMemberNames(ChatRoomApiResponse item) {
        List<String> names = new ArrayList<>();
        if (item.getMembers() == null) return names;
        for (ChatRoomApiResponse.MemberInfo member : item.getMembers()) {
            if (member.getFullName() != null && !member.getFullName().isEmpty()) {
                names.add(member.getFullName());
            }
        }
        return names;
    }

    private String formatTime(String isoTime) {
        if (isoTime == null || isoTime.isEmpty()) return "";
        if (isoTime.contains("T") && isoTime.length() >= 16) {
            return isoTime.substring(11, 16);
        }
        return isoTime;
    }

    private void openRoom(ChatRoom room) {
        Intent intent = new Intent(this, ConversationActivity.class);
        intent.putExtra("room_id", room.getId());
        startActivity(intent);
    }
}
