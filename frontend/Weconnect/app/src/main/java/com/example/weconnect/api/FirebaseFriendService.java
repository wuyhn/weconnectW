package com.example.weconnect.api;

import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FirebaseFriendService — thay thế FriendApiService (Retrofit).
 * Quản lý kết bạn, hủy kết bạn, block user qua Firestore collection "friendRequests" và "friends".
 */
public class FirebaseFriendService {

    private static final String COL_FRIEND_REQUESTS = "friendRequests";
    private static final String COL_FRIENDS         = "friends";
    private static final String COL_BLOCKED         = "blockedUsers";

    private final FirebaseFirestore db;

    public FirebaseFriendService() {
        this.db = FirebaseManager.getFirestore();
    }

    public interface ActionCallback {
        void onSuccess(String message);
        void onError(String error);
    }

    public interface FriendStatusCallback {
        void onStatus(String status); // "NONE" | "PENDING_SENT" | "PENDING_RECEIVED" | "FRIEND"
    }

    public interface FriendCountCallback {
        void onCount(int count);
    }

    public interface FriendsCallback {
        void onSuccess(List<Map<String, Object>> friends);
        void onError(String error);
    }

    // ======================================================================
    // SEND Friend Request
    // ======================================================================

    public void sendFriendRequest(String fromUid, String toUid, ActionCallback callback) {
        String docId = fromUid + "_" + toUid;
        Map<String, Object> req = new HashMap<>();
        req.put("fromUid", fromUid);
        req.put("toUid", toUid);
        req.put("status", "PENDING");
        req.put("createdAt", FieldValue.serverTimestamp());

        db.collection(COL_FRIEND_REQUESTS).document(docId)
            .set(req)
            .addOnSuccessListener(v -> callback.onSuccess("Đã gửi lời mời kết bạn"))
            .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // ======================================================================
    // CANCEL Friend Request
    // ======================================================================

    public void cancelFriendRequest(String fromUid, String toUid, ActionCallback callback) {
        String docId = fromUid + "_" + toUid;
        db.collection(COL_FRIEND_REQUESTS).document(docId)
            .delete()
            .addOnSuccessListener(v -> callback.onSuccess("Đã hủy lời mời"))
            .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // ======================================================================
    // ACCEPT Friend Request
    // ======================================================================

    public void acceptFriendRequest(String fromUid, String toUid, ActionCallback callback) {
        String docId = fromUid + "_" + toUid;
        // Update request status → ACCEPTED
        db.collection(COL_FRIEND_REQUESTS).document(docId)
            .update("status", "ACCEPTED")
            .addOnSuccessListener(v -> {
                // Thêm vào collection friends (cả 2 chiều)
                addFriendBidirectional(fromUid, toUid);
                callback.onSuccess("Đã chấp nhận kết bạn");
            })
            .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // ======================================================================
    // DECLINE Friend Request
    // ======================================================================

    public void declineFriendRequest(String fromUid, String toUid, ActionCallback callback) {
        String docId = fromUid + "_" + toUid;
        db.collection(COL_FRIEND_REQUESTS).document(docId)
            .delete()
            .addOnSuccessListener(v -> callback.onSuccess("Đã từ chối lời mời"))
            .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // ======================================================================
    // UNFRIEND
    // ======================================================================

    public void unfriend(String uid1, String uid2, ActionCallback callback) {
        String docId1 = uid1 + "_" + uid2;
        String docId2 = uid2 + "_" + uid1;
        db.collection(COL_FRIENDS).document(docId1).delete();
        db.collection(COL_FRIENDS).document(docId2).delete();
        // Xóa request nếu còn
        db.collection(COL_FRIEND_REQUESTS).document(docId1).delete();
        db.collection(COL_FRIEND_REQUESTS).document(docId2).delete();
        callback.onSuccess("Đã hủy kết bạn");
    }

    // ======================================================================
    // BLOCK User
    // ======================================================================

    public void blockUser(String blockerId, String blockedId, ActionCallback callback) {
        String docId = blockerId + "_" + blockedId;
        Map<String, Object> data = new HashMap<>();
        data.put("blockerId", blockerId);
        data.put("blockedId", blockedId);
        data.put("createdAt", FieldValue.serverTimestamp());
        db.collection(COL_BLOCKED).document(docId)
            .set(data)
            .addOnSuccessListener(v -> {
                // Xóa kết bạn nếu có
                unfriend(blockerId, blockedId, new ActionCallback() {
                    @Override public void onSuccess(String m) {}
                    @Override public void onError(String e) {}
                });
                callback.onSuccess("Đã chặn người dùng");
            })
            .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // ======================================================================
    // GET Friend Status
    // ======================================================================

    public void getFriendStatus(String myUid, String otherUid, FriendStatusCallback callback) {
        // Check if friends
        String friendDocId = myUid + "_" + otherUid;
        db.collection(COL_FRIENDS).document(friendDocId).get()
            .addOnSuccessListener(doc -> {
                if (doc.exists()) { callback.onStatus("FRIEND"); return; }

                // Check if pending sent
                db.collection(COL_FRIEND_REQUESTS).document(myUid + "_" + otherUid).get()
                    .addOnSuccessListener(reqDoc -> {
                        if (reqDoc.exists()) { callback.onStatus("PENDING_SENT"); return; }

                        // Check if pending received
                        db.collection(COL_FRIEND_REQUESTS).document(otherUid + "_" + myUid).get()
                            .addOnSuccessListener(revDoc -> {
                                if (revDoc.exists()) { callback.onStatus("PENDING_RECEIVED"); return; }
                                callback.onStatus("NONE");
                            })
                            .addOnFailureListener(e -> callback.onStatus("NONE"));
                    })
                    .addOnFailureListener(e -> callback.onStatus("NONE"));
            })
            .addOnFailureListener(e -> callback.onStatus("NONE"));
    }

    // ======================================================================
    // GET Friend Count
    // ======================================================================

    public void getFriendCount(String uid, FriendCountCallback callback) {
        db.collection(COL_FRIENDS)
            .whereEqualTo("uid1", uid)
            .get()
            .addOnSuccessListener(snaps -> callback.onCount(snaps.size()))
            .addOnFailureListener(e -> callback.onCount(0));
    }

    // ======================================================================
    // GET Friends List
    // ======================================================================

    public void getFriends(String uid, FriendsCallback callback) {
        db.collection(COL_FRIENDS)
            .whereEqualTo("uid1", uid)
            .get()
            .addOnSuccessListener(snaps -> {
                List<Map<String, Object>> friends = new ArrayList<>();
                int[] remaining = {snaps.size()};
                if (remaining[0] == 0) { callback.onSuccess(friends); return; }

                for (QueryDocumentSnapshot doc : snaps) {
                    String friendUid = doc.getString("uid2");
                    db.collection("users").document(friendUid).get()
                        .addOnSuccessListener(userDoc -> {
                            remaining[0]--;
                            if (userDoc.exists()) {
                                Map<String, Object> u = userDoc.getData();
                                u.put("id", userDoc.getId());
                                friends.add(u);
                            }
                            if (remaining[0] == 0) callback.onSuccess(friends);
                        })
                        .addOnFailureListener(e -> {
                            remaining[0]--;
                            if (remaining[0] == 0) callback.onSuccess(friends);
                        });
                }
            })
            .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // ======================================================================
    // SEND Friend Request (by name — legacy compat, prefer UID version)
    // ======================================================================

    public void sendFriendRequest(String friendName) {
        // No-op — use sendFriendRequest(fromUid, toUid, callback) instead
    }

    // ======================================================================
    // Private helpers
    // ======================================================================

    private void addFriendBidirectional(String uid1, String uid2) {
        Map<String, Object> f1 = new HashMap<>();
        f1.put("uid1", uid1);
        f1.put("uid2", uid2);
        f1.put("createdAt", FieldValue.serverTimestamp());
        db.collection(COL_FRIENDS).document(uid1 + "_" + uid2).set(f1);

        Map<String, Object> f2 = new HashMap<>();
        f2.put("uid1", uid2);
        f2.put("uid2", uid1);
        f2.put("createdAt", FieldValue.serverTimestamp());
        db.collection(COL_FRIENDS).document(uid2 + "_" + uid1).set(f2);
    }
}
