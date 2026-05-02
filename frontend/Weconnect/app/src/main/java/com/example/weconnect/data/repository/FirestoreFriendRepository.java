package com.example.weconnect.data.repository;

import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FirestoreFriendRepository — thay thế FriendApiService + FriendService backend.
 * Quản lý friendships collection trong Firestore.
 *
 * Cấu trúc: friendships/{id}
 *   ├── user1Id, user2Id (2 người liên quan)
 *   ├── requesterId (người gửi request)
 *   ├── status: PENDING | ACCEPTED | REJECTED
 *   └── createdAt
 */
public class FirestoreFriendRepository {

    private static final String COL_FRIENDSHIPS  = "friendships";
    private static final String COL_NOTIFICATIONS = "notifications";

    private final FirebaseFirestore db;

    public FirestoreFriendRepository() {
        this.db = FirebaseManager.getFirestore();
    }

    public interface FriendsCallback {
        void onSuccess(List<Map<String, Object>> friendships);
        void onError(String error);
    }

    public interface ActionCallback {
        void onSuccess(String message);
        void onError(String error);
    }

    public interface StatusCallback {
        void onResult(String status); // "NONE" | "PENDING_SENT" | "PENDING_RECEIVED" | "ACCEPTED"
    }

    // ======================================================================
    // SEND Friend Request
    // ======================================================================

