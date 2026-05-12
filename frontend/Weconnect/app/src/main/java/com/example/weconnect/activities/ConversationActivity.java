package com.example.weconnect.activities;

import android.content.Intent;
import android.os.Bundle;
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
import com.example.weconnect.activities.UserProfileActivity;
import com.example.weconnect.adapters.MessageAdapter;
import com.example.weconnect.api.ChatApiService;
import com.example.weconnect.api.RetrofitClient;
import com.example.weconnect.data.FakePostRepository;
import com.example.weconnect.data.FakeSocialRepository;
import com.example.weconnect.models.ApiResponse;
import com.example.weconnect.models.ChatMessage;
import com.example.weconnect.models.ChatMessageApiResponse;
import com.example.weconnect.models.ChatRoom;
import com.example.weconnect.utils.UserActionBottomSheet;
import com.example.weconnect.websocket.WebSocketManager;
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

    // Member info maps populated from room API response
    private final Map<String, Long> memberNameToId = new HashMap<>();
    private final Map<String, String> memberNameToAvatar = new HashMap<>();
    private long otherUserId = -1;
    private String otherUserName = "";
    private String otherUserAvatar = "";

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
        // Reconnect nếu WebSocket bị mất kết nối
        if (!WebSocketManager.getInstance().isConnected()) {
            String token = RetrofitClient.getAuthToken();
            if (token != null) {
                WebSocketManager.getInstance().connect(RetrofitClient.getBaseUrl(), token);
            }
        }
        if (backendRoomId > 0) {
            WebSocketManager.getInstance().subscribeToRoom(backendRoomId, this::onNewMessage);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        WebSocketManager.getInstance().unsubscribeFromRoom(backendRoomId);
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
        adapter = new MessageAdapter(this);
        adapter.setOnUserClickListener((userId, userName, avatarUrl) ->
                UserActionBottomSheet.show(this, userId, userName, avatarUrl));
        rvMessages.setLayoutManager(new LinearLayoutManager(this));
        rvMessages.setAdapter(adapter);
    }

    private void setupClickListeners() {
        ivBackConversation.setOnClickListener(v -> finish());
        btnSendMessage.setOnClickListener(v -> sendMessage());
        ivChatSettings.setOnClickListener(v -> showChatMenu());
    }

    private void showChatMenu() {
        if (room == null) return;
        boolean isOwner = room.isOwner(currentUsername);

        BottomSheetDialog sheet = new BottomSheetDialog(this);
        sheet.getBehavior().setSkipCollapsed(true);

        LinearLayout root = buildIosRoot();

        // ─── Group 1: actions ───
        LinearLayout group1 = buildIosGroup();

        TextView tvTitle = new TextView(this);
        tvTitle.setText(room.getTitle());
        tvTitle.setTextSize(13);
        tvTitle.setTextColor(0xFF8E8E93);
        tvTitle.setGravity(Gravity.CENTER);
        tvTitle.setPadding(dpPx(16), dpPx(13), dpPx(16), dpPx(4));
        group1.addView(tvTitle, matchW());

        addIosSep(group1);
        addIosRow(group1, "Xem thành viên nhóm", 0xFF1C1C1E, v -> {
            sheet.dismiss();
            showMemberManagementDialog();
        });

        if (isOwner) {
            addIosSep(group1);
            addIosRow(group1, "Xóa nhóm", 0xFFFF3B30, v -> {
                sheet.dismiss();
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                        .setTitle("Xóa nhóm?")
                        .setMessage("Toàn bộ tin nhắn và nhóm này sẽ bị xóa vĩnh viễn.")
                        .setPositiveButton("Xóa nhóm", (d, w) -> deleteRoom())
                        .setNegativeButton("Huỷ", null)
                        .show();
            });
        }

        root.addView(group1, matchW());
        addGroupGap(root);

        // ─── Group 2: cancel ───
        LinearLayout group2 = buildIosGroup();
        addIosRow(group2, "Đóng", 0xFF1C1C1E, v -> sheet.dismiss());
        root.addView(group2, matchW());

        sheet.setContentView(root);
        makeSheetTransparent(root);
        sheet.show();
    }

    private void deleteRoom() {
        if (backendRoomId <= 0) return;
        RetrofitClient.loadToken(this);
        chatApi.deleteRoom(backendRoomId).enqueue(new Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {
                Toast.makeText(ConversationActivity.this, "Đã xóa", Toast.LENGTH_SHORT).show();
                finish();
            }
            @Override
            public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                Toast.makeText(ConversationActivity.this, "Lỗi kết nối", Toast.LENGTH_SHORT).show();
            }
        });
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
                    WebSocketManager.getInstance().subscribeToRoom(backendRoomId, ConversationActivity.this::onNewMessage);
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
        long myId = RetrofitClient.getUserId(this);
        memberNameToId.clear();
        memberNameToAvatar.clear();
        if (data.getMembers() != null) {
            for (com.example.weconnect.models.ChatRoomApiResponse.MemberInfo m : data.getMembers()) {
                String name = m.getFullName() != null ? m.getFullName() : "";
                if (!name.isEmpty()) memberNames.add(name);
                if (m.getId() > 0) {
                    memberNameToId.put(name, m.getId());
                    if (m.getAvatarUrl() != null && !m.getAvatarUrl().isEmpty()) {
                        RetrofitClient.cacheAvatarForUser(m.getId(), m.getAvatarUrl());
                        memberNameToAvatar.put(name, m.getAvatarUrl());
                    }
                    if (m.getId() != myId) {
                        otherUserId = m.getId();
                        otherUserName = name;
                        otherUserAvatar = m.getAvatarUrl() != null ? m.getAvatarUrl() : "";
                    }
                }
            }
        }

        ChatRoom chatRoom = new ChatRoom(id, title, subtitle, postStatusLabel, type,
                R.drawable.ic_user_placeholder,
                active, inactiveLabel, new ArrayList<>(), ownerName, memberNames, new ArrayList<>());

        // For DM: set the other participant's avatar so the header shows a real photo
        if (ChatRoom.TYPE_DIRECT.equals(type) && data.getMembers() != null) {
            for (com.example.weconnect.models.ChatRoomApiResponse.MemberInfo m : data.getMembers()) {
                if (m.getId() != myId
                        && m.getAvatarUrl() != null && !m.getAvatarUrl().isEmpty()) {
                    String mUrl = m.getAvatarUrl();
                    if (mUrl.startsWith("/")) mUrl = RetrofitClient.getBaseUrl() + mUrl.substring(1);
                    chatRoom.setAvatarUrl(mUrl);
                    break;
                }
            }
        }
        return chatRoom;
    }

    private void displayRoom() {
        if (room == null) return;

        if (room.getAvatarUrl() != null && !room.getAvatarUrl().isEmpty()) {
            com.bumptech.glide.Glide.with(this)
                    .load(room.getAvatarUrl())
                    .placeholder(R.drawable.ic_user_placeholder)
                    .error(R.drawable.ic_user_placeholder)
                    .circleCrop()
                    .into(ivConversationAvatar);
        } else {
            ivConversationAvatar.setImageResource(room.getAvatarResId());
        }
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
        List<String> members = room.getMembers();
        List<String> pending = room.getPendingMembers();

        BottomSheetDialog sheet = new BottomSheetDialog(this);
        sheet.getBehavior().setSkipCollapsed(true);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(0x00000000);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundColor(0x00000000);
        int p = dpPx(10);
        content.setPadding(p, 0, p, p);

        // ─── Group 1: active members ───
        LinearLayout group1 = buildIosGroup();
        TextView tvMembersHeader = new TextView(this);
        tvMembersHeader.setText("Thành viên  " + members.size());
        tvMembersHeader.setTextSize(13);
        tvMembersHeader.setTextColor(0xFF8E8E93);
        tvMembersHeader.setGravity(Gravity.CENTER);
        tvMembersHeader.setPadding(dpPx(16), dpPx(13), dpPx(16), dpPx(4));
        group1.addView(tvMembersHeader, matchW());
        for (String memberName : members) {
            addIosSep(group1);
            group1.addView(buildMemberRow(memberName,
                    isOwner && !memberName.equalsIgnoreCase(currentUsername), sheet));
        }
        content.addView(group1, matchW());

        // ─── Group 2: pending (owner only) ───
        if (isOwner && !pending.isEmpty()) {
            addGroupGap(content);
            LinearLayout group2 = buildIosGroup();
            TextView tvPendingHeader = new TextView(this);
            tvPendingHeader.setText("Chờ duyệt  " + pending.size());
            tvPendingHeader.setTextSize(13);
            tvPendingHeader.setTextColor(0xFF8E8E93);
            tvPendingHeader.setGravity(Gravity.CENTER);
            tvPendingHeader.setPadding(dpPx(16), dpPx(13), dpPx(16), dpPx(4));
            group2.addView(tvPendingHeader, matchW());
            for (String pName : pending) {
                addIosSep(group2);
                group2.addView(buildPendingRow(pName, sheet));
            }
            content.addView(group2, matchW());
        }

        // ─── Group 3: add friend (owner of friend_group) ───
        if (isOwner && isFriendGroup) {
            addGroupGap(content);
            LinearLayout groupAdd = buildIosGroup();
            addIosRow(groupAdd, "Thêm bạn bè vào nhóm", 0xFF007AFF, v -> {
                sheet.dismiss();
                showAddFriendToGroupDialog();
            });
            content.addView(groupAdd, matchW());
        }

        addGroupGap(content);

        // ─── Group last: close ───
        LinearLayout groupClose = buildIosGroup();
        addIosRow(groupClose, "Đóng", 0xFF1C1C1E, v -> sheet.dismiss());
        content.addView(groupClose, matchW());

        sv.addView(content);
        sheet.setContentView(sv);
        makeSheetTransparent(sv);
        sheet.show();
    }

    private View buildMemberRow(String memberName, boolean canRemove, BottomSheetDialog parentSheet) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dpPx(16), dpPx(12), dpPx(16), dpPx(12));

        Long memberId = memberNameToId.get(memberName);
        long myId = RetrofitClient.getUserId(this);
        boolean isSelf = memberId != null && memberId == myId;

        if (!isSelf && memberId != null) {
            android.util.TypedValue tv = new android.util.TypedValue();
            getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
            row.setBackgroundResource(tv.resourceId);
            row.setClickable(true);
            row.setFocusable(true);
            long fId = memberId;
            row.setOnClickListener(v -> {
                String av = memberNameToAvatar.getOrDefault(memberName, "");
                UserActionBottomSheet.show(this, fId, memberName, av);
            });
        }

        // Avatar 44dp circle
        ImageView ivAvatar = new ImageView(this);
        int avatarSize = dpPx(44);
        ivAvatar.setLayoutParams(new LinearLayout.LayoutParams(avatarSize, avatarSize));
        ivAvatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        ivAvatar.setClipToOutline(true);
        android.graphics.drawable.GradientDrawable circleBg = new android.graphics.drawable.GradientDrawable();
        circleBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        circleBg.setColor(0xFFE0E0E0);
        ivAvatar.setBackground(circleBg);
        ivAvatar.setImageResource(R.drawable.ic_user_placeholder);

        String cachedAvatar = memberId != null ? RetrofitClient.getCachedAvatarForUser(memberId) : null;
        if (cachedAvatar == null) cachedAvatar = memberNameToAvatar.get(memberName);
        if (cachedAvatar != null && !cachedAvatar.isEmpty()) {
            String url = cachedAvatar.startsWith("/")
                    ? RetrofitClient.getBaseUrl() + cachedAvatar.substring(1) : cachedAvatar;
            com.bumptech.glide.Glide.with(this).load(url)
                    .placeholder(R.drawable.ic_user_placeholder)
                    .circleCrop().into(ivAvatar);
        }
        row.addView(ivAvatar);

        // Name
        boolean isOwnerMember = room != null && room.isOwner(memberName);
        TextView tvName = new TextView(this);
        tvName.setText(isOwnerMember ? memberName + "  👑" : memberName);
        tvName.setTextSize(16);
        tvName.setTextColor(0xFF1C1C1E);
        tvName.setTypeface(null, isOwnerMember ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        nameLp.leftMargin = dpPx(12);
        tvName.setLayoutParams(nameLp);
        row.addView(tvName);

        // "Xóa" text link for owner
        if (canRemove) {
            TextView tvRemove = new TextView(this);
            tvRemove.setText("Xóa");
            tvRemove.setTextSize(14);
            tvRemove.setTextColor(0xFFFF3B30);
            tvRemove.setClickable(true);
            tvRemove.setFocusable(true);
            tvRemove.setPadding(dpPx(8), dpPx(4), 0, dpPx(4));
            tvRemove.setOnClickListener(v -> {
                room.removeMember(memberName);
                Toast.makeText(this, "Đã xóa " + memberName, Toast.LENGTH_SHORT).show();
                parentSheet.dismiss();
                showMemberManagementDialog();
            });
            row.addView(tvRemove);
        }

        return row;
    }

    private View buildPendingRow(String pendingName, BottomSheetDialog parentSheet) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dpPx(16), dpPx(12), dpPx(16), dpPx(12));

        // Avatar 44dp
        ImageView ivAvatar = new ImageView(this);
        int avatarSize = dpPx(44);
        ivAvatar.setLayoutParams(new LinearLayout.LayoutParams(avatarSize, avatarSize));
        ivAvatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        ivAvatar.setClipToOutline(true);
        android.graphics.drawable.GradientDrawable circleBg = new android.graphics.drawable.GradientDrawable();
        circleBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        circleBg.setColor(0xFFE0E0E0);
        ivAvatar.setBackground(circleBg);
        ivAvatar.setImageResource(R.drawable.ic_user_placeholder);
        row.addView(ivAvatar);

        // Name
        TextView tvName = new TextView(this);
        tvName.setText(pendingName);
        tvName.setTextSize(16);
        tvName.setTextColor(0xFF1C1C1E);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        nameLp.leftMargin = dpPx(12);
        tvName.setLayoutParams(nameLp);
        row.addView(tvName);

        // "Duyệt" green link
        TextView tvApprove = new TextView(this);
        tvApprove.setText("Duyệt");
        tvApprove.setTextSize(14);
        tvApprove.setTextColor(0xFF34C759);
        tvApprove.setClickable(true);
        tvApprove.setFocusable(true);
        tvApprove.setPadding(dpPx(8), dpPx(4), 0, dpPx(4));
        tvApprove.setOnClickListener(v -> {
            room.addMember(pendingName);
            Toast.makeText(this, "Đã duyệt " + pendingName, Toast.LENGTH_SHORT).show();
            parentSheet.dismiss();
            showMemberManagementDialog();
        });
        row.addView(tvApprove);

        // "Từ chối" red link
        TextView tvReject = new TextView(this);
        tvReject.setText("Từ chối");
        tvReject.setTextSize(14);
        tvReject.setTextColor(0xFFFF3B30);
        tvReject.setClickable(true);
        tvReject.setFocusable(true);
        LinearLayout.LayoutParams rejectLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rejectLp.leftMargin = dpPx(12);
        tvReject.setLayoutParams(rejectLp);
        tvReject.setPadding(0, dpPx(4), 0, dpPx(4));
        tvReject.setOnClickListener(v -> {
            room.rejectPendingMember(pendingName);
            Toast.makeText(this, "Đã từ chối " + pendingName, Toast.LENGTH_SHORT).show();
            parentSheet.dismiss();
            showMemberManagementDialog();
        });
        row.addView(tvReject);

        return row;
    }

    private void showAddFriendToGroupDialog() {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        sheet.getBehavior().setSkipCollapsed(true);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(0x00000000);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundColor(0x00000000);
        int p = dpPx(10);
        content.setPadding(p, 0, p, p);

        LinearLayout group1 = buildIosGroup();

        TextView tvHeader = new TextView(this);
        tvHeader.setText("Thêm bạn bè vào nhóm");
        tvHeader.setTextSize(13);
        tvHeader.setTextColor(0xFF8E8E93);
        tvHeader.setGravity(Gravity.CENTER);
        tvHeader.setPadding(dpPx(16), dpPx(13), dpPx(16), dpPx(4));
        group1.addView(tvHeader, matchW());

        List<String> allFriends = FakeSocialRepository.getInstance().getFriendNames();
        boolean hasAvailable = false;
        for (String friend : allFriends) {
            if (room.isMember(friend)) continue;
            hasAvailable = true;

            addIosSep(group1);

            LinearLayout friendRow = new LinearLayout(this);
            friendRow.setOrientation(LinearLayout.HORIZONTAL);
            friendRow.setGravity(Gravity.CENTER_VERTICAL);
            friendRow.setPadding(dpPx(16), dpPx(12), dpPx(16), dpPx(12));
            android.util.TypedValue tv = new android.util.TypedValue();
            getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
            friendRow.setBackgroundResource(tv.resourceId);
            friendRow.setClickable(true);

            // Avatar
            ImageView ivA = new ImageView(this);
            int s = dpPx(44);
            ivA.setLayoutParams(new LinearLayout.LayoutParams(s, s));
            ivA.setScaleType(ImageView.ScaleType.CENTER_CROP);
            ivA.setClipToOutline(true);
            android.graphics.drawable.GradientDrawable circleBg = new android.graphics.drawable.GradientDrawable();
            circleBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            circleBg.setColor(0xFFE0E0E0);
            ivA.setBackground(circleBg);
            ivA.setImageResource(R.drawable.ic_user_placeholder);
            friendRow.addView(ivA);

            // Name
            TextView tvName = new TextView(this);
            tvName.setText(friend);
            tvName.setTextSize(16);
            tvName.setTextColor(0xFF1C1C1E);
            LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            nameLp.leftMargin = dpPx(12);
            tvName.setLayoutParams(nameLp);
            friendRow.addView(tvName);

            // "Thêm" blue link
            TextView tvAdd = new TextView(this);
            tvAdd.setText("Thêm");
            tvAdd.setTextSize(14);
            tvAdd.setTextColor(0xFF007AFF);
            tvAdd.setClickable(true);
            tvAdd.setFocusable(true);
            tvAdd.setPadding(dpPx(8), dpPx(4), 0, dpPx(4));
            String finalFriend = friend;
            tvAdd.setOnClickListener(v2 -> {
                room.addMember(finalFriend);
                Toast.makeText(this, "Đã thêm " + finalFriend + " vào nhóm",
                        Toast.LENGTH_SHORT).show();
                sheet.dismiss();
                showMemberManagementDialog();
            });
            friendRow.addView(tvAdd);

            group1.addView(friendRow, matchW());
        }

        if (!hasAvailable) {
            addIosSep(group1);
            TextView tvEmpty = new TextView(this);
            tvEmpty.setText("Tất cả bạn bè đã ở trong nhóm");
            tvEmpty.setTextSize(15);
            tvEmpty.setTextColor(0xFF8E8E93);
            tvEmpty.setGravity(Gravity.CENTER);
            tvEmpty.setPadding(dpPx(16), dpPx(20), dpPx(16), dpPx(20));
            group1.addView(tvEmpty, matchW());
        }

        content.addView(group1, matchW());
        addGroupGap(content);

        LinearLayout groupClose = buildIosGroup();
        addIosRow(groupClose, "Đóng", 0xFF1C1C1E, v -> sheet.dismiss());
        content.addView(groupClose, matchW());

        sv.addView(content);
        sheet.setContentView(sv);
        makeSheetTransparent(sv);
        sheet.show();
    }

    // ── iOS-style sheet helpers ──

    private LinearLayout buildIosRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0x00000000);
        int p = dpPx(10);
        root.setPadding(p, 0, p, p);
        return root;
    }

    private LinearLayout buildIosGroup() {
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(0xFFFFFFFF);
        bg.setCornerRadius(dpPx(14));
        ll.setBackground(bg);
        ll.setClipToOutline(true);
        return ll;
    }

    private void addIosRow(LinearLayout parent, String text, int color,
                            View.OnClickListener listener) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(17);
        tv.setTextColor(color);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dpPx(20), dpPx(15), dpPx(20), dpPx(15));
        if (listener != null) {
            tv.setClickable(true);
            tv.setFocusable(true);
            android.util.TypedValue tv2 = new android.util.TypedValue();
            getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv2, true);
            tv.setBackgroundResource(tv2.resourceId);
            tv.setOnClickListener(listener);
        }
        parent.addView(tv, matchW());
    }

    private void addIosSep(LinearLayout parent) {
        View sep = new View(this);
        sep.setBackgroundColor(0xFFD1D1D6);
        sep.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
        parent.addView(sep);
    }

    private void addGroupGap(LinearLayout parent) {
        View gap = new View(this);
        gap.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpPx(8)));
        parent.addView(gap);
    }

    private void makeSheetTransparent(View contentRoot) {
        contentRoot.post(() -> {
            if (contentRoot.getParent() instanceof View) {
                ((View) contentRoot.getParent()).setBackgroundColor(0x00000000);
            }
        });
    }

    private LinearLayout.LayoutParams matchW() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dpPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void sendMessage() {
        if (room == null || backendRoomId <= 0) return;

        String content = etMessageInput.getText() != null
                ? etMessageInput.getText().toString().trim() : "";
        if (TextUtils.isEmpty(content)) return;

        etMessageInput.setText("");
        WebSocketManager.getInstance().sendMessage(backendRoomId, content);
    }

    private void onNewMessage(ChatMessageApiResponse msg) {
        long currentUserId = RetrofitClient.getUserId(this);
        boolean sentByMe = msg.getSenderId() == currentUserId;
        String time = "";
        if (msg.getCreatedAt() != null) {
            String raw = msg.getCreatedAt();
            if (raw.contains("T") && raw.length() >= 16) time = raw.substring(11, 16);
            else time = raw;
        }
        ChatMessage chatMsg = new ChatMessage(
                String.valueOf(msg.getId()),
                msg.getSenderId(),
                msg.getSenderName() != null ? msg.getSenderName() : "",
                msg.getContent() != null ? msg.getContent() : "",
                time,
                sentByMe
        );

        // Kiểm tra trùng lặp (tránh duplicate khi load history và WS cùng lúc)
        List<ChatMessage> current = adapter.getMessages();
        for (ChatMessage existing : current) {
            if (String.valueOf(msg.getId()).equals(existing.getId())) return;
        }

        List<ChatMessage> updated = new ArrayList<>(current);
        updated.add(chatMsg);
        adapter.submitList(updated);
        rvMessages.scrollToPosition(updated.size() - 1);
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
                        messages.add(new ChatMessage(id, msgData.getSenderId(), sender, msgContent, time, sentByMe));
                    }
                    adapter.submitList(messages);
                    if (!messages.isEmpty()) {
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
