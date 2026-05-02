package com.example.weconnect.data.repository;

import android.util.Log;

import com.google.firebase.firestore.DocumentChange;
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
 * FirestoreChatRepository — thay thế ChatApiService + ChatService backend.
 *
 * ĐIỂM KHÁC BIỆT LỚN so với cũ:
 * - Cũ: HTTP polling mỗi vài giây để lấy tin nhắn mới.
 * - Mới: Firestore addSnapshotListener → tin nhắn REAL-TIME ngay lập tức.
 *
 * Cấu trúc Firestore:
 *   chatRooms/{roomId}
 *     ├── title, type, ownerId, postId, memberIds[], active, createdAt
 *     └── messages/{msgId}
 *           ├── senderId, senderName, content, createdAt
 */
public class FirestoreChatRepository {

    private static final String TAG = "ChatRepo";
    private static final String COL_ROOMS    = "chatRooms";
    private static final String COL_MESSAGES = "messages";
    private static final String COL_USERS    = "users";

    private final FirebaseFirestore db;

    public FirestoreChatRepository() {
        this.db = FirebaseManager.getFirestore();
    }

    // ======================================================================
    // Callback interfaces
    // ======================================================================

    public interface RoomsCallback {
        void onSuccess(List<Map<String, Object>> rooms);
        void onError(String error);
    }

    public interface RoomCallback {
        void onSuccess(Map<String, Object> room);
        void onError(String error);
    }

    public interface MessagesCallback {
        void onSuccess(List<Map<String, Object>> messages);
        void onError(String error);
    }

    public interface MessageCallback {
        void onSuccess(Map<String, Object> message);
        void onError(String error);
    }

    public interface ActionCallback {
        void onSuccess(String roomId);
        void onError(String error);
    }

    // ======================================================================
    // GET User Chat Rooms
    // ======================================================================

