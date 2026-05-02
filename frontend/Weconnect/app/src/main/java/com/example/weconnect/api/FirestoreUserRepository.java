package com.example.weconnect.api;

import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FirestoreUserRepository — thay thế UserApiService + UserService backend.
 * Đọc/ghi users/{uid} và tìm kiếm user trong Firestore.
 */
public class FirestoreUserRepository {

    private static final String COL_USERS        = "users";
    private static final String COL_BLOCKED      = "blockedUsers";
    private static final String COL_REVIEWS      = "userReviews";

    private final FirebaseFirestore db;

    public FirestoreUserRepository() {
        this.db = FirebaseManager.getFirestore();
    }

    public interface UserCallback {
        void onSuccess(Map<String, Object> user);
        void onError(String error);
    }

    public interface UsersCallback {
        void onSuccess(List<Map<String, Object>> users);
        void onError(String error);
    }

    public interface ActionCallback {
        void onSuccess(String message);
        void onError(String error);
    }

    /** Alias dùng trong EditProfileActivity, UserProfileActivity */
    public interface ProfileCallback extends UserCallback {}

    public interface InterestsCallback {
        void onSuccess(List<String> interests);
        void onError(String error);
    }

    // ======================================================================
    // GET User Profile
    // ======================================================================

    public void getUserProfile(String uid, UserCallback callback) {
        db.collection(COL_USERS).document(uid)
            .get()
            .addOnSuccessListener(doc -> {
                if (!doc.exists()) { callback.onError("Không tìm thấy người dùng"); return; }
                Map<String, Object> user = doc.getData();
                user.put("id", doc.getId());
                callback.onSuccess(user);
            })
            .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // ======================================================================
    // UPDATE User Profile
    // ======================================================================

    public void updateProfile(String uid, Map<String, Object> updates, ActionCallback callback) {
        db.collection(COL_USERS).document(uid)
            .update(updates)
            .addOnSuccessListener(v -> callback.onSuccess("Đã cập nhật thông tin"))
            .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // ======================================================================
    // SEARCH Users
    // ======================================================================

    /**
     * Tìm kiếm user theo fullName hoặc email (client-side filter).
     * Trả về danh sách user khớp với query, loại trừ currentUserId.
     */
    public void searchUsers(String query, String currentUserId, UsersCallback callback) {
        String lowerQuery = query.toLowerCase();
        db.collection(COL_USERS)
            .get()
            .addOnSuccessListener(snaps -> {
                List<Map<String, Object>> results = new ArrayList<>();
                for (QueryDocumentSnapshot doc : snaps) {
                    if (doc.getId().equals(currentUserId)) continue; // bỏ qua bản thân

                    String name  = doc.getString("fullName");
                    String email = doc.getString("email");
                    if ((name  != null && name.toLowerCase().contains(lowerQuery)) ||
                        (email != null && email.toLowerCase().contains(lowerQuery))) {
                        Map<String, Object> u = doc.getData();
                        u.put("id", doc.getId());
                        results.add(u);
                    }
                }
                callback.onSuccess(results);
            })
            .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // ======================================================================
    // BLOCK / UNBLOCK User
    // ======================================================================

    public void blockUser(String blockerId, String blockedId, ActionCallback callback) {
        String docId = blockerId + "_" + blockedId;
        Map<String, Object> data = new HashMap<>();
        data.put("blockerId", blockerId);
        data.put("blockedId", blockedId);
        data.put("createdAt", FieldValue.serverTimestamp());

        db.collection(COL_BLOCKED).document(docId)
            .set(data)
            .addOnSuccessListener(v -> callback.onSuccess("Đã chặn người dùng"))
            .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public void unblockUser(String blockerId, String blockedId, ActionCallback callback) {
        String docId = blockerId + "_" + blockedId;
        db.collection(COL_BLOCKED).document(docId)
            .delete()
            .addOnSuccessListener(v -> callback.onSuccess("Đã bỏ chặn"))
            .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public void getBlockedUsers(String blockerId, UsersCallback callback) {
        db.collection(COL_BLOCKED)
            .whereEqualTo("blockerId", blockerId)
            .get()
            .addOnSuccessListener(snaps -> {
                List<Map<String, Object>> users = new ArrayList<>();
                int[] remaining = {snaps.size()};
                if (remaining[0] == 0) { callback.onSuccess(users); return; }

                for (QueryDocumentSnapshot doc : snaps) {
                    String blockedId = doc.getString("blockedId");
                    db.collection(COL_USERS).document(blockedId).get()
                        .addOnSuccessListener(userDoc -> {
                            remaining[0]--;
                            if (userDoc.exists()) {
                                Map<String, Object> u = userDoc.getData();
                                u.put("id", userDoc.getId());
                                users.add(u);
                            }
                            if (remaining[0] == 0) callback.onSuccess(users);
                        })
                        .addOnFailureListener(e -> {
                            remaining[0]--;
                            if (remaining[0] == 0) callback.onSuccess(users);
                        });
                }
            })
            .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public void isUserBlocked(String blockerId, String blockedId,
                               com.google.firebase.firestore.EventListener<Boolean> listener) {
        String docId = blockerId + "_" + blockedId;
        db.collection(COL_BLOCKED).document(docId).get()
            .addOnSuccessListener(doc -> listener.onEvent(doc.exists(), null));
    }

    // ======================================================================
    // REVIEWS
    // ======================================================================

    public void submitReview(String reviewerId, String reviewedId,
                              float rating, String comment, ActionCallback callback) {
        String docId = reviewerId + "_" + reviewedId;
        Map<String, Object> review = new HashMap<>();
        review.put("reviewerId", reviewerId);
        review.put("reviewedId", reviewedId);
        review.put("rating", rating);
        review.put("comment", comment);
        review.put("createdAt", FieldValue.serverTimestamp());

        db.collection(COL_REVIEWS).document(docId)
            .set(review)
            .addOnSuccessListener(v -> {
                // Cập nhật averageRating cho user được đánh giá
                updateAverageRating(reviewedId);
                callback.onSuccess("Đã gửi đánh giá");
            })
            .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    private void updateAverageRating(String userId) {
        db.collection(COL_REVIEWS)
            .whereEqualTo("reviewedId", userId)
            .get()
            .addOnSuccessListener(snaps -> {
                if (snaps.isEmpty()) return;
                double total = 0;
                for (QueryDocumentSnapshot doc : snaps) {
                    Object r = doc.get("rating");
                    if (r instanceof Number) total += ((Number) r).doubleValue();
                }
                double avg = total / snaps.size();
                db.collection(COL_USERS).document(userId)
                    .update("averageRating", avg);
            });
    }

    public void getUserReviews(String userId, UsersCallback callback) {
        db.collection(COL_REVIEWS)
            .whereEqualTo("reviewedId", userId)
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener(snaps -> {
                List<Map<String, Object>> reviews = new ArrayList<>();
                for (QueryDocumentSnapshot doc : snaps) {
                    Map<String, Object> r = doc.getData();
                    r.put("id", doc.getId());
                    reviews.add(r);
                }
                callback.onSuccess(reviews);
            })
            .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // ======================================================================
    // INTERESTS
    // ======================================================================

    /** Lưu danh sách sở thích vào users/{uid}.interestTags */
    public void saveInterests(String uid, List<String> interests, ActionCallback callback) {
        db.collection(COL_USERS).document(uid)
            .update("interestTags", interests)
            .addOnSuccessListener(v -> callback.onSuccess("Đã lưu sở thích"))
            .addOnFailureListener(e ->
                // Nếu doc chưa tồn tại, tạo mới
                db.collection(COL_USERS).document(uid)
                    .set(java.util.Collections.singletonMap("interestTags", interests),
                        com.google.firebase.firestore.SetOptions.merge())
                    .addOnSuccessListener(v2 -> callback.onSuccess("Đã lưu sở thích"))
                    .addOnFailureListener(e2 -> callback.onError(e2.getMessage()))
            );
    }

    /** Lấy danh sách sở thích của user */
    @SuppressWarnings("unchecked")
    public void getInterests(String uid, InterestsCallback callback) {
        db.collection(COL_USERS).document(uid)
            .get()
            .addOnSuccessListener(doc -> {
                if (!doc.exists()) { callback.onSuccess(new ArrayList<>()); return; }
                Object tags = doc.get("interestTags");
                List<String> interests = new ArrayList<>();
                if (tags instanceof List) {
                    for (Object t : (List<?>) tags) interests.add(t.toString());
                } else if (tags instanceof String && !((String) tags).isEmpty()) {
                    for (String t : ((String) tags).split(",")) {
                        String trimmed = t.trim();
                        if (!trimmed.isEmpty()) interests.add(trimmed);
                    }
                }
                callback.onSuccess(interests);
            })
            .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // ======================================================================
    // DELETE Account
    // ======================================================================

    /** Xoá dữ liệu user trong Firestore (Firebase Auth deletion do client tự gọi) */
    public void deleteAccount(String uid, ActionCallback callback) {
        db.collection(COL_USERS).document(uid)
            .delete()
            .addOnSuccessListener(v -> callback.onSuccess("Đã xoá tài khoản"))
            .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }
}

