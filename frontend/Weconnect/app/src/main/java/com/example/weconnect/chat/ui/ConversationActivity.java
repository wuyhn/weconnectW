package com.example.weconnect.chat.ui;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.weconnect.R;
import com.example.weconnect.chat.ui.MessageAdapter;
import com.example.weconnect.chat.data.ChatApiService;
import com.example.weconnect.core.data.RetrofitClient;
import com.example.weconnect.post.data.FakePostRepository;
import com.example.weconnect.social.data.FakeSocialRepository;
import com.example.weconnect.core.data.ApiResponse;
import com.example.weconnect.chat.data.ChatMessage;
import com.example.weconnect.chat.data.ChatRoom;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ConversationActivity extends AppCompatActivity {

    private ImageView ivBackConversation;
    private ImageView ivConversationAvatar;
    private ImageView ivChatSettings;
    private TextView tvConversationTitle;
    private TextView tvConversationType;
    private TextView tvConversationStatus;
    private View viewOnlineDot;
    private RecyclerView rvMessages;
    private EditText etMessageInput;
    private MaterialButton btnSendMessage;
    private MessageAdapter adapter;
    private ChatRoom room;
    private String currentUsername;
    private ChatApiService chatApi;
    private long backendRoomId = -1;

    // Auto-polling cho tin nhắn real-time
    private static final long CHAT_POLL_INTERVAL = 3000; // 3 giây
    private final Handler pollHandler = new Handler(Looper.getMainLooper());
    private boolean isPolling = false;
    private int lastMessageCount = 0;
    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            if (backendRoomId > 0) {
                loadMessagesFromApi();
            }
            pollHandler.postDelayed(this, CHAT_POLL_INTERVAL);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversation);

        currentUsername = FakePostRepository.getInstance().getCurrentUsername();
        RetrofitClient.loadToken(this);
        chatApi = RetrofitClient.getClient().create(ChatApiService.class);
        initViews();
        setupRecyclerView();
        setupClickListeners();
        bindRoom();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startPolling();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopPolling();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopPolling();
    }

    private void startPolling() {
        if (!isPolling && backendRoomId > 0) {
            isPolling = true;
            pollHandler.postDelayed(pollRunnable, CHAT_POLL_INTERVAL);
        }
    }

    private void stopPolling() {
        isPolling = false;
        pollHandler.removeCallbacks(pollRunnable);
    }

    private void initViews() {
        ivBackConversation = findViewById(R.id.ivBackConversation);
        ivConversationAvatar = findViewById(R.id.ivConversationAvatar);
        ivChatSettings = findViewById(R.id.ivChatSettings);
        tvConversationTitle = findViewById(R.id.tvConversationTitle);
        tvConversationType = findViewById(R.id.tvConversationType);
        tvConversationStatus = findViewById(R.id.tvConversationStatus);
        viewOnlineDot = findViewById(R.id.viewOnlineDot);
        rvMessages = findViewById(R.id.rvMessages);
        etMessageInput = findViewById(R.id.etMessageInput);
        btnSendMessage = findViewById(R.id.btnSendMessage);
    }

    private void setupRecyclerView() {
        adapter = new MessageAdapter();
        rvMessages.setLayoutManager(new LinearLayoutManager(this));
        rvMessages.setAdapter(adapter);
    }

    private void setupClickListeners() {
        ivBackConversation.setOnClickListener(v -> finish());
        btnSendMessage.setOnClickListener(v -> sendMessage());
        ivChatSettings.setOnClickListener(v -> showMemberManagementDialog());
    }

    private void bindRoom() {
        // Thử đọc room_id dạng long (từ backend API) trước
        long roomIdLong = getIntent().getLongExtra("room_id", -1);
        String roomIdStr = getIntent().getStringExtra("room_id");
        String chatName = getIntent().getStringExtra("chat_name");

        // Ưu tiên long extra (từ PostAdapter backend call)
        if (roomIdLong > 0) {
            backendRoomId = roomIdLong;
        } else if (roomIdStr != null) {
            try {
                backendRoomId = Long.parseLong(roomIdStr);
            } catch (NumberFormatException e) {
                backendRoomId = -1;
            }
        }

        if (backendRoomId > 0) {
            // Load room từ backend API
            loadRoomFromApi(backendRoomId);
        } else {
            Toast.makeText(this, "Không tìm thấy phòng chat.", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void loadRoomFromApi(long roomId) {
        chatApi.getRoom(roomId).enqueue(new Callback<ApiResponse<com.example.weconnect.models.ChatRoomApiResponse>>() {
            @Override
            public void onResponse(Call<ApiResponse<com.example.weconnect.models.ChatRoomApiResponse>> call,
                                   Response<ApiResponse<com.example.weconnect.models.ChatRoomApiResponse>> response) {
                if (response.isSuccessful() && response.body() != null
                        && response.body().getResult() != null) {
                    com.example.weconnect.models.ChatRoomApiResponse data = response.body().getResult();
                    room = parseRoomFromApi(data);
                    displayRoom();
                    loadMessagesFromApi();
                    startPolling(); // Bắt đầu auto-refresh sau khi load room
                } else {
                    Toast.makeText(ConversationActivity.this,
                            "Không tìm thấy phòng chat.", Toast.LENGTH_SHORT).show();
                    finish();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<com.example.weconnect.models.ChatRoomApiResponse>> call, Throwable t) {
                Toast.makeText(ConversationActivity.this,
                        "Lỗi kết nối: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private ChatRoom parseRoomFromApi(com.example.weconnect.models.ChatRoomApiResponse data) {
        String id = String.valueOf(data.getId());
        String title = data.getTitle() != null ? data.getTitle() : "Phòng chat";
        String type = data.getType() != null ? data.getType() : "group";
        boolean active = data.isActive();
        String inactiveLabel = data.getInactiveStatusLabel() != null
                ? data.getInactiveStatusLabel() : "";
        String ownerName = data.getOwnerName() != null ? data.getOwnerName() : "";
        String subtitle = data.getSubtitle();
        String postStatusLabel = data.getPostStatusLabel();

        List<String> memberNames = new ArrayList<>();
        if (data.getMembers() != null) {
            for (com.example.weconnect.models.ChatRoomApiResponse.MemberInfo m : data.getMembers()) {
                String name = m.getFullName() != null ? m.getFullName() : "";
                if (!name.isEmpty()) memberNames.add(name);
            }
        }

        return new ChatRoom(id, title, subtitle, postStatusLabel, type, R.drawable.ic_user_placeholder,
                active, inactiveLabel, new ArrayList<>(), ownerName, memberNames, new ArrayList<>());
    }

    private void displayRoom() {
        if (room == null) return;

        ivConversationAvatar.setImageResource(room.getAvatarResId());
        tvConversationTitle.setText(room.getTitle());
        tvConversationType.setText(room.getTypeLabel());

        boolean isActivityOrGroup = ChatRoom.TYPE_GROUP.equals(room.getType())
                || ChatRoom.TYPE_ACTIVITY.equals(room.getType());

        if (isActivityOrGroup && !room.isActive()) {
            viewOnlineDot.setVisibility(View.GONE);
            tvConversationStatus.setText(room.getInactiveStatusLabel());
            tvConversationStatus.setTextColor(0xFF9E8E82);
        } else {
            viewOnlineDot.setVisibility(View.VISIBLE);
            tvConversationStatus.setText("Đang hoạt động");
            tvConversationStatus.setTextColor(0xFF66BB6A);
        }

        if (ChatRoom.TYPE_DIRECT.equals(room.getType())) {
            ivChatSettings.setVisibility(View.GONE);
        } else {
            ivChatSettings.setVisibility(View.VISIBLE);
        }
    }

    private void showMemberManagementDialog() {
        if (room == null) return;

        boolean isOwner = room.isOwner(currentUsername);
        boolean isFriendGroup = ChatRoom.TYPE_FRIEND_GROUP.equals(room.getType());
        boolean isActivityGroup = ChatRoom.TYPE_GROUP.equals(room.getType())
                || ChatRoom.TYPE_ACTIVITY.equals(room.getType());

        BottomSheetDialog sheet = new BottomSheetDialog(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getResources().getColor(R.color.card_surface, null));
        root.setPadding(0, 0, 0, 48);

        // Header
        TextView header = new TextView(this);
        header.setText("Quản lý phòng chat");
        header.setTextSize(20);
        header.setTextColor(getResources().getColor(R.color.primary_pink, null));
        header.setTypeface(null, android.graphics.Typeface.BOLD);
        header.setGravity(Gravity.CENTER);
        header.setPadding(0, 48, 0, 12);
        root.addView(header);

        // Room info
        TextView roomInfo = new TextView(this);
        String typeStr = isActivityGroup ? "Hoạt động" : (isFriendGroup ? "Nhóm bạn bè" : "");
        roomInfo.setText(typeStr + " • Chủ phòng: " + (room.getOwnerUsername() != null ? room.getOwnerUsername() : "N/A"));
        roomInfo.setTextSize(13);
        roomInfo.setTextColor(getResources().getColor(R.color.text_secondary, null));
        roomInfo.setGravity(Gravity.CENTER);
        roomInfo.setPadding(0, 0, 0, 24);
        root.addView(roomInfo);

        // Divider
        addDivider(root);

        // === MEMBERS SECTION ===
        TextView membersHeader = new TextView(this);
        membersHeader.setText("👥 Thành viên (" + room.getMembers().size() + ")");
        membersHeader.setTextSize(15);
        membersHeader.setTypeface(null, android.graphics.Typeface.BOLD);
        membersHeader.setTextColor(getResources().getColor(R.color.text_primary, null));
        membersHeader.setPadding(64, 28, 64, 12);
        root.addView(membersHeader);

        for (String member : room.getMembers()) {
            LinearLayout memberRow = createMemberRow(member, isOwner && !member.equalsIgnoreCase(currentUsername), sheet);
            root.addView(memberRow);
        }

        // === PENDING MEMBERS SECTION (Owner only, Activity groups) ===
        if (isOwner && !room.getPendingMembers().isEmpty()) {
            addDivider(root);

            TextView pendingHeader = new TextView(this);
            pendingHeader.setText("⏳ Chờ duyệt (" + room.getPendingMembers().size() + ")");
            pendingHeader.setTextSize(15);
            pendingHeader.setTypeface(null, android.graphics.Typeface.BOLD);
            pendingHeader.setTextColor(getResources().getColor(R.color.text_primary, null));
            pendingHeader.setPadding(64, 28, 64, 12);
            root.addView(pendingHeader);

            for (String pending : room.getPendingMembers()) {
                LinearLayout pendingRow = createPendingMemberRow(pending, sheet);
                root.addView(pendingRow);
            }
        }

        // === ADD FRIEND (Owner of friend_group only) ===
        if (isOwner && isFriendGroup) {
            addDivider(root);

            MaterialButton btnAddFriend = new MaterialButton(this);
            btnAddFriend.setText("+ Thêm bạn bè vào nhóm");
            btnAddFriend.setAllCaps(false);
            btnAddFriend.setCornerRadius(72);
            btnAddFriend.setTextSize(14);
            btnAddFriend.setBackgroundTintList(ColorStateList.valueOf(
                    getResources().getColor(R.color.primary_pink, null)));
            LinearLayout.LayoutParams btnP = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            btnP.setMargins(48, 24, 48, 0);
            btnAddFriend.setLayoutParams(btnP);
            btnAddFriend.setOnClickListener(v -> {
                sheet.dismiss();
                showAddFriendToGroupDialog();
            });
            root.addView(btnAddFriend);
        }

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(root);
        sheet.setContentView(scrollView);
        sheet.show();
    }

    private LinearLayout createMemberRow(String memberName, boolean canRemove, BottomSheetDialog parentSheet) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(64, 20, 64, 20);

        // Avatar
        ImageView avatar = new ImageView(this);
        avatar.setImageResource(R.drawable.ic_user_placeholder);
        LinearLayout.LayoutParams avatarLp = new LinearLayout.LayoutParams(72, 72);
        avatar.setLayoutParams(avatarLp);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        row.addView(avatar);

        // Name
        TextView name = new TextView(this);
        boolean isOwner = room.isOwner(memberName);
        name.setText(memberName + (isOwner ? " (Chủ phòng)" : ""));
        name.setTextSize(14);
        name.setTextColor(getResources().getColor(isOwner ? R.color.primary_pink : R.color.text_primary, null));
        name.setPadding(24, 0, 0, 0);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        name.setLayoutParams(nameLp);
        row.addView(name);

        // Remove button (only for owner, can't remove self)
        if (canRemove) {
            MaterialButton btnRemove = new MaterialButton(this);
            btnRemove.setText("Xóa");
            btnRemove.setAllCaps(false);
            btnRemove.setTextSize(11);
            btnRemove.setCornerRadius(48);
            btnRemove.setBackgroundTintList(ColorStateList.valueOf(0xFFFF4D6D));
            LinearLayout.LayoutParams removeLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, 72);
            btnRemove.setLayoutParams(removeLp);
            btnRemove.setInsetTop(0);
            btnRemove.setInsetBottom(0);
            btnRemove.setMinWidth(0);
            btnRemove.setMinimumWidth(0);
            btnRemove.setPadding(32, 0, 32, 0);
            btnRemove.setOnClickListener(v -> {
                room.removeMember(memberName);
                Toast.makeText(this, "Đã xóa " + memberName + " khỏi phòng", Toast.LENGTH_SHORT).show();
                parentSheet.dismiss();
                showMemberManagementDialog(); // Refresh
            });
            row.addView(btnRemove);
        }

        return row;
    }

    private LinearLayout createPendingMemberRow(String pendingName, BottomSheetDialog parentSheet) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(64, 20, 64, 20);

        // Avatar
        ImageView avatar = new ImageView(this);
        avatar.setImageResource(R.drawable.ic_user_placeholder);
        LinearLayout.LayoutParams avatarLp = new LinearLayout.LayoutParams(72, 72);
        avatar.setLayoutParams(avatarLp);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        row.addView(avatar);

        // Name
        TextView name = new TextView(this);
        name.setText(pendingName);
        name.setTextSize(14);
        name.setTextColor(getResources().getColor(R.color.text_primary, null));
        name.setPadding(24, 0, 0, 0);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        name.setLayoutParams(nameLp);
        row.addView(name);

        // Approve button
        MaterialButton btnApprove = new MaterialButton(this);
        btnApprove.setText("✓");
        btnApprove.setAllCaps(false);
        btnApprove.setTextSize(14);
        btnApprove.setCornerRadius(48);
        btnApprove.setBackgroundTintList(ColorStateList.valueOf(0xFF4CAF50));
        LinearLayout.LayoutParams approveLp = new LinearLayout.LayoutParams(72, 72);
        approveLp.setMargins(8, 0, 0, 0);
        btnApprove.setLayoutParams(approveLp);
        btnApprove.setInsetTop(0);
        btnApprove.setInsetBottom(0);
        btnApprove.setMinWidth(0);
        btnApprove.setMinimumWidth(0);
        btnApprove.setPadding(0, 0, 0, 0);
        btnApprove.setOnClickListener(v -> {
            room.addMember(pendingName);
            Toast.makeText(this, "Đã duyệt " + pendingName, Toast.LENGTH_SHORT).show();
            parentSheet.dismiss();
            showMemberManagementDialog(); // Refresh
        });
        row.addView(btnApprove);

        // Reject button
        MaterialButton btnReject = new MaterialButton(this);
        btnReject.setText("✕");
        btnReject.setAllCaps(false);
        btnReject.setTextSize(14);
        btnReject.setCornerRadius(48);
        btnReject.setBackgroundTintList(ColorStateList.valueOf(0xFFFF4D6D));
        LinearLayout.LayoutParams rejectLp = new LinearLayout.LayoutParams(72, 72);
        rejectLp.setMargins(8, 0, 0, 0);
        btnReject.setLayoutParams(rejectLp);
        btnReject.setInsetTop(0);
        btnReject.setInsetBottom(0);
        btnReject.setMinWidth(0);
        btnReject.setMinimumWidth(0);
        btnReject.setPadding(0, 0, 0, 0);
        btnReject.setOnClickListener(v -> {
            room.rejectPendingMember(pendingName);
            Toast.makeText(this, "Đã từ chối " + pendingName, Toast.LENGTH_SHORT).show();
            parentSheet.dismiss();
            showMemberManagementDialog(); // Refresh
        });
        row.addView(btnReject);

        return row;
    }

    private void showAddFriendToGroupDialog() {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getResources().getColor(R.color.card_surface, null));
        root.setPadding(0, 0, 0, 48);

        TextView header = new TextView(this);
        header.setText("Thêm bạn bè vào nhóm");
        header.setTextSize(20);
        header.setTextColor(getResources().getColor(R.color.primary_pink, null));
        header.setTypeface(null, android.graphics.Typeface.BOLD);
        header.setGravity(Gravity.CENTER);
        header.setPadding(0, 48, 0, 24);
        root.addView(header);

        addDivider(root);

        // Get friends of owner that aren't already in the group
        List<String> allFriends = FakeSocialRepository.getInstance().getFriendNames();

        boolean hasAvailable = false;
        for (String friend : allFriends) {
            if (room.isMember(friend)) continue;
            hasAvailable = true;

            LinearLayout friendRow = new LinearLayout(this);
            friendRow.setOrientation(LinearLayout.HORIZONTAL);
            friendRow.setGravity(Gravity.CENTER_VERTICAL);
            friendRow.setPadding(64, 24, 64, 24);
            friendRow.setBackgroundResource(android.R.drawable.list_selector_background);
            friendRow.setClickable(true);

            ImageView avatar = new ImageView(this);
            avatar.setImageResource(R.drawable.ic_user_placeholder);
            LinearLayout.LayoutParams avatarLp = new LinearLayout.LayoutParams(72, 72);
            avatar.setLayoutParams(avatarLp);
            avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
            friendRow.addView(avatar);

            TextView name = new TextView(this);
            name.setText(friend);
            name.setTextSize(15);
            name.setTextColor(getResources().getColor(R.color.text_primary, null));
            name.setPadding(24, 0, 0, 0);
            LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            name.setLayoutParams(nameLp);
            friendRow.addView(name);

            MaterialButton btnAdd = new MaterialButton(this);
            btnAdd.setText("Thêm");
            btnAdd.setAllCaps(false);
            btnAdd.setTextSize(11);
            btnAdd.setCornerRadius(48);
            btnAdd.setBackgroundTintList(ColorStateList.valueOf(
                    getResources().getColor(R.color.primary_pink, null)));
            LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, 72);
            btnAdd.setLayoutParams(addLp);
            btnAdd.setInsetTop(0);
            btnAdd.setInsetBottom(0);
            btnAdd.setMinWidth(0);
            btnAdd.setMinimumWidth(0);
            btnAdd.setPadding(32, 0, 32, 0);
            btnAdd.setOnClickListener(v -> {
                room.addMember(friend);
                Toast.makeText(this, "Đã thêm " + friend + " vào nhóm", Toast.LENGTH_SHORT).show();
                sheet.dismiss();
                showMemberManagementDialog();
            });
            friendRow.addView(btnAdd);

            root.addView(friendRow);
        }

        if (!hasAvailable) {
            TextView noFriends = new TextView(this);
            noFriends.setText("Tất cả bạn bè đã ở trong nhóm");
            noFriends.setTextSize(14);
            noFriends.setTextColor(getResources().getColor(R.color.text_secondary, null));
            noFriends.setGravity(Gravity.CENTER);
            noFriends.setPadding(0, 48, 0, 48);
            root.addView(noFriends);
        }

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(root);
        sheet.setContentView(scrollView);
        sheet.show();
    }

    private void addDivider(LinearLayout parent) {
        View div = new View(this);
        div.setBackgroundColor(0xFFE8E4DE);
        div.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 2));
        parent.addView(div);
    }

    private void sendMessage() {
        if (room == null || backendRoomId <= 0) {
            return;
        }

        String content = etMessageInput.getText() != null
                ? etMessageInput.getText().toString().trim()
                : "";

        if (TextUtils.isEmpty(content)) {
            return;
        }

        etMessageInput.setText("");

        Map<String, String> body = new HashMap<>();
        body.put("content", content);

        chatApi.sendMessage(backendRoomId, body).enqueue(new Callback<ApiResponse<com.example.weconnect.models.ChatMessageApiResponse>>() {
            @Override
            public void onResponse(Call<ApiResponse<com.example.weconnect.models.ChatMessageApiResponse>> call,
                                   Response<ApiResponse<com.example.weconnect.models.ChatMessageApiResponse>> response) {
                if (response.isSuccessful()) {
                    loadMessagesFromApi();
                } else {
                    Toast.makeText(ConversationActivity.this,
                            "Không thể gửi tin nhắn", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<com.example.weconnect.models.ChatMessageApiResponse>> call, Throwable t) {
                Toast.makeText(ConversationActivity.this,
                        "Lỗi kết nối", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadMessagesFromApi() {
        if (backendRoomId <= 0) return;

        chatApi.getMessages(backendRoomId).enqueue(new Callback<ApiResponse<List<com.example.weconnect.models.ChatMessageApiResponse>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<com.example.weconnect.models.ChatMessageApiResponse>>> call,
                                   Response<ApiResponse<List<com.example.weconnect.models.ChatMessageApiResponse>>> response) {
                if (response.isSuccessful() && response.body() != null
                        && response.body().getResult() != null) {
                    List<ChatMessage> messages = new ArrayList<>();
                    for (com.example.weconnect.models.ChatMessageApiResponse msgData : response.body().getResult()) {
                        String id = String.valueOf(msgData.getId());
                        String sender = msgData.getSenderName() != null
                                ? msgData.getSenderName() : "";
                        String msgContent = msgData.getContent() != null
                                ? msgData.getContent() : "";
                        String time = "";
                        if (msgData.getCreatedAt() != null) {
                            String raw = msgData.getCreatedAt();
                            if (raw.contains("T") && raw.length() >= 16) {
                                time = raw.substring(11, 16);
                            } else {
                                time = raw;
                            }
                        }
                        boolean sentByMe = msgData.isSentByCurrentUser();
                        messages.add(new ChatMessage(id, sender, msgContent, time, sentByMe));
                    }
                    adapter.submitList(messages);
                    // Smart scroll: chỉ auto-scroll nếu có tin nhắn mới
                    if (!messages.isEmpty() && messages.size() != lastMessageCount) {
                        lastMessageCount = messages.size();
                        rvMessages.scrollToPosition(messages.size() - 1);
                    }
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<com.example.weconnect.models.ChatMessageApiResponse>>> call, Throwable t) {
                // Silent fail for message loading
            }
        });
    }

    private void refreshMessages() {
        if (backendRoomId > 0) {
            loadMessagesFromApi();
        } else if (room != null) {
            adapter.submitList(room.getMessages());
            if (!room.getMessages().isEmpty()) {
                rvMessages.scrollToPosition(room.getMessages().size() - 1);
            }
        }
    }
}
