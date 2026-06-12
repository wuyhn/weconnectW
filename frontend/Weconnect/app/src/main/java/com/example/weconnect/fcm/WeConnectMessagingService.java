package com.example.weconnect.fcm;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.weconnect.R;
import com.example.weconnect.activities.ConversationActivity;
import com.example.weconnect.util.BadgeManager;
import com.example.weconnect.activities.ForceLogoutActivity;
import com.example.weconnect.activities.MainActivity;
import com.example.weconnect.activities.PendingListActivity;
import com.example.weconnect.activities.ReportPenaltyDetailActivity;
import com.example.weconnect.activities.UserProfileActivity;
import com.example.weconnect.api.RetrofitClient;
import com.example.weconnect.api.UserApiService;
import com.example.weconnect.models.ApiResponse;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class WeConnectMessagingService extends FirebaseMessagingService {

    public static final String CHANNEL_ID = "weconnect_channel";

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        sendTokenToBackend(token);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        String title = "WeConnect";
        String body = "";
        if (remoteMessage.getNotification() != null) {
            if (remoteMessage.getNotification().getTitle() != null) {
                title = remoteMessage.getNotification().getTitle();
            }
            if (remoteMessage.getNotification().getBody() != null) {
                body = remoteMessage.getNotification().getBody();
            }
        }

        Map<String, String> data = remoteMessage.getData();

        // Kịch bản 1 (Real-time Kick-out): kiểm tra action=FORCE_LOGOUT trước tiên.
        // Backend gửi data-only FCM (không notification block) khi khóa tài khoản real-time.
        // onMessageReceived() luôn được gọi cho data-only message kể cả khi app background.
        String action = data.get("action");
        if ("FORCE_LOGOUT".equals(action)) {
            // Truyền cả lockUntil để ForceLogoutActivity hiển thị đúng ngày mở khóa
            handleForceLogout(data.get("message"), data.get("lockUntil"));
            return;
        }

        String type = data.get("type");
        String relatedReportId = data.get("relatedReportId");
        String relatedRoomId = data.get("relatedRoomId");
        String relatedPostId = data.get("relatedPostId");
        String relatedUserId = data.get("relatedUserId");
        String relatedUsername = data.get("relatedUsername");

        if ("NEW_CHAT_MESSAGE".equals(type)) {
            // Tin nhắn mới trong phòng chat — chỉ hiển thị notification khi app không ở foreground
            // của đúng phòng đó. ConversationActivity.currentOpenRoomId là -1 khi không mở.
            String msgRoomId = data.get("roomId");
            String preview = data.get("lastMessagePreview");
            if (msgRoomId != null) {
                long roomId = -1;
                try { roomId = Long.parseLong(msgRoomId); } catch (Exception ignored) {}
                if (roomId != ConversationActivity.currentOpenRoomId) {
                    // Tăng badge cache ngay khi FCM đến — WS có thể không kết nối khi app ở background.
                    // onResume() sẽ sync lại tổng chính xác từ API.
                    BadgeManager.incrementChat();
                    String notifBody = (preview != null && !preview.isEmpty()) ? preview : "Tin nhắn mới";
                    showNotificationWithChatDeeplink("Tin nhắn mới", notifBody, msgRoomId);
                }
            }
            return;
        }

        if (("REPORT_PENALTY".equals(type) || "REPORT_CONFIRMED".equals(type) || "ADMIN_WARNING".equals(type))
                && relatedReportId != null) {
            showNotificationWithReportDeeplink(title, body, relatedReportId);
        } else if ("CHAT_SUMMARY".equals(type) && relatedRoomId != null) {
            showNotificationWithChatDeeplink(title, body, relatedRoomId);
        } else if ("JOIN_REQUEST".equals(type) && relatedPostId != null) {
            showNotificationWithPendingListDeeplink(title, body, relatedPostId);
        } else if (("FRIEND_ACCEPTED".equals(type) || "FRIEND_REQUEST_RECEIVED".equals(type)
                || "STRANGER_REQUEST_ACCEPTED".equals(type))
                && relatedUserId != null && relatedUsername != null) {
            showNotificationWithUserDeeplink(title, body, relatedUserId, relatedUsername);
        } else if (relatedPostId != null) {
            // JOIN_APPROVED, ACTIVITY_CANCELLED, POST_EXPIRED, v.v.
            boolean isJoined = "JOIN_APPROVED".equals(type);
            showNotificationWithPostDeeplink(title, body, relatedPostId, isJoined);
        } else {
            showNotification(title, body);
        }
    }

    /**
     * Kịch bản 1 (FCM fallback): Xử lý action=FORCE_LOGOUT khi app ở background.
     * Start ForceLogoutActivity với message và lockUntil để hiển thị ngày mở khóa.
     * FLAG_ACTIVITY_NEW_TASK bắt buộc vì gọi từ Service context.
     */
    private void handleForceLogout(String message, String lockUntil) {
        Intent intent = new Intent(this, ForceLogoutActivity.class);
        intent.putExtra("lock_message", message);
        intent.putExtra("lock_until", lockUntil);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    private void showNotificationWithReportDeeplink(String title, String body, String reportIdStr) {
        createNotificationChannel();
        long reportId = -1;
        try { reportId = Long.parseLong(reportIdStr); } catch (Exception ignored) {}

        Intent intent = new Intent(this, ReportPenaltyDetailActivity.class);
        intent.putExtra("report_id", reportId);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        int requestCode = (int) (System.currentTimeMillis() & 0xffff);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, requestCode, intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        buildAndShow(title, body, requestCode, pendingIntent);
    }

    private void showNotificationWithChatDeeplink(String title, String body, String roomIdStr) {
        createNotificationChannel();
        long roomId = -1;
        try { roomId = Long.parseLong(roomIdStr); } catch (Exception ignored) {}

        Intent intent = new Intent(this, ConversationActivity.class);
        intent.putExtra("room_id", roomId);
        intent.putExtra("scroll_to_summary", true);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        int requestCode = (int) (System.currentTimeMillis() & 0xffff);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, requestCode, intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        buildAndShow(title, body, requestCode, pendingIntent);
    }

    private void showNotificationWithPendingListDeeplink(String title, String body, String postIdStr) {
        createNotificationChannel();
        long postId = -1;
        try { postId = Long.parseLong(postIdStr); } catch (Exception ignored) {}

        Intent intent = new Intent(this, PendingListActivity.class);
        intent.putExtra("post_id", postId);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        int requestCode = (int) (System.currentTimeMillis() & 0xffff);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, requestCode, intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        buildAndShow(title, body, requestCode, pendingIntent);
    }

    private void showNotificationWithUserDeeplink(String title, String body,
            String userIdStr, String username) {
        createNotificationChannel();
        long userId = -1;
        try { userId = Long.parseLong(userIdStr); } catch (Exception ignored) {}

        Intent intent = new Intent(this, UserProfileActivity.class);
        intent.putExtra("username", username);
        intent.putExtra("view_other", true);
        intent.putExtra("user_id", userId);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        int requestCode = (int) (System.currentTimeMillis() & 0xffff);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, requestCode, intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        buildAndShow(title, body, requestCode, pendingIntent);
    }

    private void showNotificationWithPostDeeplink(String title, String body,
            String postIdStr, boolean isJoined) {
        createNotificationChannel();
        long postId = -1;
        try { postId = Long.parseLong(postIdStr); } catch (Exception ignored) {}

        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("navigate_post_id", postId);
        intent.putExtra("navigate_is_joined", isJoined);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        int requestCode = (int) (System.currentTimeMillis() & 0xffff);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        buildAndShow(title, body, requestCode, pendingIntent);
    }

    private void showNotification(String title, String body) {
        createNotificationChannel();

        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("open_tab", "notifications");
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        int requestCode = (int) (System.currentTimeMillis() & 0xffff);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        buildAndShow(title, body, requestCode, pendingIntent);
    }

    private void buildAndShow(String title, String body, int notificationId,
            PendingIntent pendingIntent) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent);

        NotificationManagerCompat manager = NotificationManagerCompat.from(this);
        manager.notify(notificationId, builder.build());
    }

    private void sendTokenToBackend(String fcmToken) {
        RetrofitClient.loadToken(this);
        String jwt = RetrofitClient.getAuthToken();
        if (jwt == null) return;

        Map<String, String> body = new HashMap<>();
        body.put("fcmToken", fcmToken);

        RetrofitClient.getClient().create(UserApiService.class)
                .updateFcmToken(body)
                .enqueue(new Callback<ApiResponse<Void>>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse<Void>> call,
                                           @NonNull Response<ApiResponse<Void>> response) {}

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse<Void>> call,
                                          @NonNull Throwable t) {}
                });
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "WeConnect Notifications",
                    NotificationManager.IMPORTANCE_HIGH);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }
}
