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
import com.example.weconnect.activities.NotificationsActivity;
import com.example.weconnect.activities.ReportPenaltyDetailActivity;
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
        String type = data.get("type");
        String relatedReportId = data.get("relatedReportId");

        if (("REPORT_PENALTY".equals(type) || "REPORT_CONFIRMED".equals(type))
                && relatedReportId != null) {
            showNotificationWithReportDeeplink(title, body, relatedReportId);
        } else {
            showNotification(title, body);
        }
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

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent);

        NotificationManagerCompat manager = NotificationManagerCompat.from(this);
        manager.notify(requestCode, builder.build());
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

    private void showNotification(String title, String body) {
        createNotificationChannel();

        Intent intent = new Intent(this, NotificationsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent);

        NotificationManagerCompat manager = NotificationManagerCompat.from(this);
        manager.notify((int) System.currentTimeMillis(), builder.build());
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
