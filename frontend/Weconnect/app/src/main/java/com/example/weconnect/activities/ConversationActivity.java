package com.example.weconnect.activities;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
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
import com.example.weconnect.api.FriendApiService;
import com.example.weconnect.api.PostApiService;
import com.example.weconnect.api.RetrofitClient;
import com.example.weconnect.data.FakePostRepository;
import com.example.weconnect.data.FakeSocialRepository;
import com.example.weconnect.models.ApiResponse;
import com.example.weconnect.models.ChatMessage;
import com.example.weconnect.models.ChatMessageApiResponse;
import com.example.weconnect.models.ChatRoom;
import com.example.weconnect.utils.UserActionBottomSheet;
import com.example.weconnect.utils.UserReportBottomSheet;
import com.example.weconnect.websocket.WebSocketManager;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private MaterialCardView composerCard;
    private MaterialCardView blockPanelCard;
    private TextView tvBlockPanelTitle;
    private TextView tvBlockPanelDescription;
    private LinearLayout layoutBlockPanelActions;
    private MaterialButton btnUnblockDirectUser;
    private MaterialButton btnReportDirectUser;
    private MaterialCardView nonFriendInfoCard;
    private ImageView ivNonFriendAvatar;
    private TextView tvNonFriendName;
    private MaterialButton btnAddFriendFromConversation;
    private MaterialButton btnViewProfileFromConversation;
    private MessageAdapter adapter;
    private ChatRoom room;
    private String currentUsername;
    private ChatApiService chatApi;
    private long backendRoomId = -1;
    private long backendPostId = -1;
    private long backendOwnerId = -1;

    // Member info maps populated from room API response
    private final Map<String, Long> memberNameToId = new HashMap<>();
    private final Map<String, String> memberNameToAvatar = new HashMap<>();
    private long otherUserId = -1;
    private String otherUserName = "";
    private String otherUserAvatar = "";
    private boolean currentUserBlockedOther = false;
    private boolean otherUserBlockedCurrent = false;
    private boolean directIsFriend = true;
    private boolean directIsMessageRequest = false;
    private String directFriendStatus = "NONE"; // NONE, PENDING_SENT, PENDING_RECEIVED, FRIEND

    // Block status per group member (memberId → flag)
    private final Map<Long, Boolean> memberIdBlockedByMe = new HashMap<>();
    private final Map<Long, Boolean> memberIdHasBlockedMe = new HashMap<>();
    // Rule 5: lưu Set<memberId> đã được user acknowledge per room
    // Key = roomId, Value = snapshot blocked-member IDs tại lần acknowledge gần nhất
    private static final Map<Long, Set<Long>> acknowledgedBlockedMemberIds = new HashMap<>();

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
        // Lắng nghe sự kiện bị kick khỏi phòng (KICKED event từ server)
        WebSocketManager.getInstance().subscribeToRoomEvents(this::handleRoomEvent);
        // Refresh trạng thái block + friendship khi quay lại màn
        if (backendRoomId > 0 && room != null && isDirectRoom()) {
            refreshDirectBlockStatus();
            loadDirectFriendStatus();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        WebSocketManager.getInstance().unsubscribeFromRoom(backendRoomId);
        WebSocketManager.getInstance().unsubscribeFromRoomEvents();
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
        composerCard = findViewById(R.id.composerCard);
        blockPanelCard = findViewById(R.id.blockPanelCard);
        tvBlockPanelTitle = findViewById(R.id.tvBlockPanelTitle);
        tvBlockPanelDescription = findViewById(R.id.tvBlockPanelDescription);
        layoutBlockPanelActions = findViewById(R.id.layoutBlockPanelActions);
        btnUnblockDirectUser = findViewById(R.id.btnUnblockDirectUser);
        btnReportDirectUser = findViewById(R.id.btnReportDirectUser);
        nonFriendInfoCard = findViewById(R.id.nonFriendInfoCard);
        ivNonFriendAvatar = findViewById(R.id.ivNonFriendAvatar);
        tvNonFriendName = findViewById(R.id.tvNonFriendName);
        btnAddFriendFromConversation = findViewById(R.id.btnAddFriendFromConversation);
        btnViewProfileFromConversation = findViewById(R.id.btnViewProfileFromConversation);
    }

    private void setupRecyclerView() {
        adapter = new MessageAdapter(this);
        adapter.setOnUserClickListener((userId, userName, avatarUrl) ->
                UserActionBottomSheet.show(this, userId, userName, avatarUrl));
        adapter.setFriendCardListener(new MessageAdapter.FriendCardListener() {
            @Override public void onAddFriend() { sendFriendRequestFromConversation(); }
            @Override public void onRespondToRequest() { showFriendResponseDialog(); }
            @Override public void onViewProfile() { openOtherUserProfile(); }
        });
        rvMessages.setLayoutManager(new LinearLayoutManager(this));
        rvMessages.setAdapter(adapter);
    }

    private void setupClickListeners() {
        ivBackConversation.setOnClickListener(v -> finish());
        btnSendMessage.setOnClickListener(v -> sendMessage());
        btnUnblockDirectUser.setOnClickListener(v -> unblockDirectChatUser());
        btnReportDirectUser.setOnClickListener(v ->
                UserReportBottomSheet.show(this, otherUserId, otherUserName));
        btnAddFriendFromConversation.setOnClickListener(v -> sendFriendRequestFromConversation());
        btnViewProfileFromConversation.setOnClickListener(v -> openOtherUserProfile());
        ivChatSettings.setOnClickListener(v -> {
            if (isDirectRoom()) {
                showDirectChatUserActionSheet();
            } else {
                showChatMenu();
            }
        });
    }

    private void openOtherUserProfile() {
        if (otherUserId <= 0) {
            Toast.makeText(this, "Không tìm thấy thông tin người dùng.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentUserBlockedOther || otherUserBlockedCurrent) {
            Toast.makeText(this, "Nội dung này không hiển thị.", Toast.LENGTH_SHORT).show();
            return;
        }

        String displayName = otherUserName != null && !otherUserName.isEmpty()
                ? otherUserName : "Người dùng";
        Intent intent = new Intent(this, UserProfileActivity.class);
        intent.putExtra("username", displayName);
        intent.putExtra("user_id", otherUserId);
        intent.putExtra("view_other", true);
        startActivity(intent);
    }

    private void sendFriendRequestFromConversation() {
        if (otherUserId <= 0) {
            Toast.makeText(this, "Không tìm thấy thông tin người dùng.", Toast.LENGTH_SHORT).show();
            return;
        }

        RetrofitClient.loadToken(this);
        RetrofitClient.getClient().create(FriendApiService.class)
                .sendFriendRequest(otherUserId).enqueue(new Callback<ApiResponse<Void>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<Void>> call,
                                           Response<ApiResponse<Void>> response) {
                        if (response.isSuccessful()) {
                            Toast.makeText(ConversationActivity.this,
                                    "Đã gửi lời mời kết bạn", Toast.LENGTH_SHORT).show();
                            directFriendStatus = "PENDING_SENT";
                            applyNonFriendInfoArea();
                        } else {
                            Toast.makeText(ConversationActivity.this,
                                    "Không thể gửi lời mời kết bạn", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                        Toast.makeText(ConversationActivity.this,
                                "Lỗi kết nối", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void showFriendResponseDialog() {
        if (otherUserId <= 0) return;
        String name = otherUserName != null && !otherUserName.isEmpty() ? otherUserName : "Người này";
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Lời mời kết bạn")
                .setMessage(name + " đã gửi lời mời kết bạn cho bạn.")
                .setPositiveButton("Xác nhận", (d, w) -> acceptFriendRequestFromConversation())
                .setNegativeButton("Từ chối", (d, w) -> declineFriendRequestFromConversation())
                .setNeutralButton("Huỷ", null)
                .show();
    }

    private void acceptFriendRequestFromConversation() {
        if (otherUserId <= 0) return;
        RetrofitClient.loadToken(this);
        RetrofitClient.getClient().create(FriendApiService.class)
                .acceptFriend(otherUserId).enqueue(new Callback<ApiResponse<Void>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<Void>> call,
                                           Response<ApiResponse<Void>> response) {
                        if (response.isSuccessful()) {
                            Toast.makeText(ConversationActivity.this,
                                    "Đã xác nhận kết bạn!", Toast.LENGTH_SHORT).show();
                            directIsFriend = true;
                            directFriendStatus = "FRIEND";
                            applyNonFriendInfoArea();
                        } else {
                            Toast.makeText(ConversationActivity.this,
                                    "Không thể xác nhận lời mời.", Toast.LENGTH_SHORT).show();
                        }
                    }
                    @Override
                    public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                        Toast.makeText(ConversationActivity.this,
                                "Lỗi kết nối", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void declineFriendRequestFromConversation() {
        if (otherUserId <= 0) return;
        RetrofitClient.loadToken(this);
        RetrofitClient.getClient().create(FriendApiService.class)
                .declineFriend(otherUserId).enqueue(new Callback<ApiResponse<Void>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<Void>> call,
                                           Response<ApiResponse<Void>> response) {
                        if (response.isSuccessful()) {
                            Toast.makeText(ConversationActivity.this,
                                    "Đã từ chối lời mời.", Toast.LENGTH_SHORT).show();
                            directFriendStatus = "NONE";
                            applyNonFriendInfoArea();
                        } else {
                            Toast.makeText(ConversationActivity.this,
                                    "Không thể từ chối lời mời.", Toast.LENGTH_SHORT).show();
                        }
                    }
                    @Override
                    public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                        Toast.makeText(ConversationActivity.this,
                                "Lỗi kết nối", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private boolean isDirectRoom() {
        return room != null && ChatRoom.TYPE_DIRECT.equals(room.getType());
    }

    private void showDirectChatUserActionSheet() {
        if (!isDirectRoom()) return;
        if (otherUserId <= 0) {
            Toast.makeText(this, "Không tìm thấy thông tin người dùng.", Toast.LENGTH_SHORT).show();
            return;
        }

        BottomSheetDialog sheet = new BottomSheetDialog(this);
        sheet.getBehavior().setSkipCollapsed(true);

        LinearLayout root = buildIosRoot();

        LinearLayout group1 = buildIosGroup();
        TextView tvHeader = new TextView(this);
        String displayName = otherUserName != null && !otherUserName.isEmpty()
                ? otherUserName : "Người dùng";
        tvHeader.setText(displayName);
        tvHeader.setTextSize(13);
        tvHeader.setTextColor(0xFF8E8E93);
        tvHeader.setGravity(Gravity.CENTER);
        tvHeader.setPadding(dpPx(16), dpPx(13), dpPx(16), dpPx(4));
        group1.addView(tvHeader, matchW());

        addIosSep(group1);
        addIosRow(group1, "Xem trang cá nhân", 0xFF1C1C1E, v -> {
            sheet.dismiss();
            openOtherUserProfile();
        });

        if (currentUserBlockedOther) {
            addIosSep(group1);
            addIosRow(group1, "Bỏ chặn", 0xFF1C1C1E, v -> {
                sheet.dismiss();
                unblockDirectChatUser();
            });
        } else {
        addIosSep(group1);
        addIosRow(group1, "Chặn", 0xFFFF3B30, v -> {
            sheet.dismiss();
            showBlockDirectChatUserConfirmDialog();
        });

        }

        addIosSep(group1);
        addIosRow(group1, "Báo cáo", 0xFF1C1C1E, v -> {
            sheet.dismiss();
            UserReportBottomSheet.show(this, otherUserId, displayName);
        });

        root.addView(group1, matchW());
        addGroupGap(root);

        LinearLayout group2 = buildIosGroup();
        addIosRow(group2, "Huỷ", 0xFF1C1C1E, v -> sheet.dismiss());
        root.addView(group2, matchW());

        sheet.setContentView(root);
        makeSheetTransparent(root);
        sheet.show();
    }

    private void showBlockDirectChatUserConfirmDialog() {
        if (otherUserId <= 0) {
            Toast.makeText(this, "Không tìm thấy thông tin người dùng.", Toast.LENGTH_SHORT).show();
            return;
        }

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Chặn người dùng?")
                .setMessage("Bạn sẽ không nhận được tin nhắn từ người này sau khi chặn.")
                .setPositiveButton("Chặn", (d, w) -> blockDirectChatUser())
                .setNegativeButton("Huỷ", null)
                .show();
    }

    private void blockDirectChatUser() {
        if (otherUserId <= 0) {
            Toast.makeText(this, "Không tìm thấy thông tin người dùng.", Toast.LENGTH_SHORT).show();
            return;
        }

        long targetUserId = otherUserId;
        RetrofitClient.loadToken(this);
        RetrofitClient.getClient().create(FriendApiService.class)
                .blockUser(targetUserId).enqueue(new Callback<ApiResponse<Void>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<Void>> call,
                                           Response<ApiResponse<Void>> response) {
                        if (response.isSuccessful()) {
                            Toast.makeText(ConversationActivity.this,
                                    "Đã chặn người dùng", Toast.LENGTH_SHORT).show();
                            refreshDirectBlockStatus();
                        } else {
                            Toast.makeText(ConversationActivity.this,
                                    "Không thể chặn người dùng", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                        Toast.makeText(ConversationActivity.this,
                                "Lỗi kết nối", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void unblockDirectChatUser() {
        if (otherUserId <= 0) {
            Toast.makeText(this, "Không tìm thấy thông tin người dùng.", Toast.LENGTH_SHORT).show();
            return;
        }

        RetrofitClient.loadToken(this);
        RetrofitClient.getClient().create(FriendApiService.class)
                .unblockUser(otherUserId).enqueue(new Callback<ApiResponse<Void>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<Void>> call,
                                           Response<ApiResponse<Void>> response) {
                        if (response.isSuccessful()) {
                            Toast.makeText(ConversationActivity.this,
                                    "Đã bỏ chặn", Toast.LENGTH_SHORT).show();
                            refreshDirectBlockStatus();
                        } else {
                            Toast.makeText(ConversationActivity.this,
                                    "Không thể bỏ chặn", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                        Toast.makeText(ConversationActivity.this,
                                "Lỗi kết nối", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void refreshDirectBlockStatus() {
        if (!isDirectRoom() || otherUserId <= 0) {
            applyDirectBlockStatus(false, false);
            return;
        }

        RetrofitClient.loadToken(this);
        RetrofitClient.getClient().create(FriendApiService.class)
                .getBlockStatus(otherUserId).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<Map<String, Object>>> call,
                                           Response<ApiResponse<Map<String, Object>>> response) {
                        Map<String, Object> status = response.isSuccessful()
                                && response.body() != null ? response.body().getResult() : null;
                        applyDirectBlockStatus(
                                asBoolean(status, "currentUserBlockedOther"),
                                asBoolean(status, "otherUserBlockedCurrent"));
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                        applyDirectBlockStatus(false, false);
                    }
                });
    }

    private void loadDirectFriendStatus() {
        if (!isDirectRoom() || otherUserId <= 0) return;
        RetrofitClient.loadToken(this);
        RetrofitClient.getClient().create(FriendApiService.class)
                .getFriendStatus(otherUserId).enqueue(new Callback<ApiResponse<String>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<String>> call,
                                           Response<ApiResponse<String>> response) {
                        if (response.isSuccessful() && response.body() != null
                                && response.body().getResult() != null) {
                            directFriendStatus = String.valueOf(response.body().getResult())
                                    .trim().replace("\"", "");
                        }
                        // Đồng bộ directIsFriend với status thực tế từ API
                        if ("FRIEND".equals(directFriendStatus)) directIsFriend = true;
                        applyNonFriendInfoArea();
                        applyActivityStatus();
                    }
                    @Override
                    public void onFailure(Call<ApiResponse<String>> call, Throwable t) {
                        // giữ trạng thái hiện tại
                    }
                });
    }

    private void applyDirectBlockStatus(boolean blockedByMe, boolean blockedByOther) {
        currentUserBlockedOther = blockedByMe;
        otherUserBlockedCurrent = blockedByOther;

        // Mask tên và avatar của user đã chặn mình trong message bubbles
        if (blockedByOther && !blockedByMe && otherUserId > 0) {
            adapter.setMaskedUserId(otherUserId);
        } else {
            adapter.setMaskedUserId(-1);
        }

        boolean blocked = blockedByMe || blockedByOther;
        composerCard.setVisibility(blocked ? View.GONE : View.VISIBLE);
        blockPanelCard.setVisibility(blocked ? View.VISIBLE : View.GONE);
        etMessageInput.setEnabled(!blocked);
        btnSendMessage.setEnabled(!blocked);

        if (blocked) {
            etMessageInput.setText("");
            etMessageInput.clearFocus();
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager)
                            getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(etMessageInput.getWindowToken(), 0);
            }
        }

        if (blockedByMe) {
            // Case 1: mình là người chặn — hiển thị panel với "Bỏ chặn" + "Báo cáo"
            String name = otherUserName != null && !otherUserName.isEmpty()
                    ? otherUserName : "người dùng này";
            tvBlockPanelTitle.setText("Bạn đã chặn " + name);
            tvBlockPanelDescription.setText(
                    "Bạn không thể nhắn tin cho họ và cũng không nhận được tin nhắn của họ.");
            tvBlockPanelDescription.setVisibility(View.VISIBLE);
            layoutBlockPanelActions.setVisibility(View.VISIBLE);
            btnUnblockDirectUser.setVisibility(View.VISIBLE);
            btnReportDirectUser.setVisibility(View.VISIBLE);
        } else if (blockedByOther) {
            // Case 2: mình bị chặn — chỉ hiển thị thông báo, không có action
            tvBlockPanelTitle.setText("Hiện không liên lạc được với người này");
            tvBlockPanelDescription.setVisibility(View.GONE);
            layoutBlockPanelActions.setVisibility(View.GONE);
        }

        if (isDirectRoom()) {
            if (blockedByOther && !blockedByMe) {
                // Ẩn thông tin thật của người đã chặn mình
                tvConversationTitle.setText("Người dùng không tồn tại");
                com.bumptech.glide.Glide.with(this).clear(ivConversationAvatar);
                ivConversationAvatar.setImageResource(R.drawable.ic_user_placeholder);
                tvConversationStatus.setVisibility(View.GONE);
                viewOnlineDot.setVisibility(View.GONE);
            } else if (blockedByMe) {
                // Tôi chặn họ — ẩn trạng thái hoạt động
                viewOnlineDot.setVisibility(View.GONE);
                tvConversationStatus.setVisibility(View.GONE);
            } else if (!blocked && room != null) {
                // Khôi phục header thật khi không còn bị chặn
                tvConversationTitle.setText(room.getTitle());
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
                applyActivityStatus();
            }

            if (blocked) {
                ivChatSettings.setVisibility(View.GONE);
                ivConversationAvatar.setClickable(false);
                ivConversationAvatar.setOnClickListener(null);
                tvConversationTitle.setClickable(false);
                tvConversationTitle.setOnClickListener(null);
            } else {
                ivChatSettings.setVisibility(View.VISIBLE);
                ivConversationAvatar.setClickable(true);
                ivConversationAvatar.setOnClickListener(v -> showDirectChatUserActionSheet());
                tvConversationTitle.setClickable(true);
                tvConversationTitle.setOnClickListener(v -> showDirectChatUserActionSheet());
            }
        }

        applyNonFriendInfoArea();
    }

    private boolean amIOwner() {
        long myId = RetrofitClient.getUserId(this);
        return backendOwnerId > 0 && myId > 0 && backendOwnerId == myId;
    }

    private boolean asBoolean(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key)) return false;
        Object value = map.get(key);
        return value instanceof Boolean && (Boolean) value;
    }

    private void showChatMenu() {
        if (room == null) return;
        boolean isOwner = amIOwner();

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
            boolean isActivityRoom = ChatRoom.TYPE_ACTIVITY.equals(room.getType());
            if (isActivityRoom) {
                addIosRow(group1, "Hủy hoạt động", 0xFFFF3B30, v -> {
                    sheet.dismiss();
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                            .setTitle("Hủy hoạt động?")
                            .setMessage("Khi hủy hoạt động, bài viết và nhóm chat của hoạt động này sẽ không còn khả dụng với tất cả thành viên.")
                            .setPositiveButton("Xác nhận hủy", (d, w) -> cancelActivity())
                            .setNegativeButton("Hủy", null)
                            .show();
                });
            } else {
                addIosRow(group1, "Xóa nhóm", 0xFFFF3B30, v -> {
                    sheet.dismiss();
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                            .setTitle("Xóa nhóm?")
                            .setMessage("Bạn có chắc chắn muốn xóa nhóm này không? Hành động này không thể hoàn tác.")
                            .setPositiveButton("Xóa nhóm", (d, w) -> deleteRoom())
                            .setNegativeButton("Hủy", null)
                            .show();
                });
            }
        } else {
            addIosSep(group1);
            addIosRow(group1, "Rời nhóm", 0xFFFF3B30, v -> {
                sheet.dismiss();
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                        .setTitle("Rời nhóm?")
                        .setMessage("Bạn có chắc chắn muốn rời khỏi nhóm này không?")
                        .setPositiveButton("Rời nhóm", (d, w) -> leaveRoom())
                        .setNegativeButton("Hủy", null)
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
                Toast.makeText(ConversationActivity.this, "Đã xóa nhóm", Toast.LENGTH_SHORT).show();
                finish();
            }
            @Override
            public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                Toast.makeText(ConversationActivity.this, "Lỗi kết nối", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void cancelActivity() {
        if (backendRoomId <= 0) return;
        RetrofitClient.loadToken(this);
        chatApi.cancelActivityRoom(backendRoomId).enqueue(new Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(ConversationActivity.this, "Đã hủy hoạt động", Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    String errMsg = "Không thể hủy hoạt động";
                    try {
                        if (response.errorBody() != null) {
                            org.json.JSONObject errJson = new org.json.JSONObject(response.errorBody().string());
                            if (errJson.has("message") && !errJson.getString("message").isEmpty()) {
                                errMsg = errJson.getString("message");
                            }
                        }
                    } catch (Exception ignored) {}
                    Toast.makeText(ConversationActivity.this, errMsg, Toast.LENGTH_LONG).show();
                }
            }
            @Override
            public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                Toast.makeText(ConversationActivity.this, "Lỗi kết nối: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void leaveRoom() {
        if (backendRoomId <= 0) return;
        RetrofitClient.loadToken(this);
        chatApi.leaveRoom(backendRoomId).enqueue(new Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(ConversationActivity.this, "Đã rời nhóm", Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    Toast.makeText(ConversationActivity.this, "Không thể rời nhóm", Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                Toast.makeText(ConversationActivity.this, "Lỗi kết nối", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void confirmRemoveMember(String memberName, long memberId) {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Xóa thành viên khỏi nhóm?")
                .setMessage("Bạn có chắc chắn muốn xóa thành viên này khỏi nhóm không?")
                .setPositiveButton("Xóa khỏi nhóm", (d, w) -> doRemoveMember(memberName, memberId))
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void doRemoveMember(String memberName, long memberId) {
        if (backendRoomId <= 0 || memberId <= 0) return;
        RetrofitClient.loadToken(this);
        chatApi.removeMember(backendRoomId, memberId).enqueue(new Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {
                if (response.isSuccessful()) {
                    // Xóa local cache ngay
                    if (room != null) room.removeMember(memberName);
                    memberNameToId.remove(memberName);
                    memberNameToAvatar.remove(memberName);
                    updateGroupMemberCount();
                    Toast.makeText(ConversationActivity.this,
                            "Đã xóa thành viên khỏi nhóm", Toast.LENGTH_SHORT).show();
                    // Reload room từ server để đồng bộ memberCount chính xác rồi mở lại dialog
                    refreshRoomThenShowMemberDialog();
                } else {
                    Toast.makeText(ConversationActivity.this, "Không thể xóa thành viên", Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                Toast.makeText(ConversationActivity.this, "Lỗi kết nối", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Reload room info từ server để cập nhật memberCount, sau đó mở lại dialog thành viên
    private void refreshRoomThenShowMemberDialog() {
        if (backendRoomId <= 0) return;
        RetrofitClient.loadToken(this);
        chatApi.getRoom(backendRoomId).enqueue(new Callback<ApiResponse<com.example.weconnect.models.ChatRoomApiResponse>>() {
            @Override
            public void onResponse(Call<ApiResponse<com.example.weconnect.models.ChatRoomApiResponse>> call,
                                   Response<ApiResponse<com.example.weconnect.models.ChatRoomApiResponse>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getResult() != null) {
                    room = parseRoomFromApi(response.body().getResult());
                    // Cập nhật header số thành viên
                    int count = room.getMembers().size();
                    int maxMem = room.getMaxMembers();
                    String memberText = maxMem > 0 ? count + " / " + maxMem : count + " thành viên";
                    tvConversationStatus.setText(memberText);
                    tvConversationStatus.setVisibility(View.VISIBLE);
                }
                showMemberManagementDialog();
            }
            @Override
            public void onFailure(Call<ApiResponse<com.example.weconnect.models.ChatRoomApiResponse>> call, Throwable t) {
                showMemberManagementDialog();
            }
        });
    }

    // Rule 5: Cảnh báo khi group có thành viên bị chặn bởi currentUser
    private void checkAndShowGroupBlockWarning() {
        if (backendRoomId <= 0) return;
        Set<Long> currentBlockedInGroup = memberIdBlockedByMe.keySet();
        if (currentBlockedInGroup.isEmpty()) return;

        // So sánh với snapshot đã được acknowledge trước đó
        Set<Long> lastAcknowledged = acknowledgedBlockedMemberIds.get(backendRoomId);
        if (lastAcknowledged != null && lastAcknowledged.equals(currentBlockedInGroup)) return;

        // Danh sách thay đổi (hoặc chưa từng acknowledge) → hiện popup
        showGroupBlockWarning(new HashSet<>(currentBlockedInGroup));
    }

    private void showGroupBlockWarning(Set<Long> currentBlockedSnapshot) {
        if (isFinishing() || isDestroyed()) return;
        int count = currentBlockedSnapshot.size();

        // Build id → avatarUrl reverse map
        Map<Long, String> idToAvatar = new HashMap<>();
        for (Map.Entry<String, Long> entry : memberNameToId.entrySet()) {
            String av = memberNameToAvatar.get(entry.getKey());
            if (av != null && !av.isEmpty()) idToAvatar.put(entry.getValue(), av);
        }

        // Collect up to 3 avatar URLs for blocked members
        List<String> avatarUrls = new ArrayList<>();
        for (Long id : currentBlockedSnapshot) {
            avatarUrls.add(idToAvatar.get(id)); // null → placeholder
            if (avatarUrls.size() >= 3) break;
        }

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_group_blocked_warning, null);

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(dialogView);
        dialog.setCancelable(false);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            DisplayMetrics metrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(metrics);
            int margin = (int) (24 * getResources().getDisplayMetrics().density);
            dialog.getWindow().setLayout(metrics.widthPixels - 2 * margin, ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setGravity(Gravity.CENTER);
            WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
            lp.dimAmount = 0.6f;
            dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            dialog.getWindow().setAttributes(lp);
        }

        // Title
        TextView tvTitle = dialogView.findViewById(R.id.tvBlockedWarningTitle);
        tvTitle.setText("Đoạn chat này có " + count + " người mà bạn đã chặn");

        // Avatar cluster — frames listed front-to-back in array (frame1=front)
        View[] frames = {
            dialogView.findViewById(R.id.frameAvatar1),
            dialogView.findViewById(R.id.frameAvatar2),
            dialogView.findViewById(R.id.frameAvatar3)
        };
        ImageView[] ivAvatars = {
            dialogView.findViewById(R.id.ivAvatar1),
            dialogView.findViewById(R.id.ivAvatar2),
            dialogView.findViewById(R.id.ivAvatar3)
        };
        for (int i = 0; i < avatarUrls.size(); i++) {
            frames[i].setVisibility(View.VISIBLE);
            String av = avatarUrls.get(i);
            if (av != null && !av.isEmpty()) {
                String fullUrl = av.startsWith("http") ? av
                        : RetrofitClient.getBaseUrl() + (av.startsWith("/") ? av.substring(1) : av);
                com.bumptech.glide.Glide.with(this)
                        .load(fullUrl)
                        .circleCrop()
                        .placeholder(R.drawable.ic_user_placeholder)
                        .error(R.drawable.ic_user_placeholder)
                        .into(ivAvatars[i]);
            }
        }

        // Close button
        dialogView.findViewById(R.id.btnCloseBlockedWarning).setOnClickListener(v -> {
            acknowledgedBlockedMemberIds.put(backendRoomId, currentBlockedSnapshot);
            dialog.dismiss();
        });

        // "Xem người bạn đã chặn" link
        dialogView.findViewById(R.id.tvViewBlockedLink).setOnClickListener(v ->
                startActivity(new Intent(ConversationActivity.this, BlockedUsersActivity.class)));

        // Ở lại đoạn chat
        dialogView.findViewById(R.id.btnStayInChat).setOnClickListener(v -> {
            acknowledgedBlockedMemberIds.put(backendRoomId, currentBlockedSnapshot);
            dialog.dismiss();
        });

        // Rời khỏi đoạn chat
        dialogView.findViewById(R.id.btnLeaveChat).setOnClickListener(v -> {
            dialog.dismiss();
            leaveGroupAndFinish();
        });

        dialog.show();
    }

    private void leaveGroupAndFinish() {
        RetrofitClient.loadToken(this);
        chatApi.leaveRoom(backendRoomId).enqueue(new Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {
                finish();
            }
            @Override
            public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                Toast.makeText(ConversationActivity.this, "Lỗi kết nối", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void updateGroupMemberCount() {
        if (room == null || isDirectRoom()) return;
        int count = room.getMembers().size();
        if (count > 0) {
            tvConversationStatus.setText(count + " thành viên");
            tvConversationStatus.setVisibility(View.VISIBLE);
        }
    }

    // Xử lý sự kiện WebSocket từ server (KICKED, v.v.)
    private void handleRoomEvent(String payload) {
        try {
            org.json.JSONObject json = new org.json.JSONObject(payload);
            String type = json.optString("type", "");
            long eventRoomId = json.optLong("roomId", -1);

            if ("KICKED".equals(type) && eventRoomId == backendRoomId) {
                // Disable input ngay lập tức
                etMessageInput.setEnabled(false);
                btnSendMessage.setEnabled(false);
                composerCard.setVisibility(View.GONE);

                // Huỷ WebSocket subscription để tránh nhận thêm events
                WebSocketManager.getInstance().unsubscribeFromRoom(backendRoomId);
                WebSocketManager.getInstance().unsubscribeFromRoomEvents();

                // Hiển thị thông báo và điều hướng về danh sách chat
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                        .setTitle("Đã bị xóa khỏi nhóm")
                        .setMessage("Bạn đã bị xóa khỏi nhóm này.")
                        .setCancelable(false)
                        .setPositiveButton("Đóng", (d, w) -> finish())
                        .show();
            } else if ("ACTIVITY_CANCELLED".equals(type) && eventRoomId == backendRoomId) {
                // Chủ hoạt động đã hủy — disable input ngay lập tức
                etMessageInput.setEnabled(false);
                btnSendMessage.setEnabled(false);
                composerCard.setVisibility(View.GONE);

                WebSocketManager.getInstance().unsubscribeFromRoom(backendRoomId);
                WebSocketManager.getInstance().unsubscribeFromRoomEvents();

                new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                        .setTitle("Hoạt động đã bị hủy")
                        .setMessage("Hoạt động không khả dụng.")
                        .setCancelable(false)
                        .setPositiveButton("Đóng", (d, w) -> finish())
                        .show();
            }
        } catch (Exception e) {
            android.util.Log.e("ConversationActivity", "handleRoomEvent error: " + e.getMessage());
        }
    }

    private void showMemberActionSheet(String memberName, long memberId,
                                       boolean memberIsOwner, boolean currentUserIsOwner,
                                       long myId, boolean iBlockedMember, boolean memberBlockedMe) {
        if (memberId > 0 && memberId == myId) return;

        BottomSheetDialog sheet = new BottomSheetDialog(this);
        sheet.getBehavior().setSkipCollapsed(true);

        LinearLayout root = buildIosRoot();
        LinearLayout group1 = buildIosGroup();

        // Header: tên thành viên (ẩn tên thật nếu member đã chặn tôi)
        String headerDisplayName = (memberBlockedMe && !iBlockedMember) ? "Người dùng" : memberName;
        TextView tvHeader = new TextView(this);
        tvHeader.setText(iBlockedMember ? headerDisplayName + " · Đã bị chặn" : headerDisplayName);
        tvHeader.setTextSize(13);
        tvHeader.setTextColor(0xFF8E8E93);
        tvHeader.setGravity(Gravity.CENTER);
        tvHeader.setPadding(dpPx(16), dpPx(13), dpPx(16), dpPx(4));
        group1.addView(tvHeader, matchW());

        if (iBlockedMember || memberBlockedMe) {
            // ── Block relation: hiển thị action nhưng xử lý fallback ─────
            addIosSep(group1);
            addIosRow(group1, "Xem trang cá nhân", 0xFF1C1C1E, v -> {
                sheet.dismiss();
                Intent profileIntent = new Intent(ConversationActivity.this, UserProfileActivity.class);
                profileIntent.putExtra("user_id", memberId);
                profileIntent.putExtra("view_other", true);
                profileIntent.putExtra("is_blocked_by_me", iBlockedMember);
                profileIntent.putExtra("has_blocked_me", memberBlockedMe);
                startActivity(profileIntent);
            });
            addIosSep(group1);
            addIosRow(group1, "Nhắn tin", 0xFF1C1C1E, v -> {
                sheet.dismiss();
                if (iBlockedMember) {
                    // Tôi chặn họ: thử mở phòng chat cũ nếu đã tồn tại
                    RetrofitClient.loadToken(ConversationActivity.this);
                    chatApi.getDirectRoom(memberId).enqueue(new Callback<ApiResponse<com.example.weconnect.models.ChatRoomApiResponse>>() {
                        @Override
                        public void onResponse(Call<ApiResponse<com.example.weconnect.models.ChatRoomApiResponse>> call,
                                               Response<ApiResponse<com.example.weconnect.models.ChatRoomApiResponse>> response) {
                            if (response.isSuccessful() && response.body() != null
                                    && response.body().getResult() != null) {
                                long roomId = response.body().getResult().getId();
                                Intent intent = new Intent(ConversationActivity.this, ConversationActivity.class);
                                intent.putExtra("room_id", roomId);
                                startActivity(intent);
                            } else {
                                Toast.makeText(ConversationActivity.this,
                                        "Không thể nhắn tin vì bạn đã chặn người này.",
                                        Toast.LENGTH_SHORT).show();
                            }
                        }
                        @Override
                        public void onFailure(Call<ApiResponse<com.example.weconnect.models.ChatRoomApiResponse>> call, Throwable t) {
                            Toast.makeText(ConversationActivity.this,
                                    "Không thể nhắn tin vì bạn đã chặn người này.",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                } else {
                    // Tôi bị họ chặn: không mở được
                    Toast.makeText(ConversationActivity.this,
                            "Hiện không thể nhắn tin với người này.", Toast.LENGTH_SHORT).show();
                }
            });
            if (currentUserIsOwner && !memberIsOwner) {
                addIosSep(group1);
                addIosRow(group1, "Xóa khỏi nhóm", 0xFFFF3B30, v -> {
                    sheet.dismiss();
                    confirmRemoveMember(memberName, memberId);
                });
            }
        } else {
            // ── Case C: không có block relation ──────────────────────────
            // Rule 8: "Xem trang cá nhân" + "Nhắn tin"
            addIosSep(group1);
            addIosRow(group1, "Xem trang cá nhân", 0xFF1C1C1E, v -> {
                sheet.dismiss();
                Intent intent = new Intent(this, UserProfileActivity.class);
                intent.putExtra("username", memberName);
                intent.putExtra("user_id", memberId);
                intent.putExtra("view_other", true);
                startActivity(intent);
            });

            addIosSep(group1);
            addIosRow(group1, "Nhắn tin", 0xFF1C1C1E, v -> {
                sheet.dismiss();
                RetrofitClient.loadToken(ConversationActivity.this);
                chatApi.getDirectRoom(memberId).enqueue(new Callback<ApiResponse<com.example.weconnect.models.ChatRoomApiResponse>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<com.example.weconnect.models.ChatRoomApiResponse>> call,
                                           Response<ApiResponse<com.example.weconnect.models.ChatRoomApiResponse>> response) {
                        if (response.isSuccessful() && response.body() != null
                                && response.body().getResult() != null) {
                            long roomId = response.body().getResult().getId();
                            Intent intent = new Intent(ConversationActivity.this, ConversationActivity.class);
                            intent.putExtra("room_id", roomId);
                            startActivity(intent);
                        } else {
                            String msg = "Không thể mở cuộc trò chuyện";
                            try {
                                if (response.errorBody() != null) {
                                    org.json.JSONObject json = new org.json.JSONObject(response.errorBody().string());
                                    if (json.has("message")) msg = json.getString("message");
                                }
                            } catch (Exception ignored) {}
                            Toast.makeText(ConversationActivity.this, msg, Toast.LENGTH_SHORT).show();
                        }
                    }
                    @Override
                    public void onFailure(Call<ApiResponse<com.example.weconnect.models.ChatRoomApiResponse>> call, Throwable t) {
                        Toast.makeText(ConversationActivity.this, "Lỗi kết nối", Toast.LENGTH_SHORT).show();
                    }
                });
            });

            // "Chặn" — context-aware theo Rules 2/3/4
            addIosSep(group1);
            addIosRow(group1, "Chặn", 0xFFFF3B30, v -> {
                sheet.dismiss();
                if (!currentUserIsOwner && memberIsOwner) {
                    // Rule 2: currentUser (thường) chặn chủ phòng → leave room sau khi block
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(ConversationActivity.this)
                            .setTitle("Chặn chủ phòng?")
                            .setMessage("Nếu bạn chặn chủ phòng, bạn sẽ không thể xem hoạt động này nữa và sẽ tự động rời khỏi nhóm chat của hoạt động.")
                            .setNegativeButton("Hủy", null)
                            .setPositiveButton("Chặn và rời nhóm", (d, w) -> {
                                RetrofitClient.loadToken(ConversationActivity.this);
                                RetrofitClient.getClient().create(FriendApiService.class)
                                        .blockUser(memberId).enqueue(new Callback<ApiResponse<Void>>() {
                                            @Override
                                            public void onResponse(Call<ApiResponse<Void>> call,
                                                                   Response<ApiResponse<Void>> response) {
                                                // Sau khi block xong → rời nhóm
                                                leaveGroupAndFinish();
                                            }
                                            @Override
                                            public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                                                Toast.makeText(ConversationActivity.this,
                                                        "Lỗi kết nối", Toast.LENGTH_SHORT).show();
                                            }
                                        });
                            })
                            .show();
                } else if (currentUserIsOwner && !memberIsOwner) {
                    // Rule 3: chủ phòng chặn member → chỉ tạo block relation, không auto-remove khỏi nhóm
                    // "Chặn" và "Xóa khỏi nhóm" là 2 action riêng biệt (Rule 5)
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(ConversationActivity.this)
                            .setTitle("Chặn thành viên?")
                            .setMessage("Bạn sẽ không thể nhắn tin riêng hoặc tương tác trực tiếp với người này. Thành viên vẫn ở trong nhóm.")
                            .setNegativeButton("Hủy", null)
                            .setPositiveButton("Chặn", (d, w) -> {
                                RetrofitClient.loadToken(ConversationActivity.this);
                                RetrofitClient.getClient().create(FriendApiService.class)
                                        .blockUser(memberId).enqueue(new Callback<ApiResponse<Void>>() {
                                            @Override
                                            public void onResponse(Call<ApiResponse<Void>> call,
                                                                   Response<ApiResponse<Void>> response) {
                                                if (response.isSuccessful()) {
                                                    memberIdBlockedByMe.put(memberId, true);
                                                    adapter.setGroupBlockedSets(memberIdBlockedByMe.keySet(),
                                                            memberIdHasBlockedMe.keySet());
                                                    Toast.makeText(ConversationActivity.this,
                                                            "Đã chặn " + memberName, Toast.LENGTH_SHORT).show();
                                                } else {
                                                    Toast.makeText(ConversationActivity.this,
                                                            "Không thể chặn người dùng", Toast.LENGTH_SHORT).show();
                                                }
                                            }
                                            @Override
                                            public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                                                Toast.makeText(ConversationActivity.this,
                                                        "Lỗi kết nối", Toast.LENGTH_SHORT).show();
                                            }
                                        });
                            })
                            .show();
                } else {
                    // Rule 4: hai user thường → chỉ block, không remove khỏi nhóm
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(ConversationActivity.this)
                            .setTitle("Chặn " + memberName + "?")
                            .setMessage("Người này sẽ không thể nhắn tin riêng hoặc xem hồ sơ của bạn. Cả hai vẫn ở trong nhóm.")
                            .setNegativeButton("Hủy", null)
                            .setPositiveButton("Chặn", (d, w) -> {
                                RetrofitClient.loadToken(ConversationActivity.this);
                                RetrofitClient.getClient().create(FriendApiService.class)
                                        .blockUser(memberId).enqueue(new Callback<ApiResponse<Void>>() {
                                            @Override
                                            public void onResponse(Call<ApiResponse<Void>> call,
                                                                   Response<ApiResponse<Void>> response) {
                                                if (response.isSuccessful()) {
                                                    memberIdBlockedByMe.put(memberId, true);
                                                    adapter.setGroupBlockedSets(memberIdBlockedByMe.keySet(),
                                                            memberIdHasBlockedMe.keySet());
                                                    // Kiểm tra lại popup nếu danh sách blocked thay đổi
                                                    checkAndShowGroupBlockWarning();
                                                    Toast.makeText(ConversationActivity.this,
                                                            "Đã chặn " + memberName, Toast.LENGTH_SHORT).show();
                                                } else {
                                                    Toast.makeText(ConversationActivity.this,
                                                            "Không thể chặn người dùng", Toast.LENGTH_SHORT).show();
                                                }
                                            }
                                            @Override
                                            public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                                                Toast.makeText(ConversationActivity.this,
                                                        "Lỗi kết nối", Toast.LENGTH_SHORT).show();
                                            }
                                        });
                            })
                            .show();
                }
            });

            // "Xóa khỏi nhóm": chỉ chủ phòng, không thể xóa chính chủ phòng khác
            if (currentUserIsOwner && !memberIsOwner) {
                addIosSep(group1);
                addIosRow(group1, "Xóa khỏi nhóm", 0xFFFF3B30, v -> {
                    sheet.dismiss();
                    confirmRemoveMember(memberName, memberId);
                });
            }
        }

        root.addView(group1, matchW());
        addGroupGap(root);

        LinearLayout group2 = buildIosGroup();
        addIosRow(group2, "Hủy", 0xFF1C1C1E, v -> sheet.dismiss());
        root.addView(group2, matchW());

        sheet.setContentView(root);
        makeSheetTransparent(root);
        sheet.show();
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
                    applyDirectBlockStatus(currentUserBlockedOther, otherUserBlockedCurrent);
                    refreshDirectBlockStatus();
                    loadDirectFriendStatus();
                    // Feed group block sets vào adapter (Rule 7)
                    if (!isDirectRoom()) {
                        adapter.setGroupBlockedSets(memberIdBlockedByMe.keySet(),
                                memberIdHasBlockedMe.keySet());
                        checkAndShowGroupBlockWarning();
                    }
                    loadMessagesFromApi();
                    WebSocketManager.getInstance().subscribeToRoom(backendRoomId, ConversationActivity.this::onNewMessage);
                } else {
                    // Lấy message lỗi từ server để phân biệt "không còn thành viên" vs "không tìm thấy"
                    String errorMsg = "Hoạt động không khả dụng.";
                    try {
                        if (response.errorBody() != null) {
                            org.json.JSONObject errJson = new org.json.JSONObject(response.errorBody().string());
                            if (errJson.has("message") && !errJson.getString("message").isEmpty()) {
                                errorMsg = errJson.getString("message");
                            }
                        }
                    } catch (Exception ignored) {}
                    final String finalMsg = errorMsg;
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(ConversationActivity.this)
                            .setTitle("Không thể mở đoạn chat")
                            .setMessage(finalMsg)
                            .setCancelable(false)
                            .setPositiveButton("Đóng", (d, w) -> finish())
                            .show();
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
        backendPostId = data.getPostId() != null ? data.getPostId() : -1;
        backendOwnerId = data.getOwnerId();
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
        memberIdBlockedByMe.clear();
        memberIdHasBlockedMe.clear();
        otherUserId = -1;
        otherUserName = "";
        otherUserAvatar = "";
        currentUserBlockedOther = false;
        otherUserBlockedCurrent = false;
        directIsFriend = !ChatRoom.TYPE_DIRECT.equals(type) || data.isFriend();
        directIsMessageRequest = ChatRoom.TYPE_DIRECT.equals(type) && data.isMessageRequest();
        if (ChatRoom.TYPE_DIRECT.equals(type)) {
            currentUserBlockedOther = data.isBlockedByMe();
            otherUserBlockedCurrent = data.hasBlockedMe();
        }
        if (data.getOtherUserId() > 0) {
            otherUserId = data.getOtherUserId();
            otherUserName = data.getOtherUserName() != null ? data.getOtherUserName() : "";
            otherUserAvatar = data.getOtherUserAvatarUrl() != null ? data.getOtherUserAvatarUrl() : "";
        }
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
                    // Lưu block status per member cho group chat
                    if (m.isBlockedByMe()) memberIdBlockedByMe.put(m.getId(), true);
                    if (m.hasBlockedMe()) memberIdHasBlockedMe.put(m.getId(), true);
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
        chatRoom.setFriend(directIsFriend);
        chatRoom.setMessageRequest(directIsMessageRequest);
        chatRoom.setOtherUserId(otherUserId);
        chatRoom.setOtherUserName(otherUserName);
        chatRoom.setOtherUserAvatarUrl(otherUserAvatar);
        chatRoom.setBlockedByMe(currentUserBlockedOther);
        chatRoom.setHasBlockedMe(otherUserBlockedCurrent);
        chatRoom.setBlockedBetweenUsers(currentUserBlockedOther || otherUserBlockedCurrent);
        chatRoom.setOtherUserOnline(data.isOtherUserOnline());
        chatRoom.setOtherUserLastActiveMins(data.getOtherUserLastActiveMins());

        // For DM: set the other participant's avatar so the header shows a real photo
        if (ChatRoom.TYPE_DIRECT.equals(type) && otherUserAvatar != null && !otherUserAvatar.isEmpty()) {
            String mUrl = otherUserAvatar;
            if (mUrl.startsWith("/")) mUrl = RetrofitClient.getBaseUrl() + mUrl.substring(1);
            chatRoom.setAvatarUrl(mUrl);
        }
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

        if (isDirectRoom()) {
            applyActivityStatus();
        } else {
            // Group/Activity/FriendGroup: hiển thị số thành viên, không có active status
            viewOnlineDot.setVisibility(View.GONE);
            int currentCount = room.getMembers().size();
            int maxMem = room.getMaxMembers();
            String memberText;
            if (maxMem > 0) {
                memberText = currentCount + " / " + maxMem;
            } else if (currentCount > 0) {
                memberText = currentCount + " thành viên";
            } else {
                memberText = null;
            }
            if (memberText != null) {
                tvConversationStatus.setText(memberText);
                tvConversationStatus.setTextColor(0xFF9E8E82);
                tvConversationStatus.setVisibility(View.VISIBLE);
            } else {
                tvConversationStatus.setVisibility(View.GONE);
            }
        }

        if (isDirectRoom()) {
            ivChatSettings.setVisibility(View.VISIBLE);
            ivChatSettings.setImageResource(R.drawable.ic_info_circle);
            ivChatSettings.setContentDescription("Thông tin");

            ivConversationAvatar.setClickable(true);
            ivConversationAvatar.setFocusable(true);
            ivConversationAvatar.setOnClickListener(v -> showDirectChatUserActionSheet());

            tvConversationTitle.setClickable(true);
            tvConversationTitle.setFocusable(true);
            tvConversationTitle.setOnClickListener(v -> showDirectChatUserActionSheet());
        } else {
            ivChatSettings.setVisibility(View.VISIBLE);
            ivChatSettings.setImageResource(R.drawable.ic_info_circle);
            ivChatSettings.setContentDescription("Thông tin nhóm");

            ivConversationAvatar.setClickable(false);
            ivConversationAvatar.setFocusable(false);
            ivConversationAvatar.setOnClickListener(null);

            tvConversationTitle.setClickable(false);
            tvConversationTitle.setFocusable(false);
            tvConversationTitle.setOnClickListener(null);
        }

        applyNonFriendInfoArea();
    }

    private void applyNonFriendInfoArea() {
        if (room == null) return;
        boolean show = isDirectRoom() && !directIsFriend
                && !currentUserBlockedOther && !otherUserBlockedCurrent;
        // Card cố định trong layout luôn ẩn; card scrollable được quản lý bởi adapter
        nonFriendInfoCard.setVisibility(View.GONE);
        String displayName = otherUserName != null && !otherUserName.isEmpty()
                ? otherUserName : room.getTitle();
        String avatarUrl = otherUserAvatar != null && !otherUserAvatar.isEmpty()
                ? otherUserAvatar : room.getAvatarUrl();
        adapter.setFriendCard(show, directFriendStatus, displayName, avatarUrl);
    }

    private void showMemberManagementDialog() {
        if (room == null) return;
        boolean isOwner = amIOwner();
        boolean isFriendGroup = ChatRoom.TYPE_FRIEND_GROUP.equals(room.getType());
        List<String> members = room.getMembers();
        List<String> pending = room.getPendingMembers();
        long myId = RetrofitClient.getUserId(this);

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
            Long memberId = memberNameToId.get(memberName);
            long mId = memberId != null ? memberId : -1L;
            boolean memberIsOwner = mId > 0 && mId == backendOwnerId;
            boolean iBlockedMember = mId > 0 && Boolean.TRUE.equals(memberIdBlockedByMe.get(mId));
            boolean memberBlockedMe = mId > 0 && Boolean.TRUE.equals(memberIdHasBlockedMe.get(mId));
            group1.addView(buildMemberRow(memberName, mId,
                    memberIsOwner, isOwner, myId, iBlockedMember, memberBlockedMe, sheet));
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

    private View buildMemberRow(String memberName, long memberId,
                                boolean memberIsOwner, boolean currentUserIsOwner,
                                long myId, boolean iBlockedMember, boolean memberBlockedMe,
                                BottomSheetDialog parentSheet) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dpPx(16), dpPx(10), dpPx(8), dpPx(10));

        // ── Avatar 44dp ──────────────────────────────────────────────────
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

        // Case B: member chặn tôi → chỉ dùng placeholder, không load ảnh thật
        if (!memberBlockedMe) {
            String cachedAvatar = memberId > 0 ? RetrofitClient.getCachedAvatarForUser(memberId) : null;
            if (cachedAvatar == null) cachedAvatar = memberNameToAvatar.get(memberName);
            if (cachedAvatar != null && !cachedAvatar.isEmpty()) {
                String url = cachedAvatar.startsWith("/")
                        ? RetrofitClient.getBaseUrl() + cachedAvatar.substring(1) : cachedAvatar;
                com.bumptech.glide.Glide.with(this).load(url)
                        .placeholder(R.drawable.ic_user_placeholder)
                        .circleCrop().into(ivAvatar);
            }
        }
        row.addView(ivAvatar);

        // ── Tên + role ───────────────────────────────────────────────────
        LinearLayout nameCol = new LinearLayout(this);
        nameCol.setOrientation(LinearLayout.VERTICAL);
        nameCol.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams nameColLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        nameColLp.leftMargin = dpPx(12);
        nameCol.setLayoutParams(nameColLp);

        TextView tvName = new TextView(this);
        if (memberBlockedMe) {
            // Case B: ẩn danh tính
            tvName.setText("Người dùng không khả dụng");
            tvName.setTextColor(0xFF8E8E93);
            tvName.setTypeface(null, android.graphics.Typeface.ITALIC);
        } else {
            tvName.setText(memberName);
            tvName.setTextColor(0xFF1C1C1E);
            tvName.setTypeface(null, memberIsOwner ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        }
        tvName.setTextSize(16);
        nameCol.addView(tvName);

        if (!memberBlockedMe && memberIsOwner) {
            TextView tvRole = new TextView(this);
            tvRole.setText("Chủ phòng");
            tvRole.setTextSize(12);
            tvRole.setTextColor(0xFF8E8E93);
            nameCol.addView(tvRole);
        }
        // Case A: tôi đã chặn member → hiện nhãn cảnh báo
        if (iBlockedMember && !memberBlockedMe) {
            TextView tvBlockedLabel = new TextView(this);
            tvBlockedLabel.setText("Người dùng đã bị chặn");
            tvBlockedLabel.setTextSize(12);
            tvBlockedLabel.setTextColor(0xFFFF3B30);
            nameCol.addView(tvBlockedLabel);
        }
        row.addView(nameCol);

        // ── Icon 3 chấm ──────────────────────────────────────────────────
        ImageView ivMore = new ImageView(this);
        int iconSize = dpPx(36);
        LinearLayout.LayoutParams moreLp = new LinearLayout.LayoutParams(iconSize, iconSize);
        ivMore.setLayoutParams(moreLp);
        ivMore.setImageResource(R.drawable.ic_more);
        ivMore.setColorFilter(0xFF8E8E93);
        ivMore.setPadding(dpPx(8), dpPx(8), dpPx(8), dpPx(8));
        android.util.TypedValue tvAttr = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tvAttr, true);
        ivMore.setBackgroundResource(tvAttr.resourceId);
        ivMore.setClickable(true);
        ivMore.setFocusable(true);
        long finalMemberId = memberId;
        ivMore.setOnClickListener(v -> {
            parentSheet.dismiss();
            showMemberActionSheet(memberName, finalMemberId, memberIsOwner,
                    currentUserIsOwner, myId, iBlockedMember, memberBlockedMe);
        });
        row.addView(ivMore);

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
        if (isDirectRoom() && (currentUserBlockedOther || otherUserBlockedCurrent)) {
            Toast.makeText(this, "Bạn không thể nhắn tin cho người này.", Toast.LENGTH_SHORT).show();
            return;
        }

        String content = etMessageInput.getText() != null
                ? etMessageInput.getText().toString().trim() : "";
        if (TextUtils.isEmpty(content)) return;

        etMessageInput.setText("");
        WebSocketManager.getInstance().sendMessage(backendRoomId, content);
    }

    private void onNewMessage(ChatMessageApiResponse msg) {
        long currentUserId = RetrofitClient.getUserId(this);
        boolean isSystem = msg.isSystemMessage();
        boolean sentByMe = !isSystem && msg.getSenderId() == currentUserId;
        String time = "";
        if (!isSystem && msg.getCreatedAt() != null) {
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
                sentByMe,
                isSystem
        );

        // Kiểm tra trùng lặp (tránh duplicate khi load history và WS cùng lúc)
        List<ChatMessage> current = adapter.getMessages();
        for (ChatMessage existing : current) {
            if (String.valueOf(msg.getId()).equals(existing.getId())) return;
        }

        List<ChatMessage> updated = new ArrayList<>(current);
        updated.add(chatMsg);
        adapter.submitList(updated);
        rvMessages.scrollToPosition(adapter.getItemCount() - 1);
        markCurrentRoomAsRead();
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
                        boolean isSystem = msgData.isSystemMessage();
                        messages.add(new ChatMessage(id, msgData.getSenderId(), sender, msgContent, time, sentByMe, isSystem));
                    }
                    adapter.submitList(messages);
                    if (!messages.isEmpty()) {
                        rvMessages.scrollToPosition(adapter.getItemCount() - 1);
                    }
                    markCurrentRoomAsRead();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<com.example.weconnect.models.ChatMessageApiResponse>>> call, Throwable t) {
                // Silent fail for message loading
            }
        });
    }

    private void markCurrentRoomAsRead() {
        if (backendRoomId <= 0) return;
        RetrofitClient.loadToken(this);
        chatApi.markRoomAsRead(backendRoomId).enqueue(new Callback<ApiResponse<Void>>() {
            @Override public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {}
            @Override public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {}
        });
    }

    private void refreshMessages() {
        if (backendRoomId > 0) {
            loadMessagesFromApi();
        } else if (room != null) {
            adapter.submitList(room.getMessages());
            if (!room.getMessages().isEmpty()) {
                rvMessages.scrollToPosition(adapter.getItemCount() - 1);
            }
        }
    }

    /**
     * Hiển thị trạng thái hoạt động của người đối diện trong direct room.
     * Chỉ hiển thị khi đủ điều kiện: bạn bè, không chặn, trạng thái hợp lệ.
     */
    private void applyActivityStatus() {
        if (!isDirectRoom() || room == null) {
            viewOnlineDot.setVisibility(View.GONE);
            tvConversationStatus.setVisibility(View.GONE);
            return;
        }

        boolean canShow = "FRIEND".equals(directFriendStatus)
                && !currentUserBlockedOther
                && !otherUserBlockedCurrent;

        if (!canShow) {
            viewOnlineDot.setVisibility(View.GONE);
            tvConversationStatus.setVisibility(View.GONE);
            return;
        }

        if (room.isOtherUserOnline()) {
            viewOnlineDot.setVisibility(View.VISIBLE);
            tvConversationStatus.setText("Đang hoạt động");
            tvConversationStatus.setTextColor(0xFF66BB6A);
            tvConversationStatus.setVisibility(View.VISIBLE);
        } else if (room.getOtherUserLastActiveMins() != null) {
            viewOnlineDot.setVisibility(View.GONE);
            tvConversationStatus.setText(formatLastActiveText(room.getOtherUserLastActiveMins()));
            tvConversationStatus.setTextColor(0xFF9E8E82);
            tvConversationStatus.setVisibility(View.VISIBLE);
        } else {
            viewOnlineDot.setVisibility(View.GONE);
            tvConversationStatus.setVisibility(View.GONE);
        }
    }

    private String formatLastActiveText(long mins) {
        if (mins < 60) {
            long m = Math.max(1, mins);
            return "Đã hoạt động " + m + " phút trước";
        }
        return "Đã hoạt động " + (mins / 60) + " giờ trước";
    }
}