    /**
     * Lấy danh sách phòng chat của user (user có trong memberIds).
     */
    public void getUserRooms(String userId, RoomsCallback callback) {
        db.collection(COL_ROOMS)
            .whereArrayContains("memberIds", userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener(snaps -> {
                List<Map<String, Object>> rooms = new ArrayList<>();
                for (QueryDocumentSnapshot doc : snaps) {
                    Map<String, Object> room = doc.getData();
                    room.put("id", doc.getId());
                    rooms.add(room);
                }
                callback.onSuccess(rooms);
            })
            .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // ======================================================================
    // GET or CREATE Direct Room (DM)
    // ======================================================================

    /**
     * Tìm hoặc tạo phòng chat trực tiếp (DM) giữa 2 user.
     * Type = "direct"
     */
    public void getOrCreateDirectRoom(String myId, String myName,
                                       String otherId, String otherName,
                                       ActionCallback callback) {
        // Tìm room direct đã tồn tại giữa 2 người
        db.collection(COL_ROOMS)
            .whereEqualTo("type", "direct")
            .whereArrayContains("memberIds", myId)
            .get()
            .addOnSuccessListener(snaps -> {
                for (QueryDocumentSnapshot doc : snaps) {
                    List<String> memberIds = (List<String>) doc.get("memberIds");
                    if (memberIds != null && memberIds.contains(otherId)) {
                        // Tìm thấy room cũ
                        callback.onSuccess(doc.getId());
                        return;
                    }
                }
                // Chưa có → tạo mới
                Map<String, Object> room = new HashMap<>();
                List<String> members = new ArrayList<>();
                members.add(myId);
                members.add(otherId);
                room.put("title", otherName); // tên người kia hiển thị cho myId
                room.put("type", "direct");
                room.put("ownerId", myId);
                room.put("memberIds", members);
                room.put("active", true);
                room.put("createdAt", FieldValue.serverTimestamp());

                db.collection(COL_ROOMS).add(room)
                    .addOnSuccessListener(docRef -> callback.onSuccess(docRef.getId()))
                    .addOnFailureListener(e -> callback.onError(e.getMessage()));
            })
            .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // ======================================================================
    // GET Room by ID
    // ======================================================================

    public void getRoom(String roomId, RoomCallback callback) {
        db.collection(COL_ROOMS).document(roomId)
            .get()
            .addOnSuccessListener(doc -> {
                if (!doc.exists()) { callback.onError("Không tìm thấy phòng chat"); return; }
                Map<String, Object> room = doc.getData();
                room.put("id", doc.getId());
                callback.onSuccess(room);
            })
            .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // ======================================================================
    // GET Messages (lấy lịch sử)
    // ======================================================================

    public void getMessages(String roomId, MessagesCallback callback) {
        db.collection(COL_ROOMS).document(roomId)
            .collection(COL_MESSAGES)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener(snaps -> {
                List<Map<String, Object>> msgs = new ArrayList<>();
                for (QueryDocumentSnapshot doc : snaps) {
                    Map<String, Object> msg = doc.getData();
                    msg.put("id", doc.getId());
                    msgs.add(msg);
                }
                callback.onSuccess(msgs);
            })
            .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // ======================================================================
    // REALTIME Listener — thay thế HTTP polling!
    // ======================================================================

    /**
     * Lắng nghe tin nhắn mới REAL-TIME.
     * Trả về ListenerRegistration để có thể hủy khi Activity destroyed.
     *
     * Cách dùng trong ConversationActivity:
     *   ListenerRegistration reg = chatRepo.listenToNewMessages(roomId, currentUserId, messages -> {
     *       adapter.addMessages(messages);
     *       recyclerView.scrollToPosition(adapter.getItemCount() - 1);
     *   });
     *   // Trong onDestroy():
     *   reg.remove();
     */
    public ListenerRegistration listenToNewMessages(String roomId, String currentUserId,
                                                     MessagesCallback callback) {
        return db.collection(COL_ROOMS).document(roomId)
            .collection(COL_MESSAGES)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener((snaps, error) -> {
                if (error != null) {
                    callback.onError(error.getMessage());
                    return;
                }
                List<Map<String, Object>> newMsgs = new ArrayList<>();
                for (DocumentChange change : snaps.getDocumentChanges()) {
                    if (change.getType() == DocumentChange.Type.ADDED) {
                        Map<String, Object> msg = change.getDocument().getData();
                        msg.put("id", change.getDocument().getId());
                        // Đánh dấu tin nhắn của mình
                        msg.put("isMyMessage", currentUserId.equals(msg.get("senderId")));
                        newMsgs.add(msg);
                    }
                }
                if (!newMsgs.isEmpty()) {
                    callback.onSuccess(newMsgs);
                }
            });
    }

    // ======================================================================
    // SEND Message
    // ======================================================================

    public void sendMessage(String roomId, String senderId, String senderName,
                            String content, MessageCallback callback) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("senderId", senderId);
        msg.put("senderName", senderName);
        msg.put("content", content);
        msg.put("createdAt", FieldValue.serverTimestamp());

        db.collection(COL_ROOMS).document(roomId)
            .collection(COL_MESSAGES)
            .add(msg)
            .addOnSuccessListener(docRef -> {
                msg.put("id", docRef.getId());
                callback.onSuccess(msg);
            })
            .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // ======================================================================
    // CREATE Group Room (nhóm bạn bè)
    // ======================================================================

    public void createGroupRoom(String ownerId, String title, List<String> memberIds,
                                 ActionCallback callback) {
        List<String> allMembers = new ArrayList<>(memberIds);
        if (!allMembers.contains(ownerId)) allMembers.add(0, ownerId);

        Map<String, Object> room = new HashMap<>();
        room.put("title", title);
        room.put("type", "friend_group");
        room.put("ownerId", ownerId);
        room.put("memberIds", allMembers);
        room.put("active", true);
        room.put("createdAt", FieldValue.serverTimestamp());

        db.collection(COL_ROOMS).add(room)
            .addOnSuccessListener(docRef -> callback.onSuccess(docRef.getId()))
            .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // ======================================================================
    // GET Room by PostId (Activity Chat)
    // ======================================================================

    public void getRoomByPostId(String postId, RoomCallback callback) {
        db.collection(COL_ROOMS)
            .whereEqualTo("postId", postId)
            .limit(1)
            .get()
            .addOnSuccessListener(snaps -> {
                if (snaps.isEmpty()) {
                    callback.onError("Không tìm thấy phòng chat cho hoạt động này");
                    return;
                }
                QueryDocumentSnapshot doc = (QueryDocumentSnapshot) snaps.getDocuments().get(0);
                Map<String, Object> room = doc.getData();
                room.put("id", doc.getId());
                callback.onSuccess(room);
            })
            .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // ======================================================================
    // DELETE Room
    // ======================================================================

    public void deleteRoom(String roomId, String userId, ActionCallback callback) {
        db.collection(COL_ROOMS).document(roomId)
            .get()
            .addOnSuccessListener(doc -> {
                if (!doc.exists()) { callback.onError("Không tìm thấy phòng chat"); return; }
                if (!userId.equals(doc.get("ownerId"))) {
                    callback.onError("Chỉ chủ phòng mới có quyền xóa");
                    return;
                }
                // Xóa tất cả messages trước
                db.collection(COL_ROOMS).document(roomId)
                    .collection(COL_MESSAGES).get()
                    .addOnSuccessListener(msgSnaps -> {
                        for (com.google.firebase.firestore.DocumentSnapshot m : msgSnaps) m.getReference().delete();
                        doc.getReference().delete()
                            .addOnSuccessListener(v -> callback.onSuccess(roomId))
                            .addOnFailureListener(e -> callback.onError(e.getMessage()));
                    });
            })
            .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // ======================================================================
    // LEAVE Room
    // ======================================================================

    public void leaveRoom(String roomId, String userId, ActionCallback callback) {
        db.collection(COL_ROOMS).document(roomId)
            .update("memberIds", FieldValue.arrayRemove(userId))
            .addOnSuccessListener(v -> callback.onSuccess(roomId))
            .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }
}
