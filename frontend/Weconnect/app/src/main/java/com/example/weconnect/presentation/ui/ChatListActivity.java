package com.example.weconnect.presentation.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.weconnect.R;
import com.example.weconnect.presentation.adapter.ChatRoomAdapter;
import com.example.weconnect.databinding.ActivityChatBinding;
import com.example.weconnect.data.repository.FirebaseManager;
import com.example.weconnect.data.repository.FirestoreChatRepository;
import com.example.weconnect.data.repository.FirestoreFriendRepository;
import com.example.weconnect.domain.model.ChatRoom;
import com.example.weconnect.presentation.viewmodel.ChatViewModel;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ChatListActivity extends AppCompatActivity {

    private static final String TAB_ACTIVITY = "activity";
    private static final String TAB_CONTACT  = "contact";

    private ActivityChatBinding binding;
    private ChatViewModel chatViewModel;
    
    private ChatRoomAdapter adapter;
    private String currentTab = TAB_ACTIVITY;

    private FirestoreChatRepository chatRepo;
    private FirestoreFriendRepository friendRepo;
    private List<ChatRoom> allRooms = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        chatViewModel = new ViewModelProvider(this).get(ChatViewModel.class);

        chatRepo   = new FirestoreChatRepository();
        friendRepo = new FirestoreFriendRepository();

        initViews();
        setupRecyclerView();
        setupTabs();
        setupClickListeners();
        setupSearch();
        setupObservers();
        applyIncomingContext();
        loadRoomsFromFirestore();
    }

    @Override protected void onResume() { super.onResume(); loadRoomsFromFirestore(); }

    private void loadRoomsFromFirestore() {
        String uid = FirebaseManager.getCurrentUserId();
        if (uid != null) {
            chatViewModel.loadChatRooms(uid);
        }
    }

    private void setupObservers() {
        chatViewModel.chatRooms.observe(this, rooms -> {
            allRooms.clear();
            if (rooms != null) {
                allRooms.addAll(rooms);
            }
            filterAndDisplay();
        });

        chatViewModel.error.observe(this, err -> {
            Toast.makeText(ChatListActivity.this, "Lỗi: " + err, Toast.LENGTH_SHORT).show();
        });
    }

    private void filterAndDisplay() {
        String query = binding.etChatSearch.getText() != null ?
            binding.etChatSearch.getText().toString().trim().toLowerCase() : "";

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
    }

    private void setupRecyclerView() {
        adapter = new ChatRoomAdapter(this::openRoom);
        binding.rvChats.setLayoutManager(new LinearLayoutManager(this));
        binding.rvChats.setAdapter(adapter);
    }

    private void setupTabs() {
        binding.tabChatType.addTab(binding.tabChatType.newTab().setText("Hoạt động"));
        binding.tabChatType.addTab(binding.tabChatType.newTab().setText("Liên hệ"));
        binding.tabChatType.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                currentTab = tab != null && tab.getPosition() == 1 ? TAB_CONTACT : TAB_ACTIVITY;
                filterAndDisplay();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void setupClickListeners() {
        binding.btnHome.setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            finish();
        });
        binding.btnNotifications.setOnClickListener(v -> {
            startActivity(new Intent(this, NotificationsActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            finish();
        });
        binding.btnProfile.setOnClickListener(v -> {
            Intent intent = new Intent(this, UserProfileActivity.class);
            intent.putExtra("user_uid", FirebaseManager.getCurrentUserId());
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });
    }

    private void setupSearch() {
        binding.etChatSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { filterAndDisplay(); }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void applyIncomingContext() {
        String tag = getIntent().getStringExtra("highlight_tag");
        if (tag != null && !tag.isEmpty()) {
            currentTab = TAB_ACTIVITY;
            TabLayout.Tab t = binding.tabChatType.getTabAt(0);
            if (t != null) t.select();
            binding.etChatSearch.setText(tag);
            binding.etChatSearch.setSelection(tag.length());
        }
    }
}
