package com.example.weconnect.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.weconnect.R;
import com.example.weconnect.adapters.ChatRoomAdapter;
import com.example.weconnect.api.FirebaseManager;
import com.example.weconnect.api.FirestoreChatRepository;
import com.example.weconnect.api.FirestoreFriendRepository;
import com.example.weconnect.models.ChatRoom;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ChatListActivity extends AppCompatActivity {

    private static final String TAB_ACTIVITY = "activity";
    private static final String TAB_CONTACT  = "contact";

    private EditText etChatSearch;
    private RecyclerView rvChats;
    private FrameLayout btnHome, btnMessages, btnNotifications, btnProfile;
    private TabLayout tabChatType;
    private ChatRoomAdapter adapter;
    private String currentTab = TAB_ACTIVITY;

    private FirestoreChatRepository chatRepo;
    private FirestoreFriendRepository friendRepo;
    private List<ChatRoom> allRooms = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        chatRepo   = new FirestoreChatRepository();
        friendRepo = new FirestoreFriendRepository();

        initViews();
        setupRecyclerView();
        setupTabs();
        setupClickListeners();
        setupSearch();
        applyIncomingContext();
        loadRoomsFromFirestore();
    }

    @Override protected void onResume() { super.onResume(); loadRoomsFromFirestore(); }

    private void loadRoomsFromFirestore() {
        String uid = FirebaseManager.getCurrentUserId();
        if (uid == null) return;

        chatRepo.getUserRooms(uid, new FirestoreChatRepository.RoomsCallback() {
            @Override public void onSuccess(List<Map<String, Object>> rooms) {
                allRooms.clear();
                for (Map<String, Object> r : rooms) {
                    ChatRoom room = mapToRoom(r, uid);
                    if (room != null) allRooms.add(room);
                }
                runOnUiThread(() -> filterAndDisplay());
            }
            @Override public void onError(String err) {
                runOnUiThread(() ->
                    Toast.makeText(ChatListActivity.this, "Không thể tải chat: " + err, Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    private ChatRoom mapToRoom(Map<String, Object> r, String myUid) {
        try {
            String id    = (String) r.get("id");
            String title = (String) r.get("title");
            String type  = (String) r.get("type");
            boolean active = Boolean.TRUE.equals(r.get("active"));
            List<String> memberIds = (List<String>) r.get("memberIds");
            if (title == null) title = "Phòng chat";
            if (type == null)  type  = "activity";

            return new ChatRoom(id, title, null, null, type,
                R.drawable.ic_user_placeholder, active, "",
                new ArrayList<>(), "", memberIds != null ? new ArrayList<>(memberIds) : new ArrayList<>(),
                new ArrayList<>());
        } catch (Exception e) { return null; }
    }

    private void filterAndDisplay() {
        String query = etChatSearch.getText() != null ?
            etChatSearch.getText().toString().trim().toLowerCase() : "";

        List<ChatRoom> filtered = new ArrayList<>();
        for (ChatRoom room : allRooms) {
            boolean matchTab;
            if (TAB_ACTIVITY.equals(currentTab)) {
                matchTab = ChatRoom.TYPE_ACTIVITY.equals(room.getType()) || ChatRoom.TYPE_GROUP.equals(room.getType());
            } else {
                matchTab = ChatRoom.TYPE_DIRECT.equals(room.getType()) || ChatRoom.TYPE_FRIEND_GROUP.equals(room.getType());
            }
            if (!matchTab) continue;
            if (!query.isEmpty()) {
                String t = room.getTitle() != null ? room.getTitle().toLowerCase() : "";
                if (!t.contains(query)) continue;
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

        TextView header = new TextView(this);
        header.setText("Tạo tin nhắn mới");
        header.setTextSize(20);
        header.setTextColor(getResources().getColor(R.color.primary_pink, null));
        header.setTypeface(null, android.graphics.Typeface.BOLD);
        header.setGravity(android.view.Gravity.CENTER);
        header.setPadding(0, 48, 0, 24);
        root.addView(header);

        EditText search = new EditText(this);
        search.setHint("🔍 Tìm bạn bè...");
        search.setTextSize(15);
        search.setTextColor(getResources().getColor(R.color.text_primary, null));
        search.setHintTextColor(getResources().getColor(R.color.text_secondary, null));
        search.setBackground(null);
        search.setPadding(64, 32, 64, 32);
        search.setSingleLine(true);
        root.addView(search);

        View div = new View(this);
        div.setBackgroundColor(0xFFE8E4DE);
        div.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2));
        root.addView(div);

        TextView tvSelected = new TextView(this);
        tvSelected.setText("Chọn bạn bè để tạo nhóm chat");
        tvSelected.setTextSize(14);
        tvSelected.setTextColor(getResources().getColor(R.color.text_secondary, null));
        tvSelected.setPadding(64, 28, 64, 12);
        root.addView(tvSelected);

        LinearLayout friendListContainer = new LinearLayout(this);
        friendListContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(friendListContainer);

        // Map: friendName → uid
        java.util.Map<String, String> selectedFriends = new java.util.LinkedHashMap<>();

        TextView tvLoading = new TextView(this);
        tvLoading.setText("Đang tải...");
        tvLoading.setGravity(android.view.Gravity.CENTER);
        tvLoading.setPadding(0, 48, 0, 48);
        friendListContainer.addView(tvLoading);

        String uid = FirebaseManager.getCurrentUserId();
        if (uid == null) return;

        friendRepo.getFriends(uid, new FirestoreFriendRepository.FriendsCallback() {
            @Override public void onSuccess(List<Map<String, Object>> friends) {
                // Lấy UID của bạn bè (user phía còn lại)
                List<Map<String, Object>> enriched = new ArrayList<>();
                for (Map<String, Object> f : friends) {
                    String u1 = (String) f.get("user1Id");
                    String u2 = (String) f.get("user2Id");
                    String friendUid = uid.equals(u1) ? u2 : u1;
                    // Load tên từ Firestore
                    FirebaseManager.getFirestore().collection("users").document(friendUid).get()
                        .addOnSuccessListener(doc -> {
                            if (doc.exists()) {
                                Map<String, Object> e = new java.util.HashMap<>();
                                e.put("uid", friendUid);
                                e.put("fullName", doc.getString("fullName"));
                                enriched.add(e);
                                runOnUiThread(() -> buildFriendRows(
                                    friendListContainer, enriched, selectedFriends, tvSelected, search, sheet
                                ));
                            }
                        });
                }
                if (friends.isEmpty()) {
                    runOnUiThread(() -> {
                        friendListContainer.removeAllViews();
                        TextView noFriend = new TextView(ChatListActivity.this);
                        noFriend.setText("Bạn chưa có bạn bè nào");
                        noFriend.setGravity(android.view.Gravity.CENTER);
                        noFriend.setPadding(0, 48, 0, 48);
                        friendListContainer.addView(noFriend);
                    });
                }
            }
            @Override public void onError(String err) {}
        });

        View div2 = new View(this);
        div2.setBackgroundColor(0xFFE8E4DE);
        div2.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2));
        root.addView(div2);

        com.google.android.material.button.MaterialButton btnCreate =
            new com.google.android.material.button.MaterialButton(this);
        btnCreate.setText("Tạo nhóm chat");
        btnCreate.setAllCaps(false);
        btnCreate.setCornerRadius(72);
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
            sheet.dismiss();
            String myUid = FirebaseManager.getCurrentUserId();
            String myName = FirebaseManager.getUserName(this);

            if (selectedFriends.size() == 1) {
                // DM
                Map.Entry<String, String> entry = selectedFriends.entrySet().iterator().next();
                chatRepo.getOrCreateDirectRoom(myUid, myName, entry.getValue(), entry.getKey(),
                    new FirestoreChatRepository.ActionCallback() {
                        @Override public void onSuccess(String roomId) {
                            Intent intent = new Intent(ChatListActivity.this, ConversationActivity.class);
                            intent.putExtra("room_id", roomId);
                            startActivity(intent);
                        }
                        @Override public void onError(String err) {
                            Toast.makeText(ChatListActivity.this, "Lỗi: " + err, Toast.LENGTH_SHORT).show();
                        }
                    });
            } else {
                // Group
                List<String> memberUids = new ArrayList<>(selectedFriends.values());
                String title = String.join(", ", selectedFriends.keySet());
                chatRepo.createGroupRoom(myUid, title, memberUids,
                    new FirestoreChatRepository.ActionCallback() {
                        @Override public void onSuccess(String roomId) {
                            Intent intent = new Intent(ChatListActivity.this, ConversationActivity.class);
                            intent.putExtra("room_id", roomId);
                            startActivity(intent);
                            loadRoomsFromFirestore();
                        }
                        @Override public void onError(String err) {
                            Toast.makeText(ChatListActivity.this, "Lỗi: " + err, Toast.LENGTH_SHORT).show();
                        }
                    });
            }
        });
        root.addView(btnCreate);

        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(root);
        sheet.setContentView(sv);
        sheet.show();
    }

    private void buildFriendRows(LinearLayout container, List<Map<String, Object>> friends,
                                  java.util.Map<String, String> selected,
                                  TextView tvSelected, EditText search,
                                  com.google.android.material.bottomsheet.BottomSheetDialog sheet) {
        container.removeAllViews();
        String q = search.getText().toString().trim().toLowerCase();
        for (Map<String, Object> f : friends) {
            String name = f.get("fullName") != null ? f.get("fullName").toString() : "Người dùng";
            String fUid = f.get("uid") != null ? f.get("uid").toString() : "";
            if (!q.isEmpty() && !name.toLowerCase().contains(q)) continue;

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(64, 24, 64, 24);
            row.setBackgroundResource(android.R.drawable.list_selector_background);
            row.setClickable(true);

            android.widget.CheckBox cb = new android.widget.CheckBox(this);
            cb.setChecked(selected.containsKey(name));
            cb.setButtonTintList(android.content.res.ColorStateList.valueOf(
                getResources().getColor(R.color.primary_pink, null)));

            ImageView avatar = new ImageView(this);
            avatar.setImageResource(R.drawable.ic_user_placeholder);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(88, 88);
            lp.setMargins(24, 0, 0, 0);
            avatar.setLayoutParams(lp);

            TextView tv = new TextView(this);
            tv.setText(name);
            tv.setTextSize(15);
            tv.setTextColor(getResources().getColor(R.color.text_primary, null));
            tv.setPadding(24, 0, 0, 0);

            row.addView(cb); row.addView(avatar); row.addView(tv);

            View.OnClickListener toggle = vv -> {
                if (selected.containsKey(name)) { selected.remove(name); cb.setChecked(false); }
                else { selected.put(name, fUid); cb.setChecked(true); }
                tvSelected.setText(selected.isEmpty() ? "Chọn bạn bè để tạo nhóm chat"
                    : "Đã chọn: " + selected.size() + " người");
            };
            row.setOnClickListener(toggle);
            cb.setOnClickListener(toggle);
            container.addView(row);
        }
        if (container.getChildCount() == 0) {
            TextView empty = new TextView(this);
            empty.setText("Không tìm thấy bạn bè");
            empty.setGravity(android.view.Gravity.CENTER);
            empty.setPadding(0, 48, 0, 48);
            container.addView(empty);
        }
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                buildFriendRows(container, friends, selected, tvSelected, search, sheet);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void initViews() {
        ImageView ivNewChat = findViewById(R.id.ivNewChat);
        ivNewChat.setOnClickListener(v -> showNewChatDialog());
        etChatSearch     = findViewById(R.id.etChatSearch);
        rvChats          = findViewById(R.id.rvChats);
        btnHome          = findViewById(R.id.btnHome);
        btnMessages      = findViewById(R.id.btnMessages);
        btnNotifications = findViewById(R.id.btnNotifications);
        btnProfile       = findViewById(R.id.btnProfile);
        tabChatType      = findViewById(R.id.tabChatType);
    }

    private void setupRecyclerView() {
        adapter = new ChatRoomAdapter(this::openRoom);
        rvChats.setLayoutManager(new LinearLayoutManager(this));
        rvChats.setAdapter(adapter);
    }

    private void setupTabs() {
        tabChatType.addTab(tabChatType.newTab().setText("Hoạt động"));
        tabChatType.addTab(tabChatType.newTab().setText("Liên hệ"));
        tabChatType.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                currentTab = tab != null && tab.getPosition() == 1 ? TAB_CONTACT : TAB_ACTIVITY;
                filterAndDisplay();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void setupClickListeners() {
        btnHome.setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            finish();
        });
        btnNotifications.setOnClickListener(v -> {
            startActivity(new Intent(this, NotificationsActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            finish();
        });
        btnProfile.setOnClickListener(v -> {
            Intent intent = new Intent(this, UserProfileActivity.class);
            intent.putExtra("user_uid", FirebaseManager.getCurrentUserId());
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });
    }

    private void setupSearch() {
        etChatSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { filterAndDisplay(); }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void applyIncomingContext() {
        String tag = getIntent().getStringExtra("highlight_tag");
        if (tag != null && !tag.isEmpty()) {
            currentTab = TAB_ACTIVITY;
            TabLayout.Tab t = tabChatType.getTabAt(0);
            if (t != null) t.select();
            etChatSearch.setText(tag);
            etChatSearch.setSelection(tag.length());
        }
    }
}
