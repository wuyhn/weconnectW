package com.example.weconnect.data.repository;

import android.util.Log;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FirestorePostRepository — thay thế PostApiService + PostService (Java backend).
 * Tất cả data đọc/ghi trực tiếp vào Firestore collection "posts".
 */
public class FirestorePostRepository {

    private static final String TAG = "PostRepo";
    private static final String COLLECTION_POSTS   = "posts";
    private static final String COLLECTION_MEMBERS = "members";

    private final FirebaseFirestore db;

    public FirestorePostRepository() {
        this.db = FirebaseManager.getFirestore();
    }

    // ======================================================================
    // Callback interfaces
    // ======================================================================

    public interface PostsCallback {
        void onSuccess(List<Map<String, Object>> posts);
        void onError(String error);
    }

    public interface PostCallback {
        void onSuccess(Map<String, Object> post);
        void onError(String error);
    }

    public interface ActionCallback {
        void onSuccess(String message);
        void onError(String error);
    }

    public interface MembersCallback {
        void onSuccess(List<Map<String, Object>> members);
        void onError(String error);
    }

    // ======================================================================
    // GET Active Posts (Home Feed)
    // ======================================================================

    /**
     * Lấy danh sách bài đăng active: archived=false và endTime > now()
     */
    public void getActivePosts(PostsCallback callback) {
        Timestamp now = Timestamp.now();
        db.collection(COLLECTION_POSTS)
            .whereEqualTo("archived", false)
            .whereGreaterThan("endTime", now)
            .orderBy("endTime")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener(snapshots -> {
                List<Map<String, Object>> posts = new ArrayList<>();
                for (QueryDocumentSnapshot doc : snapshots) {
                    Map<String, Object> post = doc.getData();
                    post.put("id", doc.getId());
                    posts.add(post);
                }
                callback.onSuccess(posts);
            })
            .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // ======================================================================
    // GET Post by ID
    // ======================================================================

    public void getPost(String postId, PostCallback callback) {
        db.collection(COLLECTION_POSTS)
            .document(postId)
            .get()
            .addOnSuccessListener(doc -> {
                if (!doc.exists()) {
                    callback.onError("Không tìm thấy bài đăng");
                    return;
                }
                Map<String, Object> post = doc.getData();
                post.put("id", doc.getId());
                callback.onSuccess(post);
            })
            .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // ======================================================================
    // CREATE Post
    // ======================================================================

    public void createPost(String authorId, String authorName, String authorAvatarUrl,
                           String content, String interestTag, String location,
                           String imageUrl, int maxMembers,
                           Timestamp startTime, Timestamp endTime,
                           ActionCallback callback) {
        Map<String, Object> postData = new HashMap<>();
        postData.put("authorId", authorId);
        postData.put("authorName", authorName);
        postData.put("authorAvatarUrl", authorAvatarUrl != null ? authorAvatarUrl : "");
        postData.put("content", content);
        postData.put("interestTag", interestTag != null ? interestTag : "");
        postData.put("location", location != null ? location : "");
        postData.put("imageUrl", imageUrl != null ? imageUrl : "");
        postData.put("maxMembers", maxMembers > 0 ? maxMembers : 10);
        postData.put("memberCount", 1); // author tính là 1 thành viên
        postData.put("archived", false);
        postData.put("expired", false);
        postData.put("startTime", startTime != null ? startTime : Timestamp.now());
        postData.put("endTime", endTime);
        postData.put("createdAt", FieldValue.serverTimestamp());

        db.collection(COLLECTION_POSTS)
            .add(postData)
            .addOnSuccessListener(docRef -> {
                // Tự động thêm author vào subcollection members với status APPROVED
                addMemberToPost(docRef.getId(), authorId, authorName, "APPROVED", new ActionCallback() {
                    @Override public void onSuccess(String msg) {
                        // Tạo chat room cho hoạt động
                        createActivityChatRoom(docRef.getId(), authorId, interestTag, authorName);
                        callback.onSuccess(docRef.getId());
                    }
                    @Override public void onError(String err) {
                        callback.onSuccess(docRef.getId()); // vẫn trả về id dù lỗi nhỏ
                    }
                });
            })
            .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public void createPost(Map<String, Object> postData, ActionCallback callback) {
        db.collection(COLLECTION_POSTS)
            .add(postData)
            .addOnSuccessListener(docRef -> {
                String authorId = (String) postData.get("authorId");
                String authorName = (String) postData.get("authorName");
                String interestTag = (String) postData.get("interestTag");
                addMemberToPost(docRef.getId(), authorId, authorName, "APPROVED", new ActionCallback() {
                    @Override public void onSuccess(String msg) {
                        createActivityChatRoom(docRef.getId(), authorId, interestTag, authorName);
                        callback.onSuccess(docRef.getId());
                    }
                    @Override public void onError(String err) {
                        callback.onSuccess(docRef.getId()); 
                    }
                });
            })
            .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // ======================================================================
    // UPDATE Post
    // ======================================================================

    public void updatePost(String postId, String currentUserId, Map<String, Object> updates,
                           ActionCallback callback) {
        db.collection(COLLECTION_POSTS).document(postId)
            .get()
            .addOnSuccessListener(doc -> {
                if (!doc.exists()) { callback.onError("Không tìm thấy bài đăng"); return; }
                String authorId = (String) doc.get("authorId");
                if (!currentUserId.equals(authorId)) {
                    callback.onError("Bạn không có quyền sửa bài đăng này");
                    return;
                }
                db.collection(COLLECTION_POSTS).document(postId)
                    .update(updates)
                    .addOnSuccessListener(v -> callback.onSuccess("Đã cập nhật bài đăng"))
                    .addOnFailureListener(e -> callback.onError(e.getMessage()));
            })
            .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // ======================================================================
    // DELETE Post
    // ======================================================================

    public void deletePost(String postId, String currentUserId, ActionCallback callback) {
        db.collection(COLLECTION_POSTS).document(postId)
            .get()
            .addOnSuccessListener(doc -> {
                if (!doc.exists()) { callback.onError("Không tìm thấy bài đăng"); return; }
                String authorId = (String) doc.get("authorId");
                if (!currentUserId.equals(authorId)) {
                    callback.onError("Bạn không có quyền xóa bài đăng này");
                    return;
                }
                // Xóa subcollection members trước
                db.collection(COLLECTION_POSTS).document(postId)
                    .collection(COLLECTION_MEMBERS)
                    .get()
                    .addOnSuccessListener(memberSnaps -> {
                        for (DocumentSnapshot m : memberSnaps) m.getReference().delete();
                        // Xóa post doc
                        db.collection(COLLECTION_POSTS).document(postId)
                            .delete()
                            .addOnSuccessListener(v -> callback.onSuccess("Đã xóa bài đăng"))
                            .addOnFailureListener(e -> callback.onError(e.getMessage()));
                    });
            })
            .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // ======================================================================
    // JOIN Post (gửi yêu cầu tham gia)
    // ======================================================================

    public void joinPost(String postId, String userId, String userFullName, ActionCallback callback) {
        db.collection(COLLECTION_POSTS).document(postId)
            .get()
            .addOnSuccessListener(doc -> {
                if (!doc.exists()) { callback.onError("Không tìm thấy bài đăng"); return; }

                // Kiểm tra post expired
                Timestamp endTime = doc.getTimestamp("endTime");
                if (endTime != null && endTime.toDate().before(new Date())) {
                    callback.onError("Bài viết đã hết hạn, không thể tham gia");
                    return;
                }

                String authorId = (String) doc.get("authorId");
                if (userId.equals(authorId)) {
                    callback.onError("Bạn là chủ bài đăng, không cần tham gia");
                    return;
                }

                // Kiểm tra đã là member chưa
                db.collection(COLLECTION_POSTS).document(postId)
                    .collection(COLLECTION_MEMBERS).document(userId)
                    .get()
                    .addOnSuccessListener(memberDoc -> {
                        if (memberDoc.exists()) {
                            callback.onError("Bạn đã gửi yêu cầu tham gia rồi");
                            return;
                        }
                        // Thêm member với status PENDING
                        addMemberToPost(postId, userId, userFullName, "PENDING", new ActionCallback() {
                            @Override public void onSuccess(String msg) {
                                // Gửi notification cho owner
                                sendJoinNotification(postId, authorId, userId, userFullName,
                                        (String) doc.get("content"));
                                callback.onSuccess("Đã gửi yêu cầu tham gia!");
                            }
                            @Override public void onError(String err) { callback.onError(err); }
                        });
                    })
                    .addOnFailureListener(e -> callback.onError(e.getMessage()));
            })
            .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // ======================================================================
    // APPROVE Member
    // ======================================================================

    public void approveMember(String postId, String memberId, String ownerId, ActionCallback callback) {
        db.collection(COLLECTION_POSTS).document(postId)
            .get()
            .addOnSuccessListener(doc -> {
                if (!doc.exists()) { callback.onError("Không tìm thấy bài đăng"); return; }
                if (!ownerId.equals(doc.get("authorId"))) {
                    callback.onError("Bạn không có quyền duyệt thành viên");
                    return;
                }
                // Cập nhật status → APPROVED
                db.collection(COLLECTION_POSTS).document(postId)
                    .collection(COLLECTION_MEMBERS).document(memberId)
                    .update("status", "APPROVED")
                    .addOnSuccessListener(v -> {
                        // Tăng memberCount
                        db.collection(COLLECTION_POSTS).document(postId)
                            .update("memberCount", FieldValue.increment(1));
                        // Thêm vào chat room
                        addMemberToActivityChatRoom(postId, memberId);
                        // Gửi notification
                        sendApproveNotification(postId, memberId, ownerId, (String) doc.get("content"));
                        callback.onSuccess("Đã duyệt thành viên!");
                    })
                    .addOnFailureListener(e -> callback.onError(e.getMessage()));
            })
            .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // ======================================================================
    // REJECT Member
    // ======================================================================

    public void rejectMember(String postId, String memberId, String ownerId, ActionCallback callback) {
        db.collection(COLLECTION_POSTS).document(postId)
            .get()
            .addOnSuccessListener(doc -> {
                if (!doc.exists()) { callback.onError("Không tìm thấy bài đăng"); return; }
                if (!ownerId.equals(doc.get("authorId"))) {
                    callback.onError("Bạn không có quyền từ chối thành viên");
                    return;
                }
                db.collection(COLLECTION_POSTS).document(postId)
                    .collection(COLLECTION_MEMBERS).document(memberId)
                    .update("status", "REJECTED")
                    .addOnSuccessListener(v -> {
                        sendRejectNotification(postId, memberId, ownerId, (String) doc.get("content"));
                        callback.onSuccess("Đã từ chối thành viên");
                    })
                    .addOnFailureListener(e -> callback.onError(e.getMessage()));
            })
            .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // ======================================================================
    // GET Members of a Post
    // ======================================================================

    public void getMembers(String postId, MembersCallback callback) {
        db.collection(COLLECTION_POSTS).document(postId)
            .collection(COLLECTION_MEMBERS)
            .get()
            .addOnSuccessListener(snaps -> {
                List<Map<String, Object>> members = new ArrayList<>();
                for (QueryDocumentSnapshot doc : snaps) {
                    Map<String, Object> m = doc.getData();
                    m.put("userId", doc.getId());
                    members.add(m);
                }
                callback.onSuccess(members);
            })
            .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public void getPendingMembers(String postId, MembersCallback callback) {
        db.collection(COLLECTION_POSTS).document(postId)
            .collection(COLLECTION_MEMBERS)
            .whereEqualTo("status", "PENDING")
            .get()
            .addOnSuccessListener(snaps -> {
                List<Map<String, Object>> members = new ArrayList<>();
                for (QueryDocumentSnapshot doc : snaps) {
                    Map<String, Object> m = doc.getData();
                    m.put("userId", doc.getId());
                    members.add(m);
                }
                callback.onSuccess(members);
            })
            .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    /** Lấy danh sách thành viên đã được duyệt (status = APPROVED) */
    public void getApprovedMembers(String postId, MembersCallback callback) {
        db.collection(COLLECTION_POSTS).document(postId)
            .collection(COLLECTION_MEMBERS)
            .whereEqualTo("status", "APPROVED")
            .get()
            .addOnSuccessListener(snaps -> {
                List<Map<String, Object>> members = new ArrayList<>();
                for (QueryDocumentSnapshot doc : snaps) {
                    Map<String, Object> m = doc.getData();
                    m.put("userId", doc.getId());
                    members.add(m);
                }
                callback.onSuccess(members);
            })
            .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    /** Báo cáo bài đăng */
    public void reportPost(String postId, String reporterId, String reason, String description, ActionCallback callback) {
        Map<String, Object> report = new HashMap<>();
        report.put("postId", postId);
        report.put("reporterId", reporterId);
        report.put("reason", reason);
        report.put("description", description);
        report.put("createdAt", FieldValue.serverTimestamp());
        db.collection("reports").add(report)
            .addOnSuccessListener(v -> callback.onSuccess("Đã gửi báo cáo"))
            .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // ======================================================================
    // GET User Posts / Archived / Activities
    // ======================================================================

    public void getUserPosts(String userId, PostsCallback callback) {
        Timestamp now = Timestamp.now();
        db.collection(COLLECTION_POSTS)
            .whereEqualTo("authorId", userId)
            .whereEqualTo("archived", false)
            .whereGreaterThan("endTime", now)
            .orderBy("endTime")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener(snaps -> {
                List<Map<String, Object>> posts = new ArrayList<>();
                for (QueryDocumentSnapshot doc : snaps) {
                    Map<String, Object> p = doc.getData();
                    p.put("id", doc.getId());
                    posts.add(p);
                }
                callback.onSuccess(posts);
            })
            .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public void getUserArchivedPosts(String userId, PostsCallback callback) {
        db.collection(COLLECTION_POSTS)
            .whereEqualTo("authorId", userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener(snaps -> {
                List<Map<String, Object>> posts = new ArrayList<>();
                Timestamp now = Timestamp.now();
                for (QueryDocumentSnapshot doc : snaps) {
                    Boolean archived = doc.getBoolean("archived");
                    Timestamp endTime = doc.getTimestamp("endTime");
                    boolean isExpired = endTime != null && endTime.compareTo(now) < 0;
                    if (Boolean.TRUE.equals(archived) || isExpired) {
                        Map<String, Object> p = doc.getData();
                        p.put("id", doc.getId());
                        posts.add(p);
                    }
                }
                callback.onSuccess(posts);
            })
            .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    /** Lấy bài viết user đã tham gia (approved) — không phải bài của chính họ */
    public void getMyActivities(String userId, PostsCallback callback) {
        db.collection(COLLECTION_POSTS)
            .whereNotEqualTo("authorId", userId)
            .get()
            .addOnSuccessListener(postSnaps -> {
                List<Map<String, Object>> result = new ArrayList<>();
                int[] remaining = {postSnaps.size()};
                if (remaining[0] == 0) { callback.onSuccess(result); return; }

                for (QueryDocumentSnapshot postDoc : postSnaps) {
                    String postId = postDoc.getId();
                    db.collection(COLLECTION_POSTS).document(postId)
                        .collection(COLLECTION_MEMBERS).document(userId)
                        .get()
                        .addOnSuccessListener(memberDoc -> {
                            remaining[0]--;
                            if (memberDoc.exists() && "APPROVED".equals(memberDoc.getString("status"))) {
                                Map<String, Object> p = postDoc.getData();
                                p.put("id", postId);
                                result.add(p);
                            }
                            if (remaining[0] == 0) callback.onSuccess(result);
                        })
                        .addOnFailureListener(e -> {
                            remaining[0]--;
                            if (remaining[0] == 0) callback.onSuccess(result);
                        });
                }
            })
            .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // ======================================================================
    // SEARCH Posts
    // ======================================================================

    /**
     * Tìm kiếm bài đăng theo content hoặc interestTag (case-insensitive client-side).
     * Firestore không hỗ trợ full-text search native → lấy tất cả rồi filter.
     * TODO: Tích hợp Algolia/Firebase Extensions nếu cần tìm kiếm nâng cao.
     */
    public void searchPosts(String query, PostsCallback callback) {
        String lowerQuery = query.toLowerCase();
        db.collection(COLLECTION_POSTS)
            .whereEqualTo("archived", false)
            .get()
            .addOnSuccessListener(snaps -> {
                List<Map<String, Object>> posts = new ArrayList<>();
                Timestamp now = Timestamp.now();
                for (QueryDocumentSnapshot doc : snaps) {
                    Timestamp endTime = doc.getTimestamp("endTime");
                    if (endTime != null && endTime.compareTo(now) < 0) continue; // skip expired

                    String content = doc.getString("content");
                    String tag = doc.getString("interestTag");
                    if ((content != null && content.toLowerCase().contains(lowerQuery)) ||
                        (tag != null && tag.toLowerCase().contains(lowerQuery))) {
                        Map<String, Object> p = doc.getData();
                        p.put("id", doc.getId());
                        posts.add(p);
                    }
                }
                callback.onSuccess(posts);
            })
            .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // ======================================================================
    // Private Helpers
    // ======================================================================

    private void addMemberToPost(String postId, String userId, String fullName,
                                  String status, ActionCallback callback) {
        Map<String, Object> memberData = new HashMap<>();
        memberData.put("userId", userId);
        memberData.put("fullName", fullName);
        memberData.put("status", status);
        memberData.put("joinedAt", FieldValue.serverTimestamp());

        db.collection(COLLECTION_POSTS).document(postId)
            .collection(COLLECTION_MEMBERS).document(userId)
            .set(memberData)
            .addOnSuccessListener(v -> callback.onSuccess("ok"))
            .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    private void createActivityChatRoom(String postId, String ownerId,
                                         String interestTag, String ownerName) {
        String title = (interestTag != null && !interestTag.isEmpty() ? interestTag : "Hoạt động")
                       + " - " + ownerName;

        // Kiểm tra room đã tồn tại chưa
        db.collection("chatRooms")
            .whereEqualTo("postId", postId)
            .limit(1)
            .get()
            .addOnSuccessListener(snaps -> {
                if (!snaps.isEmpty()) return; // đã có rồi

                Map<String, Object> room = new HashMap<>();
                room.put("title", title);
                room.put("type", "activity");
                room.put("ownerId", ownerId);
                room.put("postId", postId);
                room.put("memberIds", new ArrayList<String>() {{ add(ownerId); }});
                room.put("active", true);
                room.put("createdAt", FieldValue.serverTimestamp());

                db.collection("chatRooms").add(room);
            });
    }

    private void addMemberToActivityChatRoom(String postId, String userId) {
        db.collection("chatRooms")
            .whereEqualTo("postId", postId)
            .limit(1)
            .get()
            .addOnSuccessListener(snaps -> {
                if (snaps.isEmpty()) return;
                String roomId = snaps.getDocuments().get(0).getId();
                db.collection("chatRooms").document(roomId)
                    .update("memberIds", FieldValue.arrayUnion(userId));
            });
    }

    private void sendJoinNotification(String postId, String ownerId, String joinerId,
                                       String joinerName, String postContent) {
        String preview = postContent != null && postContent.length() > 50
                ? postContent.substring(0, 50) + "..." : postContent;
        String message = joinerName + " muốn tham gia kèo \"" + preview + "\" của bạn.";
        createNotification(ownerId, "JOIN_REQUEST", message, joinerName, postId, joinerId);
    }

    private void sendApproveNotification(String postId, String memberId, String ownerId,
                                          String postContent) {
        String preview = postContent != null && postContent.length() > 50
                ? postContent.substring(0, 50) + "..." : postContent;
        // Lấy tên owner
        db.collection("users").document(ownerId).get().addOnSuccessListener(doc -> {
            String ownerName = doc.exists() ? doc.getString("fullName") : "Chủ bài đăng";
            String message = "Chúc mừng! " + ownerName + " đã chấp nhận yêu cầu của bạn cho kèo \"" + preview + "\".";
            createNotification(memberId, "JOIN_APPROVED", message, ownerName, postId, ownerId);
        });
    }

    private void sendRejectNotification(String postId, String memberId, String ownerId,
                                         String postContent) {
        String preview = postContent != null && postContent.length() > 50
                ? postContent.substring(0, 50) + "..." : postContent;
        db.collection("users").document(ownerId).get().addOnSuccessListener(doc -> {
            String ownerName = doc.exists() ? doc.getString("fullName") : "Chủ bài đăng";
            String message = ownerName + " đã từ chối yêu cầu tham gia kèo \"" + preview + "\" của bạn.";
            createNotification(memberId, "JOIN_REJECTED", message, ownerName, postId, ownerId);
        });
    }

    private void createNotification(String userId, String type, String message,
                                     String actorName, String postId, String actorId) {
        Map<String, Object> notif = new HashMap<>();
        notif.put("userId", userId);
        notif.put("type", type);
        notif.put("message", message);
        notif.put("actorName", actorName);
        notif.put("postId", postId);
        notif.put("actorId", actorId);
        notif.put("read", false);
        notif.put("createdAt", FieldValue.serverTimestamp());
        db.collection("notifications").add(notif)
            .addOnFailureListener(e -> Log.e(TAG, "Notification error: " + e.getMessage()));
    }
}
