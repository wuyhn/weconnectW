package com.example.weconnect.activities;

import android.content.res.ColorStateList;
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
import com.example.weconnect.adapters.MessageAdapter;
import com.example.weconnect.api.FirebaseManager;
import com.example.weconnect.api.FirestoreChatRepository;
import com.example.weconnect.models.ChatMessage;
import com.example.weconnect.models.ChatRoom;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ConversationActivity — REAL-TIME chat với Firestore Snapshot Listener.
 *
 * ĐIỂM CẢI TIẾN LỚN SO VỚI CŨ:
 * - Cũ: HTTP Polling cứ 3 giây gọi 1 lần (chậm, tốn pin)
 * - Mới: Firestore addSnapshotListener → tin nhắn mới NGAY LẬP TỨC
 */
public class ConversationActivity extends AppCompatActivity {

    private ImageView ivBackConversation, ivConversationAvatar, ivChatSettings;
    private TextView tvConversationTitle, tvConversationType, tvConversationStatus;
    private View viewOnlineDot;
    private RecyclerView rvMessages;
    private EditText etMessageInput;
    private MaterialButton btnSendMessage;
    private MessageAdapter adapter;

    private String roomId;
    private String currentUid;
    private String currentName;
    private Map<String, Object> currentRoom;

    private FirestoreChatRepository chatRepo;
    private ListenerRegistration messageListener; // Realtime listener

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversation);

        chatRepo    = new FirestoreChatRepository();
        currentUid  = FirebaseManager.getCurrentUserId();
        currentName = FirebaseManager.getUserName(this);

        initViews();
        setupRecyclerView();
        setupClickListeners();
        bindRoom();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // ⚡ Hủy realtime listener khi Activity bị destroy — tránh memory leak
        if (messageListener != null) {
            messageListener.remove();
            messageListener = null;
        }
    }

    private void initViews() {
        ivBackConversation  = findViewById(R.id.ivBackConversation);
        ivConversationAvatar = findViewById(R.id.ivConversationAvatar);
        ivChatSettings       = findViewById(R.id.ivChatSettings);
        tvConversationTitle  = findViewById(R.id.tvConversationTitle);
        tvConversationType   = findViewById(R.id.tvConversationType);
        tvConversationStatus = findViewById(R.id.tvConversationStatus);
        viewOnlineDot        = findViewById(R.id.viewOnlineDot);
        rvMessages           = findViewById(R.id.rvMessages);
        etMessageInput       = findViewById(R.id.etMessageInput);
        btnSendMessage       = findViewById(R.id.btnSendMessage);
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
                    startRealtimeMessageListener(); // ⚡ Bắt đầu lắng nghe realtime
                });
            }
            @Override public void onError(String err) {
                Toast.makeText(ConversationActivity.this, "Không tìm thấy phòng chat.", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void displayRoom(Map<String, Object> room) {
        String title = (String) room.get("title");
        String type  = (String) room.get("type");
        boolean active = Boolean.TRUE.equals(room.get("active"));

        tvConversationTitle.setText(title != null ? title : "Phòng chat");

        String typeLabel = "activity".equals(type) ? "Hoạt động" :
                           "direct".equals(type) ? "Nhắn tin riêng" : "Nhóm chat";
        tvConversationType.setText(typeLabel);

        if (!active) {
            viewOnlineDot.setVisibility(View.GONE);
            tvConversationStatus.setText("Đã kết thúc");
            tvConversationStatus.setTextColor(0xFF9E8E82);
        } else {
            viewOnlineDot.setVisibility(View.VISIBLE);
            tvConversationStatus.setText("Đang hoạt động");
            tvConversationStatus.setTextColor(0xFF66BB6A);
        }

        // Ẩn settings button với DM
        ivChatSettings.setVisibility("direct".equals(type) ? View.GONE : View.VISIBLE);
    }

    // ======================================================================
    // ⚡ REALTIME LISTENER — Thay thế hoàn toàn HTTP polling 3s cũ!
    // ======================================================================
    private void startRealtimeMessageListener() {
        messageListener = chatRepo.listenToNewMessages(roomId, currentUid,
            new FirestoreChatRepository.MessagesCallback() {
                @Override public void onSuccess(List<Map<String, Object>> newMsgs) {
                    List<ChatMessage> msgs = new ArrayList<>();
                    for (Map<String, Object> m : newMsgs) {
                        String id       = (String) m.get("id");
                        String sender   = (String) m.get("senderName");
                        String content  = (String) m.get("content");
                        boolean isMe    = Boolean.TRUE.equals(m.get("isMyMessage"));
                        com.google.firebase.Timestamp ts = (com.google.firebase.Timestamp) m.get("createdAt");
                        String time = ts != null ? formatTime(ts) : "";
                        msgs.add(new ChatMessage(id, sender, content, time, isMe));
                    }
                    runOnUiThread(() -> {
                        // Append tin nhắn mới (không load lại toàn bộ)
                        List<ChatMessage> current = adapter.getCurrentList();
                        List<ChatMessage> combined = new ArrayList<>(current);
                        combined.addAll(msgs);
                        adapter.submitList(combined, () ->
                            rvMessages.scrollToPosition(combined.size() - 1)
                        );
                    });
                }
                @Override public void onError(String err) {
                    // Silent fail
                }
            });
    }

    private String formatTime(com.google.firebase.Timestamp ts) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault());
        return sdf.format(ts.toDate());
    }

    // ======================================================================
    // Gửi tin nhắn
    // ======================================================================
    private void sendMessage() {
        if (roomId == null) return;
        String content = etMessageInput.getText() != null ?
            etMessageInput.getText().toString().trim() : "";
        if (TextUtils.isEmpty(content)) return;

        etMessageInput.setText("");

        chatRepo.sendMessage(roomId, currentUid, currentName, content,
            new FirestoreChatRepository.MessageCallback() {
                @Override public void onSuccess(Map<String, Object> msg) {
                    // Realtime listener sẽ tự cập nhật UI — không cần làm gì thêm
                }
                @Override public void onError(String err) {
                    runOnUiThread(() ->
                        Toast.makeText(ConversationActivity.this, "Không thể gửi tin nhắn", Toast.LENGTH_SHORT).show()
                    );
                }
            });
    }

    // ======================================================================
    // Member management dialog
    // ======================================================================
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