    public void sendFriendRequest(String fromId, String fromName, String toId, ActionCallback callback) {
        // Kiểm tra đã có friendship chưa
        findFriendship(fromId, toId, doc -> {
            if (doc != null) {
                callback.onError("Bạn đã gửi yêu cầu hoặc đã là bạn bè rồi");
                return;
            }
            // Tạo friendship mới
            Map<String, Object> data = new HashMap<>();
            data.put("user1Id", fromId);
            data.put("user2Id", toId);
            data.put("requesterId", fromId);
            data.put("status", "PENDING");
            data.put("createdAt", FieldValue.serverTimestamp());

            db.collection(COL_FRIENDSHIPS).add(data)
                .addOnSuccessListener(docRef -> {
                    // Gửi notification
                    sendFriendRequestNotification(fromId, fromName, toId);
                    callback.onSuccess("Đã gửi lời mời kết bạn!");
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
        });
    }

    // ======================================================================
    // ACCEPT Friend Request
    // ======================================================================

    public void acceptFriendRequest(String friendshipId, String acceptorId, ActionCallback callback) {
        db.collection(COL_FRIENDSHIPS).document(friendshipId)
            .get()
            .addOnSuccessListener(doc -> {
                if (!doc.exists()) { callback.onError("Không tìm thấy lời mời kết bạn"); return; }

                String requesterId = doc.getString("requesterId");
                String user1Id = doc.getString("user1Id");
                String user2Id = doc.getString("user2Id");

                // Chỉ người nhận mới được accept
                String receiverId = requesterId.equals(user1Id) ? user2Id : user1Id;
                if (!acceptorId.equals(receiverId)) {
                    callback.onError("Bạn không có quyền chấp nhận yêu cầu này");
                    return;
                }

                doc.getReference().update("status", "ACCEPTED")
                    .addOnSuccessListener(v -> {
                        // Gửi notification cho người gửi
                        sendFriendAcceptedNotification(acceptorId, requesterId);
                        callback.onSuccess("Đã chấp nhận lời mời kết bạn!");
                    })
                    .addOnFailureListener(e -> callback.onError(e.getMessage()));
            })
            .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public void acceptRequestByUsers(String senderId, String receiverId, ActionCallback callback) {
        findFriendship(senderId, receiverId, docId -> {
            if (docId == null) {
                callback.onError("Không tìm thấy lời mời kết bạn");
                return;
            }
            acceptFriendRequest(docId, receiverId, callback);
        });
    }

    // ======================================================================
    // REJECT / CANCEL Friend Request
    // ======================================================================

    public void rejectOrCancelRequest(String friendshipId, ActionCallback callback) {
        db.collection(COL_FRIENDSHIPS).document(friendshipId)
            .delete()
            .addOnSuccessListener(v -> callback.onSuccess("Đã từ chối/hủy lời mời"))
            .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public void declineRequestByUsers(String senderId, String receiverId, ActionCallback callback) {
        findFriendship(senderId, receiverId, docId -> {
            if (docId == null) {
                callback.onError("Không tìm thấy lời mời kết bạn");
                return;
            }
            rejectOrCancelRequest(docId, callback);
        });
    }

    // ======================================================================
    // UNFRIEND
    // ======================================================================

    public void unfriend(String userId, String otherId, ActionCallback callback) {
        findFriendship(userId, otherId, doc -> {
            if (doc == null) { callback.onError("Không tìm thấy quan hệ bạn bè"); return; }
            db.collection(COL_FRIENDSHIPS).document(doc)
                .delete()
                .addOnSuccessListener(v -> callback.onSuccess("Đã hủy kết bạn"))
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
        });
    }

    // ======================================================================
    // GET Friends List (ACCEPTED)
    // ======================================================================

    public void getFriends(String userId, FriendsCallback callback) {
        // Lấy tất cả friendships mà userId là user1Id HOẶC user2Id và status=ACCEPTED
        db.collection(COL_FRIENDSHIPS)
            .whereEqualTo("user1Id", userId)
            .whereEqualTo("status", "ACCEPTED")
            .get()
            .addOnSuccessListener(snaps1 -> {
                List<Map<String, Object>> friends = new ArrayList<>();
                for (QueryDocumentSnapshot doc : snaps1) {
                    Map<String, Object> f = doc.getData();
                    f.put("id", doc.getId());
                    friends.add(f);
                }

                db.collection(COL_FRIENDSHIPS)
                    .whereEqualTo("user2Id", userId)
                    .whereEqualTo("status", "ACCEPTED")
                    .get()
                    .addOnSuccessListener(snaps2 -> {
                        for (QueryDocumentSnapshot doc : snaps2) {
                            Map<String, Object> f = doc.getData();
                            f.put("id", doc.getId());
                            friends.add(f);
                        }
                        callback.onSuccess(friends);
                    })
                    .addOnFailureListener(e -> callback.onSuccess(friends)); // partial ok
            })
            .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // ======================================================================
    // GET Pending Requests (nhận được)
    // ======================================================================

    public void getPendingRequests(String userId, FriendsCallback callback) {
        // Yêu cầu gửi đến userId (userId KHÔNG phải requester)
        db.collection(COL_FRIENDSHIPS)
            .whereEqualTo("user2Id", userId)
            .whereEqualTo("status", "PENDING")
            .get()
            .addOnSuccessListener(snaps -> {
                List<Map<String, Object>> pending = new ArrayList<>();
                for (QueryDocumentSnapshot doc : snaps) {
                    String requesterId = doc.getString("requesterId");
                    if (!userId.equals(requesterId)) { // userId là người nhận
                        Map<String, Object> f = doc.getData();
                        f.put("id", doc.getId());
                        pending.add(f);
                    }
                }
                // Cũng check user1Id
                db.collection(COL_FRIENDSHIPS)
                    .whereEqualTo("user1Id", userId)
                    .whereEqualTo("status", "PENDING")
                    .get()
                    .addOnSuccessListener(snaps2 -> {
                        for (QueryDocumentSnapshot doc : snaps2) {
                            String requesterId = doc.getString("requesterId");
                            if (!userId.equals(requesterId)) {
                                Map<String, Object> f = doc.getData();
                                f.put("id", doc.getId());
                                pending.add(f);
                            }
                        }
                        callback.onSuccess(pending);
                    })
                    .addOnFailureListener(e -> callback.onSuccess(pending));
            })
            .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // ======================================================================
    // CHECK Friendship Status
    // ======================================================================

    public void getFriendshipStatus(String myId, String otherId, StatusCallback callback) {
        db.collection(COL_FRIENDSHIPS)
            .whereEqualTo("user1Id", myId).whereEqualTo("user2Id", otherId)
            .limit(1).get()
            .addOnSuccessListener(snaps1 -> {
                if (!snaps1.isEmpty()) {
                    resolveStatus(snaps1.getDocuments().get(0), myId, callback);
                    return;
                }
                db.collection(COL_FRIENDSHIPS)
                    .whereEqualTo("user1Id", otherId).whereEqualTo("user2Id", myId)
                    .limit(1).get()
                    .addOnSuccessListener(snaps2 -> {
                        if (!snaps2.isEmpty()) {
                            resolveStatus(snaps2.getDocuments().get(0), myId, callback);
                        } else {
                            callback.onResult("NONE");
                        }
                    })
                    .addOnFailureListener(e -> callback.onResult("NONE"));
            })
            .addOnFailureListener(e -> callback.onResult("NONE"));
    }

    private void resolveStatus(com.google.firebase.firestore.DocumentSnapshot doc,
                                String myId, StatusCallback callback) {
        String status = doc.getString("status");
        if ("ACCEPTED".equals(status)) { callback.onResult("ACCEPTED"); return; }
        if ("PENDING".equals(status)) {
            String requesterId = doc.getString("requesterId");
            callback.onResult(myId.equals(requesterId) ? "PENDING_SENT" : "PENDING_RECEIVED");
        }
    }

    // ======================================================================
    // Private helpers
    // ======================================================================

    interface FindCallback { void onResult(String docId); }

    private void findFriendship(String user1, String user2, FindCallback callback) {
        db.collection(COL_FRIENDSHIPS)
            .whereEqualTo("user1Id", user1).whereEqualTo("user2Id", user2)
            .limit(1).get()
            .addOnSuccessListener(s1 -> {
                if (!s1.isEmpty()) { callback.onResult(s1.getDocuments().get(0).getId()); return; }
                db.collection(COL_FRIENDSHIPS)
                    .whereEqualTo("user1Id", user2).whereEqualTo("user2Id", user1)
                    .limit(1).get()
                    .addOnSuccessListener(s2 -> {
                        if (!s2.isEmpty()) callback.onResult(s2.getDocuments().get(0).getId());
                        else callback.onResult(null);
                    })
                    .addOnFailureListener(e -> callback.onResult(null));
            })
            .addOnFailureListener(e -> callback.onResult(null));
    }

    private void sendFriendRequestNotification(String fromId, String fromName, String toId) {
        Map<String, Object> notif = new HashMap<>();
        notif.put("userId", toId);
        notif.put("type", "FRIEND_REQUEST");
        notif.put("message", fromName + " đã gửi lời mời kết bạn với bạn.");
        notif.put("actorName", fromName);
        notif.put("actorId", fromId);
        notif.put("read", false);
        notif.put("createdAt", FieldValue.serverTimestamp());
        db.collection(COL_NOTIFICATIONS).add(notif);
    }

    private void sendFriendAcceptedNotification(String acceptorId, String requesterId) {
        db.collection("users").document(acceptorId).get()
            .addOnSuccessListener(doc -> {
                String acceptorName = doc.exists() ? doc.getString("fullName") : "Người dùng";
                Map<String, Object> notif = new HashMap<>();
                notif.put("userId", requesterId);
                notif.put("type", "FRIEND_ACCEPTED");
                notif.put("message", acceptorName + " đã chấp nhận lời mời kết bạn của bạn.");
                notif.put("actorName", acceptorName);
                notif.put("actorId", acceptorId);
                notif.put("read", false);
                notif.put("createdAt", FieldValue.serverTimestamp());
                db.collection(COL_NOTIFICATIONS).add(notif);
            });
    }
}
