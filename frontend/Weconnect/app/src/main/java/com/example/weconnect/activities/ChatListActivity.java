package com.example.weconnect.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.weconnect.R;
import com.example.weconnect.adapters.ChatRoomAdapter;
import com.example.weconnect.util.BadgeManager;
import com.example.weconnect.api.ChatApiService;
import com.example.weconnect.api.RetrofitClient;
import com.example.weconnect.models.ApiResponse;
import com.example.weconnect.models.ChatMessage;
import com.example.weconnect.models.ChatRoom;
import com.example.weconnect.models.ChatRoomApiResponse;
import com.example.weconnect.websocket.WebSocketManager;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ChatListActivity extends AppCompatActivity {

    private static final String TAB_ACTIVITY = "activity";
    private static final String TAB_CONTACT = "contact";

    private ImageView ivNewChat;
    private EditText etChatSearch;
    private RecyclerView rvChats;
    private FrameLayout btnHome;
    private FrameLayout btnMessages;
    private FrameLayout btnNotifications;
    private FrameLayout btnProfile;
    private TabLayout tabChatType;
    private ChatRoomAdapter adapter;
    private String currentTab = TAB_ACTIVITY;
    private TextView tvNotifBadge;

    // Cached rooms from API
    private List<ChatRoom> allRooms = new ArrayList<>();
    private List<ChatRoom> messageRequestRooms = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        initViews();
        setupRecyclerView();
        setupTabs();
        setupClickListeners();
        setupSearch();
        applyIncomingContext();
        loadChatsFromApi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        BadgeManager.applyBadge(tvNotifBadge);
        loadChatsFromApi();
        WebSocketManager.getInstance().subscribeToChatList(payload -> loadChatsFromApi());
    }

    @Override
    protected void onPause() {
        super.onPause();
        WebSocketManager.getInstance().unsubscribeFromChatList();
    }

    private void initViews() {
        ivNewChat = findViewById(R.id.ivNewChat);
        etChatSearch = findViewById(R.id.etChatSearch);
        rvChats = findViewById(R.id.rvChats);
        btnHome = findViewById(R.id.btnHome);
        btnMessages = findViewById(R.id.btnMessages);
        btnNotifications = findViewById(R.id.btnNotifications);
        btnProfile = findViewById(R.id.btnProfile);
        tabChatType = findViewById(R.id.tabChatType);
        tvNotifBadge = findViewById(R.id.tvNotifBadge);
    }

    private void setupRecyclerView() {
        adapter = new ChatRoomAdapter(this::openRoom);
        rvChats.setLayoutManager(new LinearLayoutManager(this));
        rvChats.setAdapter(adapter);
        btnMessages.setAlpha(1.0f);
    }

    private void setupTabs() {
        tabChatType.addTab(tabChatType.newTab().setText("Hoạt động"));
        tabChatType.addTab(tabChatType.newTab().setText("Liên hệ"));
        tabChatType.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                currentTab = tab != null && tab.getPosition() == 1 ? TAB_CONTACT : TAB_ACTIVITY;
                filterAndDisplay();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
    }

    private void setupClickListeners() {
        ivNewChat.setOnClickListener(v -> showNewChatDialog());

        btnHome.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
            overridePendingTransition(0, 0);
        });

        btnNotifications.setOnClickListener(v -> {
            Intent intent = new Intent(this, NotificationsActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
            overridePendingTransition(0, 0);
        });

        btnProfile.setOnClickListener(v -> {
            Intent intent = new Intent(this, UserProfileActivity.class);
            intent.putExtra("username", RetrofitClient.getUserName(this));
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
            overridePendingTransition(0, 0);
        });
    }

    private void setupSearch() {
        etChatSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterAndDisplay();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void applyIncomingContext() {
        String highlightTag = getIntent().getStringExtra("highlight_tag");
        if (highlightTag != null && !highlightTag.trim().isEmpty()) {
            currentTab = TAB_ACTIVITY;
            TabLayout.Tab groupTab = tabChatType.getTabAt(0);
            if (groupTab != null) {
                groupTab.select();
            }
            etChatSearch.setText(highlightTag);
            etChatSearch.setSelection(highlightTag.length());
        }
    }

    /**
     * Load danh sách phòng chat từ backend API.
     */
    private void loadChatsFromApi() {
        RetrofitClient.loadToken(this);
        String token = RetrofitClient.getAuthToken();

        if (token == null) {
            Toast.makeText(this, "Vui lòng đăng nhập để xem tin nhắn", Toast.LENGTH_SHORT).show();
            return;
        }

        ChatApiService chatApi = RetrofitClient.getClient().create(ChatApiService.class);
        chatApi.getRooms().enqueue(new Callback<ApiResponse<List<ChatRoomApiResponse>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<ChatRoomApiResponse>>> call,
                                   Response<ApiResponse<List<ChatRoomApiResponse>>> response) {
                if (response.isSuccessful() && response.body() != null
                        && response.body().getResult() != null) {
                    allRooms = convertRooms(response.body().getResult());
                    filterAndDisplay();
                    loadMessageRequestsFromApi(chatApi);
                } else {
                    Toast.makeText(ChatListActivity.this,
                            "Không thể tải danh sách chat", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<ChatRoomApiResponse>>> call, Throwable t) {
                Toast.makeText(ChatListActivity.this,
                        "Lỗi kết nối: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadMessageRequestsFromApi(ChatApiService chatApi) {
        chatApi.getMessageRequests().enqueue(new Callback<ApiResponse<List<ChatRoomApiResponse>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<ChatRoomApiResponse>>> call,
                                   Response<ApiResponse<List<ChatRoomApiResponse>>> response) {
                if (response.isSuccessful() && response.body() != null
                        && response.body().getResult() != null) {
                    messageRequestRooms = convertRooms(response.body().getResult());
                    filterAndDisplay();
                } else {
                    messageRequestRooms = new ArrayList<>();
                    filterAndDisplay();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<ChatRoomApiResponse>>> call, Throwable t) {
                messageRequestRooms = new ArrayList<>();
                filterAndDisplay();
            }
        });
    }

    /**
     * Convert typed API response to ChatRoom list.
     */
    private List<ChatRoom> convertRooms(List<ChatRoomApiResponse> data) {
        List<ChatRoom> rooms = new ArrayList<>();
        for (ChatRoomApiResponse item : data) {
            try {
                String id = String.valueOf(item.getId());
                String title = item.getTitle() != null ? item.getTitle() : "Phòng chat";
                String type = item.getType() != null ? item.getType() : "group";
                boolean active = item.isActive();
                String inactiveLabel = item.getInactiveStatusLabel() != null
                        ? item.getInactiveStatusLabel() : "";
                String subtitle = item.getSubtitle();
                String postStatusLabel = item.getPostStatusLabel();
                String lastPreview = item.getLastMessagePreview() != null
                        ? item.getLastMessagePreview() : "";
                String lastTime = item.getLastMessageTime() != null
                        ? item.getLastMessageTime() : "";

                // Build members list
                List<String> memberNames = new ArrayList<>();
                String ownerName = item.getOwnerName() != null ? item.getOwnerName() : "";
                if (item.getMembers() != null) {
                    for (ChatRoomApiResponse.MemberInfo m : item.getMembers()) {
                        String name = m.getFullName() != null ? m.getFullName() : "";
                        if (!name.isEmpty()) memberNames.add(name);
                    }
                }

                // Create a simple ChatMessage for last preview
                List<ChatMessage> messages = new ArrayList<>();
                if (!lastPreview.isEmpty() && !"Chưa có tin nhắn".equals(lastPreview)) {
                    String formattedTime = formatTime(lastTime);
                    messages.add(new ChatMessage(
                            "0", "", lastPreview, formattedTime, false
                    ));
                }

                ChatRoom room = new ChatRoom(
                        id, title, subtitle, postStatusLabel, type,
                        R.drawable.ic_user_placeholder,
                        active, inactiveLabel,
                        messages, ownerName, memberNames, new ArrayList<>()
                );
                room.setLastMessageTimeRaw(lastTime);
                room.setCreatedAt(item.getCreatedAt() != null ? item.getCreatedAt() : "");
                room.setFriend(item.isFriend());
                room.setMessageRequest(item.isMessageRequest());
                room.setOtherUserId(item.getOtherUserId());
                room.setOtherUserName(item.getOtherUserName());
                room.setOtherUserAvatarUrl(item.getOtherUserAvatarUrl());
                room.setBlockedByMe(item.isBlockedByMe());
                room.setHasBlockedMe(item.hasBlockedMe());
                room.setBlockedBetweenUsers(item.isBlockedBetweenUsers());
                room.setOtherUserOnline(item.isOtherUserOnline());
                room.setOtherUserLastActiveMins(item.getOtherUserLastActiveMins());
                room.setUnreadCount(item.getUnreadCount());
                room.setActivityDateDisplay(item.getActivityDateDisplay());
                room.setMaxMembers(item.getMaxMembers());
                if (ChatRoom.TYPE_DIRECT.equals(type)
                        && item.getOtherUserAvatarUrl() != null
                        && !item.getOtherUserAvatarUrl().isEmpty()) {
                    String mUrl = item.getOtherUserAvatarUrl();
                    if (mUrl.startsWith("/")) mUrl = RetrofitClient.getBaseUrl() + mUrl.substring(1);
                    room.setAvatarUrl(mUrl);
                }

                if (item.getMembers() != null) {
                    long myId = RetrofitClient.getUserId(ChatListActivity.this);
                    if (ChatRoom.TYPE_DIRECT.equals(type)) {
                        // DM: avatar của người còn lại
                        for (ChatRoomApiResponse.MemberInfo m : item.getMembers()) {
                            if (m.getId() != myId) {
                                // Ưu tiên global cache (realtime) → fallback API data
                                String cachedUrl = RetrofitClient.getCachedAvatarForUser(m.getId());
                                String mUrl = (cachedUrl != null && !cachedUrl.isEmpty())
                                        ? cachedUrl : m.getAvatarUrl();
                                if (mUrl != null && !mUrl.isEmpty()) {
                                    if (mUrl.startsWith("/")) mUrl = RetrofitClient.getBaseUrl() + mUrl.substring(1);
                                    room.setAvatarUrl(mUrl);
                                }
                                break;
                            }
                        }
                    } else {
                        // Group/Activity: avatar của owner (role="owner")
                        for (ChatRoomApiResponse.MemberInfo m : item.getMembers()) {
                            if ("owner".equalsIgnoreCase(m.getRole())) {
                                String cachedUrl = RetrofitClient.getCachedAvatarForUser(m.getId());
                                String mUrl = (cachedUrl != null && !cachedUrl.isEmpty())
                                        ? cachedUrl : m.getAvatarUrl();
                                if (mUrl != null && !mUrl.isEmpty()) {
                                    if (mUrl.startsWith("/")) mUrl = RetrofitClient.getBaseUrl() + mUrl.substring(1);
                                    room.setAvatarUrl(mUrl);
                                }
                                break;
                            }
                        }
                    }
                }

                rooms.add(room);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return rooms;
    }

    /**
     * Format LocalDateTime string to short display.
     */
    private String formatTime(String isoTime) {
        if (isoTime == null || isoTime.isEmpty()) return "";
        try {
            // Format: "2026-04-18T21:04:10"
            if (isoTime.contains("T") && isoTime.length() >= 16) {
                return isoTime.substring(11, 16); // "HH:mm"
            }
        } catch (Exception ignored) {}
        return isoTime;
    }

    /**
     * Filter allRooms by tab and search, then display.
     */
    private void filterAndDisplay() {
        String query = etChatSearch.getText() != null
                ? etChatSearch.getText().toString().trim().toLowerCase() : "";

        List<ChatRoom> filtered = new ArrayList<>();
        for (ChatRoom room : allRooms) {
            // Filter by tab
            boolean matchesTab;
            if (TAB_ACTIVITY.equals(currentTab)) {
                matchesTab = ChatRoom.TYPE_ACTIVITY.equals(room.getType())
                        || ChatRoom.TYPE_GROUP.equals(room.getType());
            } else {
                matchesTab = ChatRoom.TYPE_DIRECT.equals(room.getType())
                        || ChatRoom.TYPE_FRIEND_GROUP.equals(room.getType());
            }
            if (!matchesTab) continue;
            // Tin nhắn từ người lạ (chưa reply) không hiển thị ở list chat chính
            if (TAB_CONTACT.equals(currentTab)
                    && ChatRoom.TYPE_DIRECT.equals(room.getType())
                    && room.isMessageRequest()) {
                continue;
            }

            // Filter by search query
            if (!query.isEmpty()) {
                String title = room.getTitle() != null ? room.getTitle().toLowerCase() : "";
                String preview = room.getLastMessagePreview() != null
                        ? room.getLastMessagePreview().toLowerCase() : "";
                if (!title.contains(query) && !preview.contains(query)) continue;
            }

            filtered.add(room);
        }

        // Thêm summary "Tin nhắn từ người lạ" nếu có message request chưa reply
        if (TAB_CONTACT.equals(currentTab) && !messageRequestRooms.isEmpty()) {
            ChatRoom summary = buildMessageRequestsSummary();
            if (summary != null) {
                String title = summary.getTitle() != null ? summary.getTitle().toLowerCase() : "";
                String preview = summary.getLastMessagePreview() != null
                        ? summary.getLastMessagePreview().toLowerCase() : "";
                if (query.isEmpty() || title.contains(query) || preview.contains(query)) {
                    filtered.add(summary);
                }
            }
        }

        // Sort: tin nhắn mới nhất lên đầu; phòng chưa có tin dùng createdAt làm fallback
        Collections.sort(filtered, (a, b) -> {
            String keyA = !a.getLastMessageTimeRaw().isEmpty() ? a.getLastMessageTimeRaw() : a.getCreatedAt();
            String keyB = !b.getLastMessageTimeRaw().isEmpty() ? b.getLastMessageTimeRaw() : b.getCreatedAt();
            return keyB.compareTo(keyA);
        });

        adapter.submitList(filtered);
    }

    private ChatRoom buildMessageRequestsSummary() {
        if (messageRequestRooms.isEmpty()) return null;

        ChatRoom newest = messageRequestRooms.get(0);
        for (ChatRoom room : messageRequestRooms) {
            if (room.getLastMessageTimeRaw().compareTo(newest.getLastMessageTimeRaw()) > 0) {
                newest = room;
            }
        }

        String preview = "Chưa có trong danh bạ";

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("0", "", preview, newest.getLastMessageTime(), false));

        ChatRoom summary = new ChatRoom(
                "message_requests",
                "Tin nhắn từ người lạ",
                ChatRoom.TYPE_MESSAGE_REQUESTS,
                R.drawable.ic_chat,
                messages
        );
        summary.setLastMessageTimeRaw(newest.getLastMessageTimeRaw());
        summary.setRequestCount(messageRequestRooms.size());
        return summary;
    }

    private void openRoom(ChatRoom room) {
        if (ChatRoom.TYPE_MESSAGE_REQUESTS.equals(room.getType())) {
            startActivity(new Intent(this, MessageRequestsActivity.class));
            return;
        }

        Intent intent = new Intent(this, ConversationActivity.class);
        intent.putExtra("room_id", room.getId());
        startActivity(intent);
    }

    private void showNewChatDialog() {
        com.google.android.material.bottomsheet.BottomSheetDialog sheet =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        sheet.getBehavior().setSkipCollapsed(true);

        final int cText   = 0xFF1C1C1E;
        final int cSub    = 0xFF8E8E93;
        final int cSep    = 0xFFE8E4DE;
        final int cSearch = 0xFFF2F2F7;
        final int cPink   = getResources().getColor(R.color.primary_pink, null);
        final int cPinkDisabled = 0xFFDDD8E8;

        // ── Root: white card, rounded top corners ──
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        android.graphics.drawable.GradientDrawable rootBg = new android.graphics.drawable.GradientDrawable();
        rootBg.setColor(0xFFFFFFFF);
        float cr = ncDp(24);
        rootBg.setCornerRadii(new float[]{cr, cr, cr, cr, 0, 0, 0, 0});
        root.setBackground(rootBg);

        // ── Handle bar ──
        FrameLayout handleWrap = new FrameLayout(this);
        handleWrap.setPadding(0, ncDp(10), 0, ncDp(6));
        View handleBar = new View(this);
        FrameLayout.LayoutParams hblp = new FrameLayout.LayoutParams(
                ncDp(36), ncDp(4), android.view.Gravity.CENTER_HORIZONTAL);
        handleBar.setLayoutParams(hblp);
        android.graphics.drawable.GradientDrawable hbBg = new android.graphics.drawable.GradientDrawable();
        hbBg.setColor(0xFFD1D1D6);
        hbBg.setCornerRadius(ncDp(2));
        handleBar.setBackground(hbBg);
        handleWrap.addView(handleBar);
        root.addView(handleWrap, ncMatchW());

        // ── Title ──
        TextView tvTitle = new TextView(this);
        tvTitle.setText("Tin nhắn mới");
        tvTitle.setTextSize(20);
        tvTitle.setTextColor(cText);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setPadding(ncDp(20), ncDp(4), ncDp(20), 0);
        root.addView(tvTitle, ncMatchW());

        // ── Subtitle (updates with selection) ──
        TextView tvSubtitle = new TextView(this);
        tvSubtitle.setText("Chọn một người bạn để nhắn tin");
        tvSubtitle.setTextSize(13);
        tvSubtitle.setTextColor(cSub);
        LinearLayout.LayoutParams subLp = ncMatchW();
        subLp.topMargin = ncDp(3);
        subLp.bottomMargin = ncDp(18);
        tvSubtitle.setPadding(ncDp(20), 0, ncDp(20), 0);
        root.addView(tvSubtitle, subLp);

        // ── Search bar (gray rounded bg, icon + field) ──
        LinearLayout searchBar = new LinearLayout(this);
        searchBar.setOrientation(LinearLayout.HORIZONTAL);
        searchBar.setGravity(android.view.Gravity.CENTER_VERTICAL);
        android.graphics.drawable.GradientDrawable sbBg = new android.graphics.drawable.GradientDrawable();
        sbBg.setColor(cSearch);
        sbBg.setCornerRadius(ncDp(12));
        searchBar.setBackground(sbBg);
        LinearLayout.LayoutParams sbLp = ncMatchW();
        sbLp.setMargins(ncDp(16), 0, ncDp(16), ncDp(18));
        searchBar.setLayoutParams(sbLp);

        TextView tvSearchIcon = new TextView(this);
        tvSearchIcon.setText("🔍");
        tvSearchIcon.setTextSize(14);
        tvSearchIcon.setPadding(ncDp(12), ncDp(10), 0, ncDp(10));
        searchBar.addView(tvSearchIcon);

        EditText etSearch = new EditText(this);
        etSearch.setHint("Tìm bạn bè...");
        etSearch.setTextSize(14);
        etSearch.setTextColor(cText);
        etSearch.setHintTextColor(cSub);
        etSearch.setBackground(null);
        etSearch.setPadding(ncDp(8), ncDp(10), ncDp(12), ncDp(10));
        etSearch.setSingleLine(true);
        etSearch.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        searchBar.addView(etSearch);
        root.addView(searchBar);

        // ── Section label ──
        TextView tvSection = new TextView(this);
        tvSection.setText("BẠN BÈ");
        tvSection.setTextSize(11);
        tvSection.setTextColor(cSub);
        tvSection.setTypeface(null, android.graphics.Typeface.BOLD);
        tvSection.setPadding(ncDp(20), 0, ncDp(20), ncDp(8));
        root.addView(tvSection, ncMatchW());

        root.addView(ncSep(cSep));

        // ── Friend list (scrollable, 40% screen height) ──
        android.widget.ScrollView scrollFriends = new android.widget.ScrollView(this);
        int screenH = getResources().getDisplayMetrics().heightPixels;
        scrollFriends.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int) (screenH * 0.40f)));
        LinearLayout friendContainer = new LinearLayout(this);
        friendContainer.setOrientation(LinearLayout.VERTICAL);
        scrollFriends.addView(friendContainer);
        root.addView(scrollFriends);

        // ── Footer ──
        View footerSep = new View(this);
        LinearLayout.LayoutParams fsSepLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        fsSepLp.topMargin = ncDp(8);
        footerSep.setLayoutParams(fsSepLp);
        footerSep.setBackgroundColor(cSep);
        root.addView(footerSep);

        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setPadding(ncDp(16), ncDp(14), ncDp(16), ncDp(30));

        // "Nhắn tin" button — disabled initially
        android.graphics.drawable.GradientDrawable btnBg = new android.graphics.drawable.GradientDrawable();
        btnBg.setColor(cPinkDisabled);
        btnBg.setCornerRadius(ncDp(14));

        TextView btnSend = new TextView(this);
        btnSend.setText("Nhắn tin");
        btnSend.setTextSize(16);
        btnSend.setTypeface(null, android.graphics.Typeface.BOLD);
        btnSend.setGravity(android.view.Gravity.CENTER);
        btnSend.setPadding(0, ncDp(15), 0, ncDp(15));
        btnSend.setBackground(btnBg);
        btnSend.setTextColor(0xFFFFFFFF);
        btnSend.setAlpha(0.55f);
        btnSend.setClickable(false);
        footer.addView(btnSend, ncMatchW());

        // "Huỷ" text
        TextView btnCancel = new TextView(this);
        btnCancel.setText("Huỷ");
        btnCancel.setTextSize(15);
        btnCancel.setTextColor(cSub);
        btnCancel.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams cancelLp = ncMatchW();
        cancelLp.topMargin = ncDp(6);
        btnCancel.setLayoutParams(cancelLp);
        btnCancel.setClickable(true);
        btnCancel.setFocusable(true);
        android.util.TypedValue rippleCancel = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, rippleCancel, true);
        btnCancel.setBackgroundResource(rippleCancel.resourceId);
        btnCancel.setOnClickListener(v -> sheet.dismiss());
        footer.addView(btnCancel);

        root.addView(footer, ncMatchW());

        // ── Selected map + UI update Runnable ──
        java.util.Map<String, Long> selected = new java.util.LinkedHashMap<>();

        Runnable updateSelUI = () -> {
            int count = selected.size();
            if (count == 0) {
                btnBg.setColor(cPinkDisabled);
                btnSend.setAlpha(0.55f);
                btnSend.setClickable(false);
                btnSend.setText("Nhắn tin");
                tvSubtitle.setText("Chọn một người bạn để nhắn tin");
            } else if (count == 1) {
                btnBg.setColor(cPink);
                btnSend.setAlpha(1f);
                btnSend.setClickable(true);
                btnSend.setText("Nhắn tin");
                tvSubtitle.setText("1 người đã chọn");
            } else {
                btnBg.setColor(cPink);
                btnSend.setAlpha(1f);
                btnSend.setClickable(true);
                btnSend.setText("Tạo nhóm (" + count + ")");
                tvSubtitle.setText(count + " người đã chọn");
            }
        };

        // ── btnSend click ──
        btnSend.setOnClickListener(v -> {
            if (selected.isEmpty()) return;
            ChatApiService chatApi = RetrofitClient.getClient().create(ChatApiService.class);
            if (selected.size() == 1) {
                long friendId = selected.values().iterator().next();
                sheet.dismiss();
                chatApi.getDirectRoom(friendId).enqueue(new Callback<ApiResponse<ChatRoomApiResponse>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<ChatRoomApiResponse>> call,
                                           Response<ApiResponse<ChatRoomApiResponse>> resp) {
                        if (resp.isSuccessful() && resp.body() != null && resp.body().getResult() != null) {
                            Intent intent = new Intent(ChatListActivity.this, ConversationActivity.class);
                            intent.putExtra("room_id", String.valueOf(resp.body().getResult().getId()));
                            startActivity(intent);
                        } else {
                            Toast.makeText(ChatListActivity.this, "Không thể mở phòng chat", Toast.LENGTH_SHORT).show();
                        }
                    }
                    @Override
                    public void onFailure(Call<ApiResponse<ChatRoomApiResponse>> call, Throwable t) {
                        Toast.makeText(ChatListActivity.this, "Lỗi kết nối", Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
                List<Long> memberIds = new ArrayList<>(selected.values());
                String groupTitle = String.join(", ", selected.keySet());
                sheet.dismiss();
                Map<String, Object> body = new java.util.HashMap<>();
                body.put("title", groupTitle);
                body.put("memberIds", memberIds);
                chatApi.createGroupRoom(body).enqueue(new Callback<ApiResponse<ChatRoomApiResponse>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<ChatRoomApiResponse>> call,
                                           Response<ApiResponse<ChatRoomApiResponse>> resp) {
                        if (resp.isSuccessful() && resp.body() != null && resp.body().getResult() != null) {
                            Intent intent = new Intent(ChatListActivity.this, ConversationActivity.class);
                            intent.putExtra("room_id", String.valueOf(resp.body().getResult().getId()));
                            startActivity(intent);
                            loadChatsFromApi();
                        } else {
                            Toast.makeText(ChatListActivity.this, "Không thể tạo nhóm chat", Toast.LENGTH_SHORT).show();
                        }
                    }
                    @Override
                    public void onFailure(Call<ApiResponse<ChatRoomApiResponse>> call, Throwable t) {
                        Toast.makeText(ChatListActivity.this, "Lỗi kết nối", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });

        // ── Loading placeholder ──
        {
            TextView tvLoad = new TextView(this);
            tvLoad.setText("Đang tải...");
            tvLoad.setTextSize(14);
            tvLoad.setTextColor(cSub);
            tvLoad.setGravity(android.view.Gravity.CENTER);
            tvLoad.setPadding(0, ncDp(32), 0, ncDp(32));
            friendContainer.addView(tvLoad);
        }

        RetrofitClient.loadToken(this);
        RetrofitClient.getClient()
                .create(com.example.weconnect.api.FriendApiService.class)
                .getFriends()
                .enqueue(new Callback<ApiResponse<List<Map<String, Object>>>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<List<Map<String, Object>>>> call,
                                           Response<ApiResponse<List<Map<String, Object>>>> resp) {
                        List<Map<String, Object>> friends = new ArrayList<>();
                        if (resp.isSuccessful() && resp.body() != null && resp.body().getResult() != null)
                            friends.addAll(resp.body().getResult());

                        final List<Map<String, Object>> fl = friends;
                        buildFriendRows(friendContainer, fl, selected, updateSelUI, etSearch,
                                cText, cSub, cSep, cPink);

                        etSearch.addTextChangedListener(new TextWatcher() {
                            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
                            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                                buildFriendRows(friendContainer, fl, selected, updateSelUI, etSearch,
                                        cText, cSub, cSep, cPink);
                            }
                            @Override public void afterTextChanged(Editable s) {}
                        });
                    }
                    @Override
                    public void onFailure(Call<ApiResponse<List<Map<String, Object>>>> call, Throwable t) {
                        friendContainer.removeAllViews();
                        TextView err = new TextView(ChatListActivity.this);
                        err.setText("Lỗi kết nối. Vui lòng thử lại.");
                        err.setTextSize(14);
                        err.setTextColor(cSub);
                        err.setGravity(android.view.Gravity.CENTER);
                        err.setPadding(0, ncDp(32), 0, ncDp(32));
                        friendContainer.addView(err);
                    }
                });

        sheet.setContentView(root);
        root.post(() -> {
            if (root.getParent() instanceof View)
                ((View) root.getParent()).setBackgroundColor(0x00000000);
        });
        sheet.show();

        etSearch.requestFocus();
        etSearch.postDelayed(() -> {
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(etSearch, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        }, 300);
    }

    private void buildFriendRows(LinearLayout container,
                                  List<Map<String, Object>> friends,
                                  java.util.Map<String, Long> selected,
                                  Runnable onSelectionChanged,
                                  EditText etSearch,
                                  int cText, int cSub, int cSep, int cPink) {
        container.removeAllViews();
        String query = etSearch.getText() != null
                ? etSearch.getText().toString().trim().toLowerCase() : "";

        if (friends.isEmpty()) {
            LinearLayout emptyState = new LinearLayout(this);
            emptyState.setOrientation(LinearLayout.VERTICAL);
            emptyState.setGravity(android.view.Gravity.CENTER);
            emptyState.setPadding(ncDp(32), ncDp(40), ncDp(32), ncDp(40));

            TextView tvEmoji = new TextView(this);
            tvEmoji.setText("🤝");
            tvEmoji.setTextSize(40);
            tvEmoji.setGravity(android.view.Gravity.CENTER);
            emptyState.addView(tvEmoji, ncMatchW());

            TextView tvEmptyTitle = new TextView(this);
            tvEmptyTitle.setText("Bạn chưa có bạn bè nào");
            tvEmptyTitle.setTextSize(16);
            tvEmptyTitle.setTextColor(cText);
            tvEmptyTitle.setTypeface(null, android.graphics.Typeface.BOLD);
            tvEmptyTitle.setGravity(android.view.Gravity.CENTER);
            LinearLayout.LayoutParams etLp = ncMatchW();
            etLp.topMargin = ncDp(12);
            emptyState.addView(tvEmptyTitle, etLp);

            TextView tvEmptyBody = new TextView(this);
            tvEmptyBody.setText("Hãy kết bạn để bắt đầu trò chuyện");
            tvEmptyBody.setTextSize(13);
            tvEmptyBody.setTextColor(cSub);
            tvEmptyBody.setGravity(android.view.Gravity.CENTER);
            LinearLayout.LayoutParams ebLp = ncMatchW();
            ebLp.topMargin = ncDp(6);
            emptyState.addView(tvEmptyBody, ebLp);

            TextView btnFind = new TextView(this);
            btnFind.setText("Tìm bạn bè");
            btnFind.setTextSize(14);
            btnFind.setTextColor(cPink);
            btnFind.setTypeface(null, android.graphics.Typeface.BOLD);
            btnFind.setGravity(android.view.Gravity.CENTER);
            android.graphics.drawable.GradientDrawable findBg = new android.graphics.drawable.GradientDrawable();
            findBg.setColor(0x00000000);
            findBg.setStroke(ncDp(2), cPink);
            findBg.setCornerRadius(ncDp(10));
            btnFind.setBackground(findBg);
            btnFind.setPadding(ncDp(24), ncDp(10), ncDp(24), ncDp(10));
            LinearLayout.LayoutParams findLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            findLp.topMargin = ncDp(16);
            findLp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
            btnFind.setClickable(true);
            btnFind.setFocusable(true);
            btnFind.setOnClickListener(v -> startActivity(
                    new Intent(ChatListActivity.this, SearchActivity.class)));
            emptyState.addView(btnFind, findLp);

            container.addView(emptyState, ncMatchW());
            return;
        }

        boolean first = true;
        boolean anyShown = false;
        for (Map<String, Object> f : friends) {
            String name = f.get("fullName") != null ? f.get("fullName").toString() : "Người dùng";
            long fId = -1;
            try {
                if (f.get("userId") != null) fId = ((Number) f.get("userId")).longValue();
                else if (f.get("id") != null) fId = ((Number) f.get("id")).longValue();
            } catch (Exception ignored) {}

            if (!query.isEmpty() && !name.toLowerCase().contains(query)) continue;
            anyShown = true;

            if (!first) container.addView(ncSep(cSep));
            first = false;

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(ncDp(16), ncDp(13), ncDp(16), ncDp(13));
            row.setClickable(true);
            row.setFocusable(true);
            android.util.TypedValue ripple = new android.util.TypedValue();
            getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true);
            row.setBackgroundResource(ripple.resourceId);

            // Avatar 48dp circle
            FrameLayout avatarFrame = new FrameLayout(this);
            LinearLayout.LayoutParams afLp = new LinearLayout.LayoutParams(ncDp(48), ncDp(48));
            afLp.setMarginEnd(ncDp(14));
            avatarFrame.setLayoutParams(afLp);
            android.graphics.drawable.GradientDrawable avBg = new android.graphics.drawable.GradientDrawable();
            avBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            avBg.setColor(0xFFE8E4DE);
            avatarFrame.setBackground(avBg);
            avatarFrame.setClipToOutline(true);
            ImageView ivAvatar = new ImageView(this);
            ivAvatar.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            ivAvatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
            ivAvatar.setImageResource(R.drawable.ic_user_placeholder);
            avatarFrame.addView(ivAvatar);

            final long finalId = fId;
            if (finalId > 0) {
                String cached = RetrofitClient.getCachedAvatarForUser(finalId);
                String raw = f.get("avatarUrl") != null ? f.get("avatarUrl").toString() : null;
                String url = (cached != null && !cached.isEmpty()) ? cached : raw;
                if (url != null && !url.isEmpty()) {
                    if (url.startsWith("/")) url = RetrofitClient.getBaseUrl() + url.substring(1);
                    com.bumptech.glide.Glide.with(this).load(url)
                            .placeholder(R.drawable.ic_user_placeholder)
                            .circleCrop().into(ivAvatar);
                }
            }
            row.addView(avatarFrame);

            // Name + subtitle column
            LinearLayout nameCol = new LinearLayout(this);
            nameCol.setOrientation(LinearLayout.VERTICAL);
            nameCol.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            TextView tvName = new TextView(this);
            tvName.setText(name);
            tvName.setTextSize(15);
            tvName.setTextColor(cText);
            tvName.setTypeface(null, android.graphics.Typeface.BOLD);
            nameCol.addView(tvName, ncMatchW());
            TextView tvFriendSub = new TextView(this);
            tvFriendSub.setText("Bạn bè");
            tvFriendSub.setTextSize(12);
            tvFriendSub.setTextColor(cSub);
            LinearLayout.LayoutParams fsubLp = ncMatchW();
            fsubLp.topMargin = ncDp(2);
            nameCol.addView(tvFriendSub, fsubLp);
            row.addView(nameCol);

            // Selection indicator circle (24dp)
            FrameLayout selCircle = new FrameLayout(this);
            LinearLayout.LayoutParams siLp = new LinearLayout.LayoutParams(ncDp(24), ncDp(24));
            siLp.setMarginStart(ncDp(8));
            selCircle.setLayoutParams(siLp);

            TextView tvCheck = new TextView(this);
            tvCheck.setText("✓");
            tvCheck.setTextSize(12);
            tvCheck.setTextColor(0xFFFFFFFF);
            tvCheck.setTypeface(null, android.graphics.Typeface.BOLD);
            tvCheck.setGravity(android.view.Gravity.CENTER);
            selCircle.addView(tvCheck, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            row.addView(selCircle);

            final String finalName = name;
            Runnable updateInd = () -> {
                android.graphics.drawable.GradientDrawable ind = new android.graphics.drawable.GradientDrawable();
                ind.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                if (selected.containsKey(finalName)) {
                    ind.setColor(cPink);
                    tvCheck.setVisibility(View.VISIBLE);
                } else {
                    ind.setColor(0x00000000);
                    ind.setStroke(ncDp(2), 0xFFD1D1D6);
                    tvCheck.setVisibility(View.GONE);
                }
                selCircle.setBackground(ind);
            };
            updateInd.run();

            row.setOnClickListener(v -> {
                if (selected.containsKey(finalName)) selected.remove(finalName);
                else selected.put(finalName, finalId);
                updateInd.run();
                if (onSelectionChanged != null) onSelectionChanged.run();
            });

            container.addView(row);
        }

        if (!anyShown && !query.isEmpty()) {
            TextView noResult = new TextView(this);
            noResult.setText("Không tìm thấy \"" + query + "\"");
            noResult.setTextSize(14);
            noResult.setTextColor(cSub);
            noResult.setGravity(android.view.Gravity.CENTER);
            noResult.setPadding(0, ncDp(32), 0, ncDp(32));
            container.addView(noResult, ncMatchW());
        }
    }

    private View ncSep(int color) {
        View v = new View(this);
        v.setBackgroundColor(color);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
        return v;
    }

    private LinearLayout.LayoutParams ncMatchW() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int ncDp(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
