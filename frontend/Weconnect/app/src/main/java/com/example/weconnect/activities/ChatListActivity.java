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
import com.example.weconnect.api.ChatApiService;
import com.example.weconnect.api.RetrofitClient;
import com.example.weconnect.models.ApiResponse;
import com.example.weconnect.models.ChatMessage;
import com.example.weconnect.models.ChatRoom;
import com.example.weconnect.models.ChatRoomApiResponse;
import com.example.weconnect.websocket.WebSocketManager;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
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

    // Cached rooms from API
    private List<ChatRoom> allRooms = new ArrayList<>();

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
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        btnNotifications.setOnClickListener(v -> {
            Intent intent = new Intent(this, NotificationsActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        btnProfile.setOnClickListener(v -> {
            Intent intent = new Intent(this, UserProfileActivity.class);
            intent.putExtra("username", RetrofitClient.getUserName(this));
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
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

            // Filter by search query
            if (!query.isEmpty()) {
                String title = room.getTitle() != null ? room.getTitle().toLowerCase() : "";
                String preview = room.getLastMessagePreview() != null
                        ? room.getLastMessagePreview().toLowerCase() : "";
                if (!title.contains(query) && !preview.contains(query)) continue;
            }

            filtered.add(room);
        }

        adapter.submitList(filtered);
    }

    private void openRoom(ChatRoom room) {
        Intent intent = new Intent(this, ConversationActivity.class);
        intent.putExtra("room_id", room.getId());
        startActivity(intent);
    }

    private void showNewChatDialog() {
        com.google.android.material.bottomsheet.BottomSheetDialog sheet =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getResources().getColor(R.color.card_surface, null));
        root.setPadding(0, 0, 0, 48);

        // Header: "Tạo tin nhắn mới"
        TextView header = new TextView(this);
        header.setText("Tạo tin nhắn mới");
        header.setTextSize(20);
        header.setTextColor(getResources().getColor(R.color.primary_pink, null));
        header.setTypeface(null, android.graphics.Typeface.BOLD);
        header.setGravity(android.view.Gravity.CENTER);
        header.setPadding(0, 48, 0, 24);
        root.addView(header);

        // Search bar
        EditText search = new EditText(this);
        search.setHint("🔍 Tìm bạn bè...");
        search.setTextSize(15);
        search.setTextColor(getResources().getColor(R.color.text_primary, null));
        search.setHintTextColor(getResources().getColor(R.color.text_secondary, null));
        search.setBackground(null);
        search.setPadding(64, 32, 64, 32);
        search.setSingleLine(true);
        search.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        search.setFocusable(true);
        search.setFocusableInTouchMode(true);
        root.addView(search);

        // Divider
        View div1 = new View(this);
        div1.setBackgroundColor(0xFFE8E4DE);
        div1.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 2));
        root.addView(div1);

        // Selected friends label
        TextView tvSelected = new TextView(this);
        tvSelected.setText("Chọn bạn bè để tạo nhóm chat");
        tvSelected.setTextSize(14);
        tvSelected.setTextColor(getResources().getColor(R.color.text_secondary, null));
        tvSelected.setPadding(64, 28, 64, 12);
        root.addView(tvSelected);

        // Friend list container
        LinearLayout friendListContainer = new LinearLayout(this);
        friendListContainer.setOrientation(LinearLayout.VERTICAL);

        // Load friends from backend
        java.util.Map<String, Long> selectedFriends = new java.util.LinkedHashMap<>();

        // Loading indicator
        TextView tvLoading = new TextView(this);
        tvLoading.setText("Đang tải danh sách bạn bè...");
        tvLoading.setTextSize(14);
        tvLoading.setTextColor(getResources().getColor(R.color.text_secondary, null));
        tvLoading.setGravity(android.view.Gravity.CENTER);
        tvLoading.setPadding(0, 48, 0, 48);
        friendListContainer.addView(tvLoading);
        root.addView(friendListContainer);

        // Load friends from API
        RetrofitClient.loadToken(this);
        com.example.weconnect.api.FriendApiService friendApi =
                RetrofitClient.getClient().create(com.example.weconnect.api.FriendApiService.class);

        friendApi.getFriends().enqueue(new Callback<ApiResponse<List<Map<String, Object>>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<Map<String, Object>>>> call,
                                   Response<ApiResponse<List<Map<String, Object>>>> response) {
                friendListContainer.removeAllViews();
                List<Map<String, Object>> friendsList = new ArrayList<>();
                if (response.isSuccessful() && response.body() != null
                        && response.body().getResult() != null) {
                    friendsList.addAll(response.body().getResult());
                }
                buildFriendRows(friendListContainer, friendsList, selectedFriends,
                        tvSelected, search, sheet);
            }

            @Override
            public void onFailure(Call<ApiResponse<List<Map<String, Object>>>> call, Throwable t) {
                friendListContainer.removeAllViews();
                TextView noResult = new TextView(ChatListActivity.this);
                noResult.setText("Lỗi kết nối. Vui lòng thử lại.");
                noResult.setTextSize(14);
                noResult.setTextColor(getResources().getColor(R.color.text_secondary, null));
                noResult.setGravity(android.view.Gravity.CENTER);
                noResult.setPadding(0, 48, 0, 48);
                friendListContainer.addView(noResult);
            }
        });

        // Divider
        View div2 = new View(this);
        div2.setBackgroundColor(0xFFE8E4DE);
        div2.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 2));
        root.addView(div2);

        // Create group button
        com.google.android.material.button.MaterialButton btnCreate =
                new com.google.android.material.button.MaterialButton(this);
        btnCreate.setText("Tạo nhóm chat");
        btnCreate.setAllCaps(false);
        btnCreate.setCornerRadius(72);
        btnCreate.setTextSize(16);
        btnCreate.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                getResources().getColor(R.color.primary_pink, null)));
        LinearLayout.LayoutParams btnP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnP.setMargins(48, 24, 48, 0);
        btnCreate.setLayoutParams(btnP);
        btnCreate.setOnClickListener(v -> {
            if (selectedFriends.isEmpty()) {
                Toast.makeText(this, "Vui lòng chọn ít nhất 1 người bạn", Toast.LENGTH_SHORT).show();
                return;
            }

            ChatApiService chatApi = RetrofitClient.getClient().create(ChatApiService.class);

            if (selectedFriends.size() == 1) {
                // Direct message — gọi API tạo/lấy phòng DM
                java.util.Map.Entry<String, Long> entry = selectedFriends.entrySet().iterator().next();
                long friendId = entry.getValue();
                sheet.dismiss();

                chatApi.getDirectRoom(friendId).enqueue(new Callback<ApiResponse<ChatRoomApiResponse>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<ChatRoomApiResponse>> call,
                                           Response<ApiResponse<ChatRoomApiResponse>> response) {
                        if (response.isSuccessful() && response.body() != null
                                && response.body().getResult() != null) {
                            ChatRoomApiResponse roomData = response.body().getResult();
                            String roomId = String.valueOf(roomData.getId());
                            Intent intent = new Intent(ChatListActivity.this, ConversationActivity.class);
                            intent.putExtra("room_id", roomId);
                            startActivity(intent);
                        } else {
                            Toast.makeText(ChatListActivity.this,
                                    "Không thể tạo phòng chat", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<ChatRoomApiResponse>> call, Throwable t) {
                        Toast.makeText(ChatListActivity.this,
                                "Lỗi kết nối", Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
                // Group chat — gọi API tạo phòng nhóm
                List<Long> memberIds = new ArrayList<>(selectedFriends.values());
                String groupTitle = String.join(", ", selectedFriends.keySet());
                sheet.dismiss();

                Map<String, Object> body = new java.util.HashMap<>();
                body.put("title", groupTitle);
                body.put("memberIds", memberIds);

                chatApi.createGroupRoom(body).enqueue(new Callback<ApiResponse<ChatRoomApiResponse>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<ChatRoomApiResponse>> call,
                                           Response<ApiResponse<ChatRoomApiResponse>> response) {
                        if (response.isSuccessful() && response.body() != null
                                && response.body().getResult() != null) {
                            ChatRoomApiResponse roomData = response.body().getResult();
                            String roomId = String.valueOf(roomData.getId());
                            Intent intent = new Intent(ChatListActivity.this, ConversationActivity.class);
                            intent.putExtra("room_id", roomId);
                            startActivity(intent);
                            loadChatsFromApi();
                        } else {
                            Toast.makeText(ChatListActivity.this,
                                    "Không thể tạo phòng chat", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<ChatRoomApiResponse>> call, Throwable t) {
                        Toast.makeText(ChatListActivity.this,
                                "Lỗi kết nối", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
        root.addView(btnCreate);

        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        scrollView.addView(root);
        sheet.setContentView(scrollView);
        sheet.show();

        // Auto-show keyboard on search field
        search.requestFocus();
        search.postDelayed(() -> {
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(search, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            }
        }, 300);
    }

    private void buildFriendRows(LinearLayout container,
                                  List<Map<String, Object>> friends,
                                  java.util.Map<String, Long> selectedFriends,
                                  TextView tvSelected,
                                  EditText search,
                                  com.google.android.material.bottomsheet.BottomSheetDialog sheet) {
        container.removeAllViews();
        String queryText = search.getText().toString().trim().toLowerCase();

        for (Map<String, Object> friendData : friends) {
            String friendName = friendData.get("fullName") != null
                    ? friendData.get("fullName").toString() : "Người dùng";
            long friendId = -1;
            try {
                if (friendData.get("userId") != null)
                    friendId = ((Number) friendData.get("userId")).longValue();
                else if (friendData.get("id") != null)
                    friendId = ((Number) friendData.get("id")).longValue();
            } catch (Exception ignored) {}

            if (!queryText.isEmpty() && !friendName.toLowerCase().contains(queryText)) {
                continue;
            }

            LinearLayout friendRow = new LinearLayout(this);
            friendRow.setOrientation(LinearLayout.HORIZONTAL);
            friendRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
            friendRow.setPadding(64, 24, 64, 24);
            friendRow.setBackgroundResource(android.R.drawable.list_selector_background);
            friendRow.setClickable(true);

            // Checkbox
            android.widget.CheckBox checkBox = new android.widget.CheckBox(this);
            checkBox.setChecked(selectedFriends.containsKey(friendName));
            checkBox.setButtonTintList(android.content.res.ColorStateList.valueOf(
                    getResources().getColor(R.color.primary_pink, null)));

            // Avatar
            ImageView avatar = new ImageView(this);
            avatar.setImageResource(R.drawable.ic_user_placeholder);
            LinearLayout.LayoutParams avatarLp = new LinearLayout.LayoutParams(88, 88);
            avatarLp.setMargins(24, 0, 0, 0);
            avatar.setLayoutParams(avatarLp);
            avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);

            // Name
            TextView name = new TextView(this);
            name.setText(friendName);
            name.setTextSize(15);
            name.setTextColor(getResources().getColor(R.color.text_primary, null));
            name.setPadding(24, 0, 0, 0);

            friendRow.addView(checkBox);
            friendRow.addView(avatar);
            friendRow.addView(name);

            final long fId = friendId;
            View.OnClickListener toggleFriend = v -> {
                if (selectedFriends.containsKey(friendName)) {
                    selectedFriends.remove(friendName);
                    checkBox.setChecked(false);
                } else {
                    selectedFriends.put(friendName, fId);
                    checkBox.setChecked(true);
                }
                if (selectedFriends.isEmpty()) {
                    tvSelected.setText("Chọn bạn bè để tạo nhóm chat");
                } else {
                    tvSelected.setText("Đã chọn: " + selectedFriends.size() + " người");
                }
            };

            friendRow.setOnClickListener(toggleFriend);
            checkBox.setOnClickListener(toggleFriend);

            container.addView(friendRow);
        }

        if (container.getChildCount() == 0) {
            TextView noResult = new TextView(this);
            noResult.setText("Không tìm thấy bạn bè");
            noResult.setTextSize(14);
            noResult.setTextColor(getResources().getColor(R.color.text_secondary, null));
            noResult.setGravity(android.view.Gravity.CENTER);
            noResult.setPadding(0, 48, 0, 48);
            container.addView(noResult);
        }

        // Search filter
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                buildFriendRows(container, friends, selectedFriends, tvSelected, search, sheet);
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });
    }
}
