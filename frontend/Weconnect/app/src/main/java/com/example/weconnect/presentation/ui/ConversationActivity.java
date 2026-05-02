package com.example.weconnect.presentation.ui;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.weconnect.R;
import com.example.weconnect.presentation.adapter.MessageAdapter;
import com.example.weconnect.databinding.ActivityConversationBinding;
import com.example.weconnect.data.repository.FirebaseManager;
import com.example.weconnect.data.repository.FirestoreChatRepository;
import com.example.weconnect.domain.model.ChatMessage;
import com.example.weconnect.presentation.viewmodel.ChatViewModel;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ConversationActivity extends AppCompatActivity {

    private ActivityConversationBinding binding;
    private ChatViewModel chatViewModel;

    private MessageAdapter adapter;

    private String roomId;
    private String currentUid;
    private String currentName;
    private Map<String, Object> currentRoom;

    private FirestoreChatRepository chatRepo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityConversationBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        chatViewModel = new ViewModelProvider(this).get(ChatViewModel.class);
        chatRepo    = new FirestoreChatRepository();

        currentUid  = FirebaseManager.getCurrentUserId();
        currentName = FirebaseManager.getUserName(this);

        setupRecyclerView();
        setupClickListeners();
        setupObservers();
        bindRoom();
    }

    private void setupRecyclerView() {
        adapter = new MessageAdapter();
        binding.rvMessages.setLayoutManager(new LinearLayoutManager(this));
        binding.rvMessages.setAdapter(adapter);
    }

    private void setupClickListeners() {
        binding.ivBackConversation.setOnClickListener(v -> finish());
        binding.btnSendMessage.setOnClickListener(v -> sendMessage());
        binding.ivChatSettings.setOnClickListener(v -> showMemberManagementDialog());
    }

    private void setupObservers() {
        chatViewModel.messages.observe(this, newMsgs -> {
            if (newMsgs != null && !newMsgs.isEmpty()) {
                List<ChatMessage> current = adapter.getCurrentList();
                List<ChatMessage> combined = new ArrayList<>(current);
                
                // Add only messages that are not already in the list
                for (ChatMessage m : newMsgs) {
                    boolean exists = false;
                    for (ChatMessage c : combined) {
                        if (c.getId() != null && c.getId().equals(m.getId())) {
                            exists = true;
                            break;
                        }
                    }
                    if (!exists) {
                        combined.add(m);
                    }
                }
                
                adapter.submitList(combined, () ->
                    binding.rvMessages.scrollToPosition(combined.size() - 1)
                );
            }
        });

        chatViewModel.error.observe(this, err -> {
            if (err != null && !err.isEmpty()) {
                Toast.makeText(this, "Lỗi: " + err, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void bindRoom() {
        roomId = getIntent().getStringExtra("room_id");
        if (roomId == null) {
            Toast.makeText(this, "Không tìm thấy phòng chat.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Load room info
        chatRepo.getRoom(roomId, new FirestoreChatRepository.RoomCallback() {
            @Override public void onSuccess(Map<String, Object> room) {
                currentRoom = room;
                runOnUiThread(() -> {
                    displayRoom(room);
                    chatViewModel.listenToMessages(roomId, currentUid);
                });
            }
            @Override public void onError(String err) {
                runOnUiThread(() -> {
                    Toast.makeText(ConversationActivity.this, "Không tìm thấy phòng chat.", Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        });
    }

    private void displayRoom(Map<String, Object> room) {
        String title = (String) room.get("title");
        String type  = (String) room.get("type");
        boolean active = Boolean.TRUE.equals(room.get("active"));

        binding.tvConversationTitle.setText(title != null ? title : "Phòng chat");

        String typeLabel = "activity".equals(type) ? "Hoạt động" :
                           "direct".equals(type) ? "Nhắn tin riêng" : "Nhóm chat";
        binding.tvConversationType.setText(typeLabel);

        if (!active) {
            binding.viewOnlineDot.setVisibility(View.GONE);
            binding.tvConversationStatus.setText("Đã kết thúc");
            binding.tvConversationStatus.setTextColor(0xFF9E8E82);
        } else {
            binding.viewOnlineDot.setVisibility(View.VISIBLE);
            binding.tvConversationStatus.setText("Đang hoạt động");
            binding.tvConversationStatus.setTextColor(0xFF66BB6A);
        }

        // Ẩn settings button với DM
        binding.ivChatSettings.setVisibility("direct".equals(type) ? View.GONE : View.VISIBLE);
    }

    private void sendMessage() {
        if (roomId == null) return;
        String content = binding.etMessageInput.getText() != null ?
            binding.etMessageInput.getText().toString().trim() : "";
        if (TextUtils.isEmpty(content)) return;

        binding.etMessageInput.setText("");

        chatViewModel.sendMessage(roomId, currentUid, currentName, content);
    }

    private void showMemberManagementDialog() {
        if (currentRoom == null) return;

        BottomSheetDialog sheet = new BottomSheetDialog(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getResources().getColor(R.color.card_surface, null));
        root.setPadding(0, 0, 0, 48);

        TextView header = new TextView(this);
        header.setText("Quản lý phòng chat");
        header.setTextSize(20);
        header.setTextColor(getResources().getColor(R.color.primary_pink, null));
        header.setTypeface(null, android.graphics.Typeface.BOLD);
        header.setGravity(Gravity.CENTER);
        header.setPadding(0, 48, 0, 12);
        root.addView(header);

        // Members list
        List<String> memberIds = (List<String>) currentRoom.get("memberIds");
        if (memberIds != null) {
            TextView membersLabel = new TextView(this);
            membersLabel.setText("👥 Thành viên (" + memberIds.size() + ")");
            membersLabel.setTextSize(15);
            membersLabel.setTypeface(null, android.graphics.Typeface.BOLD);
            membersLabel.setTextColor(getResources().getColor(R.color.text_primary, null));
            membersLabel.setPadding(64, 28, 64, 12);
            root.addView(membersLabel);

            for (String memberId : memberIds) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(64, 20, 64, 20);

                ImageView av = new ImageView(this);
                av.setImageResource(R.drawable.ic_user_placeholder);
                av.setLayoutParams(new LinearLayout.LayoutParams(72, 72));
                row.addView(av);

                TextView tv = new TextView(this);
                tv.setText(memberId.equals(currentUid) ? "Bạn" : memberId.substring(0, Math.min(8, memberId.length())) + "...");
                tv.setTextSize(14);
                tv.setTextColor(getResources().getColor(memberId.equals(currentUid) ?
                    R.color.primary_pink : R.color.text_primary, null));
                tv.setPadding(24, 0, 0, 0);
                row.addView(tv);

                root.addView(row);
            }
        }

        // Leave room button
        View divider = new View(this);
        divider.setBackgroundColor(0xFFE8E4DE);
        divider.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2));
        root.addView(divider);

        MaterialButton btnLeave = new MaterialButton(this);
        btnLeave.setText("Rời khỏi phòng");
        btnLeave.setAllCaps(false);
        btnLeave.setCornerRadius(72);
        btnLeave.setBackgroundTintList(ColorStateList.valueOf(0xFFFF4D6D));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(48, 24, 48, 0);
        btnLeave.setLayoutParams(lp);
        btnLeave.setOnClickListener(v -> {
            sheet.dismiss();
            chatRepo.leaveRoom(roomId, currentUid, new FirestoreChatRepository.ActionCallback() {
                @Override public void onSuccess(String id) {
                    runOnUiThread(() -> {
                        Toast.makeText(ConversationActivity.this, "Đã rời khỏi phòng chat", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                }
                @Override public void onError(String err) {
                    runOnUiThread(() ->
                        Toast.makeText(ConversationActivity.this, "Lỗi: " + err, Toast.LENGTH_SHORT).show()
                    );
                }
            });
        });
        root.addView(btnLeave);

        ScrollView sv = new ScrollView(this);
        sv.addView(root);
        sheet.setContentView(sv);
        sheet.show();
    }
}
