package com.example.weconnect.api;

import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FirestoreNotificationRepository — thay thế NotificationApiService + NotificationService.
 * Đọc/ghi notifications/{id} trong Firestore.
 * Hỗ trợ realtime listener để badge count update ngay lập tức.
 */
public class FirestoreNotificationRepository {

    private static final String COL_NOTIF = "notifications";
    private final FirebaseFirestore db;

    public FirestoreNotificationRepository() {
        this.db = FirebaseManager.getFirestore();
    }

    public interface NotificationsCallback {
        void onSuccess(List<Map<String, Object>> notifications);
        void onError(String error);
    }

    public interface CountCallback {
        void onCount(int count);
    }

    public interface ActionCallback {
        void onSuccess(String message);
        void onError(String error);
    }

    // ======================================================================
    // GET Notifications
    // ======================================================================

    public void getNotifications(String userId, NotificationsCallback callback) {
        db.collection(COL_NOTIF)
            .whereEqualTo("userId", userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50)
            .get()
            .addOnSuccessListener(snaps -> {
                List<Map<String, Object>> notifs = new ArrayList<>();
                for (QueryDocumentSnapshot doc : snaps) {
                    Map<String, Object> n = doc.getData();
                    n.put("id", doc.getId());
                    notifs.add(n);
                }
                callback.onSuccess(notifs);
            })
            .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // ======================================================================
    // REALTIME Listener cho Unread Count (badge)
    // ======================================================================

    /**
     * Lắng nghe số thông báo chưa đọc REAL-TIME → cập nhật badge ngay.
     *
     * Dùng trong MainActivity:
     *   ListenerRegistration reg = notifRepo.listenUnreadCount(uid, count -> {
     *       updateBadge(count);
     *   });
     *   // onDestroy(): reg.remove();
     */
    public ListenerRegistration listenUnreadCount(String userId, CountCallback callback) {
        return db.collection(COL_NOTIF)
            .whereEqualTo("userId", userId)
            .whereEqualTo("read", false)
            .addSnapshotListener((snaps, error) -> {
                if (error != null || snaps == null) {
                    callback.onCount(0);
                    return;
                }
                callback.onCount(snaps.size());
            });
    }

    // ======================================================================
    // MARK as READ
    // ======================================================================

    public void markAsRead(String notificationId, ActionCallback callback) {
        db.collection(COL_NOTIF).document(notificationId)
            .update("read", true)
            .addOnSuccessListener(v -> callback.onSuccess("ok"))
            .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public void markAsActioned(String notificationId, ActionCallback callback) {
        db.collection(COL_NOTIF).document(notificationId)
            .update("actioned", true)
            .addOnSuccessListener(v -> callback.onSuccess("ok"))
            .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public void markAllAsRead(String userId, ActionCallback callback) {
        db.collection(COL_NOTIF)
            .whereEqualTo("userId", userId)
            .whereEqualTo("read", false)
            .get()
            .addOnSuccessListener(snaps -> {
                for (QueryDocumentSnapshot doc : snaps) {
                    doc.getReference().update("read", true);
                }
                callback.onSuccess("Đã đánh dấu tất cả là đã đọc");
            })
            .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // ======================================================================
    // DELETE Notification
    // ======================================================================

    public void deleteNotification(String notificationId, ActionCallback callback) {
        db.collection(COL_NOTIF).document(notificationId)
            .delete()
            .addOnSuccessListener(v -> callback.onSuccess("ok"))
            .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // ======================================================================
    // CREATE Notification (dùng nội bộ hoặc từ repositories khác)
    // ======================================================================

    public static void create(FirebaseFirestore db, String userId, String type,
                               String message, String actorName,
                               String postId, String actorId) {
        Map<String, Object> notif = new HashMap<>();
        notif.put("userId", userId);
        notif.put("type", type);
        notif.put("message", message);
        notif.put("actorName", actorName != null ? actorName : "");
        notif.put("postId", postId != null ? postId : "");
        notif.put("actorId", actorId != null ? actorId : "");
        notif.put("read", false);
        notif.put("createdAt", FieldValue.serverTimestamp());
        db.collection("notifications").add(notif);
    }
}
