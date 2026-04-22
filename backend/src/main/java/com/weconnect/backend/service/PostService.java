package com.weconnect.backend.service;

import com.weconnect.backend.dto.PostRequest;
import com.weconnect.backend.dto.PostResponse;
import com.weconnect.backend.entity.Notification;
import com.weconnect.backend.entity.BlockedUser;
import com.weconnect.backend.entity.Post;
import com.weconnect.backend.entity.PostMember;
import com.weconnect.backend.entity.User;
import com.weconnect.backend.repository.BlockedUserRepository;
import com.weconnect.backend.repository.PostMemberRepository;
import com.weconnect.backend.repository.PostRepository;
import com.weconnect.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PostService {

    private final PostRepository postRepository;
    private final PostMemberRepository postMemberRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final ChatService chatService;
    private final BlockedUserRepository blockedUserRepository;

    public PostService(PostRepository postRepository,
                       PostMemberRepository postMemberRepository,
                       UserRepository userRepository,
                       NotificationService notificationService,
                       ChatService chatService,
                       BlockedUserRepository blockedUserRepository) {
        this.postRepository = postRepository;
        this.postMemberRepository = postMemberRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.chatService = chatService;
        this.blockedUserRepository = blockedUserRepository;
    }

    // Lấy danh sách bài đăng active
    public List<PostResponse> getActivePosts(Long currentUserId) {
        List<Post> posts = postRepository.findByArchivedFalseAndEndTimeAfterOrderByCreatedAtDesc(LocalDateTime.now());
        posts = filterBlockedPosts(posts, currentUserId);
        return toResponseList(posts, currentUserId);
    }

    // Lấy chi tiết bài đăng
    public PostResponse getPost(Long postId, Long currentUserId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bài đăng."));
        return toResponse(post, currentUserId);
    }

    // Tạo bài đăng mới
    public PostResponse createPost(Long authorId, PostRequest request) {
        LocalDateTime start = request.getStartTime() != null ? request.getStartTime() : LocalDateTime.now();
        // Ưu tiên endTime (chính xác từ frontend), expirationHours chỉ để hiển thị
        LocalDateTime end;
        if (request.getEndTime() != null) {
            end = request.getEndTime();
        } else if (request.getExpirationHours() != null && request.getExpirationHours() > 0) {
            end = start.plusHours(request.getExpirationHours());
        } else {
            end = start.plusDays(1);
        }
        // Tính expirationHours từ khoảng cách thực tế start → end (chỉ để hiển thị)
        long totalMinutes = java.time.Duration.between(start, end).toMinutes();
        Integer expHours = (int) (totalMinutes / 60);

        Post post = Post.builder()
                .authorId(authorId)
                .content(request.getContent())
                .interestTag(request.getInterestTag())
                .location(request.getLocation())
                .imageUrl(request.getImageUrl())
                .maxMembers(request.getMaxMembers() > 0 ? request.getMaxMembers() : 10)
                .startTime(start)
                .endTime(end)
                .expirationHours(expHours)
                .archived(false)
                .expirationNotified(false)
                .build();

        post = postRepository.save(post);

        // Tự động tạo phòng chat cho hoạt động
        String chatTitle = (request.getInterestTag() != null && !request.getInterestTag().isEmpty())
                ? request.getInterestTag() : "Hoạt động";
        User author = userRepository.findById(authorId).orElse(null);
        if (author != null) {
            chatTitle = chatTitle + " - " + author.getFullName();
        }
        chatService.createActivityChatRoom(post.getId(), authorId, chatTitle);

        return toResponse(post, authorId);
    }

    // Sửa bài đăng
    public PostResponse updatePost(Long postId, Long userId, PostRequest request) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bài đăng."));

        if (!post.getAuthorId().equals(userId)) {
            throw new RuntimeException("Bạn không có quyền sửa bài đăng này.");
        }

        if (request.getContent() != null) post.setContent(request.getContent());
        if (request.getInterestTag() != null) post.setInterestTag(request.getInterestTag());
        if (request.getLocation() != null) post.setLocation(request.getLocation());
        if (request.getImageUrl() != null) post.setImageUrl(request.getImageUrl());
        if (request.getMaxMembers() > 0) post.setMaxMembers(request.getMaxMembers());
        if (request.getStartTime() != null) post.setStartTime(request.getStartTime());
        if (request.getEndTime() != null) post.setEndTime(request.getEndTime());

        post = postRepository.save(post);
        return toResponse(post, userId);
    }

    // Xóa bài đăng
    public void deletePost(Long postId, Long userId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bài đăng."));

        if (!post.getAuthorId().equals(userId)) {
            throw new RuntimeException("Bạn không có quyền xóa bài đăng này.");
        }

        postMemberRepository.findByPostId(postId).forEach(postMemberRepository::delete);
        postRepository.delete(post);
    }

    // Xin tham gia hoạt động
    public String joinPost(Long postId, Long userId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bài đăng."));

        // Chặn tham gia bài viết đã hết hạn
        if (post.isExpired()) {
            throw new RuntimeException("Bài viết đã hết hạn, không thể tham gia.");
        }

        if (post.getAuthorId().equals(userId)) {
            throw new RuntimeException("Bạn là chủ bài đăng, không cần tham gia.");
        }

        // Admin không được tham gia hoạt động
        User joinerUser = userRepository.findById(userId).orElse(null);
        if (joinerUser != null && joinerUser.getRole() == 1) {
            throw new RuntimeException("Tài khoản admin không thể tham gia hoạt động.");
        }

        if (postMemberRepository.existsByPostIdAndUserId(postId, userId)) {
            throw new RuntimeException("Bạn đã gửi yêu cầu tham gia rồi.");
        }

        int currentMembers = postMemberRepository.countByPostIdAndStatus(postId, PostMember.Status.APPROVED);
        if (currentMembers >= post.getMaxMembers()) {
            throw new RuntimeException("Hoạt động đã đủ thành viên.");
        }

        PostMember member = PostMember.builder()
                .postId(postId)
                .userId(userId)
                .status(PostMember.Status.PENDING)
                .build();

        postMemberRepository.save(member);

        // Tạo thông báo cho chủ bài đăng
        User joiner = userRepository.findById(userId).orElse(null);
        String joinerName = joiner != null ? joiner.getFullName() : "Người dùng";
        String postTitle = post.getContent();
        if (postTitle != null && postTitle.length() > 50) {
            postTitle = postTitle.substring(0, 50) + "...";
        }
        String message = joinerName + " muốn tham gia kèo \"" + postTitle + "\" của bạn.";
        notificationService.createNotification(
                post.getAuthorId(),
                Notification.NotificationType.JOIN_REQUEST,
                message,
                joinerName,
                postId,
                userId
        );

        return "Đã gửi yêu cầu tham gia!";
    }

    // Duyệt thành viên
    public String approveMember(Long postId, Long memberId, Long ownerId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bài đăng."));

        if (!post.getAuthorId().equals(ownerId)) {
            throw new RuntimeException("Bạn không có quyền duyệt thành viên.");
        }

        PostMember member = postMemberRepository.findByPostIdAndUserId(postId, memberId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy yêu cầu tham gia."));

        member.setStatus(PostMember.Status.APPROVED);
        postMemberRepository.save(member);

        // Tạo thông báo cho người được duyệt
        User owner = userRepository.findById(ownerId).orElse(null);
        String ownerName = owner != null ? owner.getFullName() : "Chủ bài đăng";
        String postTitle = post.getContent();
        if (postTitle != null && postTitle.length() > 50) {
            postTitle = postTitle.substring(0, 50) + "...";
        }
        String message = "Chúc mừng! " + ownerName + " đã chấp nhận yêu cầu của bạn cho kèo \"" + postTitle + "\".";
        notificationService.createNotification(
                memberId,
                Notification.NotificationType.JOIN_APPROVED,
                message,
                ownerName,
                postId,
                ownerId
        );

        // Thêm user vào phòng chat hoạt động
    chatService.addMemberToActivityRoom(postId, memberId);

    return "Đã duyệt thành viên!";
    }

    // Từ chối thành viên
    public String rejectMember(Long postId, Long memberId, Long ownerId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bài đăng."));

        if (!post.getAuthorId().equals(ownerId)) {
            throw new RuntimeException("Bạn không có quyền từ chối thành viên.");
        }

        PostMember member = postMemberRepository.findByPostIdAndUserId(postId, memberId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy yêu cầu tham gia."));

        member.setStatus(PostMember.Status.REJECTED);
        postMemberRepository.save(member);

        // Tạo thông báo cho người bị từ chối
        User owner = userRepository.findById(ownerId).orElse(null);
        String ownerName = owner != null ? owner.getFullName() : "Chủ bài đăng";
        String postTitle = post.getContent();
        if (postTitle != null && postTitle.length() > 50) {
            postTitle = postTitle.substring(0, 50) + "...";
        }
        String message = ownerName + " đã từ chối yêu cầu tham gia kèo \"" + postTitle + "\" của bạn.";
        notificationService.createNotification(
                memberId,
                Notification.NotificationType.JOIN_REJECTED,
                message,
                ownerName,
                postId,
                ownerId
        );

        return "Đã từ chối thành viên.";
    }

    // Lấy danh sách thành viên (enriched with user info)
    public List<java.util.Map<String, Object>> getMembers(Long postId) {
        List<PostMember> members = postMemberRepository.findByPostId(postId);
        List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
        for (PostMember pm : members) {
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            map.put("userId", pm.getUserId());
            map.put("status", pm.getStatus().name());
            User user = userRepository.findById(pm.getUserId()).orElse(null);
            if (user != null) {
                map.put("fullName", user.getFullName());
                map.put("username", user.getEmail());
            } else {
                map.put("fullName", "Người dùng #" + pm.getUserId());
                map.put("username", "");
            }
            result.add(map);
        }
        return result;
    }

    // Lấy danh sách thành viên đang chờ duyệt
    public List<PostMember> getPendingMembers(Long postId) {
        return postMemberRepository.findByPostIdAndStatus(postId, PostMember.Status.PENDING);
    }

    // Bài đăng của user (active)
    public List<PostResponse> getUserActivePosts(Long userId, Long currentUserId) {
        List<Post> posts = postRepository.findByAuthorIdAndArchivedFalseAndEndTimeAfterOrderByCreatedAtDesc(userId, LocalDateTime.now());
        posts = filterBlockedPosts(posts, currentUserId);
        return toResponseList(posts, currentUserId);
    }

    // Bài đăng đã lưu trữ
    public List<PostResponse> getUserArchivedPosts(Long userId, Long currentUserId) {
        List<Post> posts = postRepository.findByAuthorIdOrderByCreatedAtDesc(userId);
        List<Post> archived = new ArrayList<>();
        for (Post post : posts) {
            if (post.isArchived() || post.isExpired()) {
                archived.add(post);
            }
        }
        return toResponseList(archived, currentUserId);
    }

    // Hoạt động của tôi: bài user đã được duyệt tham gia (kể cả hết hạn)
    public List<PostResponse> getMyActivities(Long userId) {
        List<PostMember> memberships = postMemberRepository.findByUserIdAndStatus(userId, PostMember.Status.APPROVED);
        List<PostResponse> results = new ArrayList<>();
        for (PostMember pm : memberships) {
            try {
                Post post = postRepository.findById(pm.getPostId()).orElse(null);
                if (post == null) continue;
                // Bỏ qua bài của chính user (author) - chỉ lấy bài tham gia
                if (post.getAuthorId().equals(userId)) continue;
                results.add(toResponse(post, userId));
            } catch (Exception ignored) {}
        }
        return results;
    }

    // Hoạt động của user khác: lấy activities của targetUser, resolve CTA theo viewer
    public List<PostResponse> getUserActivities(Long targetUserId, Long viewerId) {
        List<PostMember> memberships = postMemberRepository.findByUserIdAndStatus(targetUserId, PostMember.Status.APPROVED);
        List<PostResponse> results = new ArrayList<>();
        for (PostMember pm : memberships) {
            try {
                Post post = postRepository.findById(pm.getPostId()).orElse(null);
                if (post == null) continue;
                // Bỏ qua bài của chính targetUser (author)
                if (post.getAuthorId().equals(targetUserId)) continue;
                // Resolve CTA theo viewer (joined, pending, etc.)
                results.add(toResponse(post, viewerId));
            } catch (Exception ignored) {}
        }
        return results;
    }

    // Tìm kiếm bài đăng
    public List<PostResponse> searchPosts(String query, Long currentUserId) {
        List<Post> posts = postRepository.findByContentContainingIgnoreCaseOrInterestTagContainingIgnoreCase(query, query);
        posts = filterBlockedPosts(posts, currentUserId);
        return toResponseList(posts, currentUserId);
    }

    // --- Helper methods ---
    private PostResponse toResponse(Post post, Long currentUserId) {
        // Chỉ đếm member APPROVED và không phải admin (role != 1)
        List<PostMember> approvedMembers = postMemberRepository.findByPostIdAndStatus(post.getId(), PostMember.Status.APPROVED);
        int memberCount = 0;
        for (PostMember pm : approvedMembers) {
            User pmUser = userRepository.findById(pm.getUserId()).orElse(null);
            if (pmUser != null && pmUser.getRole() == 1) continue; // Bỏ qua admin
            memberCount++;
        }
        memberCount += 1; // +1 tính cả người tổ chức (author)
        boolean joined = false;
        boolean pending = false;

        if (currentUserId != null) {
            if (post.getAuthorId().equals(currentUserId)) {
                // Author luôn là thành viên của bài viết
                joined = true;
            } else {
                PostMember pm = postMemberRepository.findByPostIdAndUserId(post.getId(), currentUserId).orElse(null);
                if (pm != null) {
                    joined = pm.getStatus() == PostMember.Status.APPROVED;
                    pending = pm.getStatus() == PostMember.Status.PENDING;
                }
            }
        }

        String authorName = "";
        String authorAvatarUrl = null;
        User author = userRepository.findById(post.getAuthorId()).orElse(null);
        if (author != null) {
            authorName = author.getFullName();
            authorAvatarUrl = author.getAvatarUrl();
        }

        return PostResponse.builder()
                .id(post.getId())
                .authorId(post.getAuthorId())
                .authorName(authorName)
                .authorAvatarUrl(authorAvatarUrl)
                .content(post.getContent())
                .interestTag(post.getInterestTag())
                .location(post.getLocation())
                .imageUrl(post.getImageUrl())
                .maxMembers(post.getMaxMembers())
                .memberCount(memberCount)
                .joined(joined)
                .pendingApproval(pending)
                .archived(post.isArchived())
                .expired(post.isExpired())
                .expirationHours(post.getExpirationHours())
                .startTime(post.getStartTime())
                .endTime(post.getEndTime())
                .createdAt(post.getCreatedAt())
                .build();
    }

    private List<PostResponse> toResponseList(List<Post> posts, Long currentUserId) {
        List<PostResponse> responses = new ArrayList<>();
        for (Post post : posts) {
            responses.add(toResponse(post, currentUserId));
        }
        return responses;
    }

    /**
     * Lọc bỏ bài đăng từ user bị chặn (2 chiều).
     */
    private List<Post> filterBlockedPosts(List<Post> posts, Long currentUserId) {
        if (currentUserId == null) return posts;

        // Lấy danh sách user mà currentUser đã chặn
        Set<Long> blockedByMe = blockedUserRepository.findByBlockerId(currentUserId)
                .stream().map(BlockedUser::getBlockedId).collect(Collectors.toSet());
        // Lấy danh sách user đã chặn currentUser
        Set<Long> blockedMe = blockedUserRepository.findByBlockedId(currentUserId)
                .stream().map(BlockedUser::getBlockerId).collect(Collectors.toSet());

        List<Post> filtered = new ArrayList<>();
        for (Post post : posts) {
            if (!blockedByMe.contains(post.getAuthorId()) && !blockedMe.contains(post.getAuthorId())) {
                filtered.add(post);
            }
        }
        return filtered;
    }

    /**
     * Kiểm tra bài đăng hết hạn:
     * - Auto-reject các pending requests
     * - Gửi thông báo cho chủ bài viết
     * Được gọi bởi scheduler mỗi 1 phút.
     */
    @Transactional
    public void checkAndNotifyExpiredPosts() {
        List<Post> expiredPosts = postRepository.findByArchivedFalseAndExpirationNotifiedFalseAndEndTimeBefore(LocalDateTime.now());

        for (Post post : expiredPosts) {
            // Lấy danh sách pending members
            List<PostMember> pendingMembers = postMemberRepository.findByPostIdAndStatus(post.getId(), PostMember.Status.PENDING);

            // Auto-reject tất cả pending requests
            for (PostMember pm : pendingMembers) {
                pm.setStatus(PostMember.Status.REJECTED);
                postMemberRepository.save(pm);

                // Thông báo cho user bị reject
                String postTitle = post.getContent();
                if (postTitle != null && postTitle.length() > 50) {
                    postTitle = postTitle.substring(0, 50) + "...";
                }
                String rejectMsg = "Yêu cầu tham gia kèo \"" + postTitle + "\" đã bị từ chối do bài viết hết hạn.";
                notificationService.createNotification(
                        pm.getUserId(),
                        Notification.NotificationType.JOIN_REJECTED,
                        rejectMsg,
                        null,
                        post.getId(),
                        post.getAuthorId()
                );
            }

            // Thông báo cho chủ bài viết
            String postTitle = post.getContent();
            if (postTitle != null && postTitle.length() > 50) {
                postTitle = postTitle.substring(0, 50) + "...";
            }
            String expiredMsg;
            if (!pendingMembers.isEmpty()) {
                expiredMsg = "Bài viết \"" + postTitle + "\" đã hết hạn. " + pendingMembers.size() + " yêu cầu tham gia đã tự động bị từ chối.";
            } else {
                expiredMsg = "Bài viết \"" + postTitle + "\" đã hết hạn.";
            }
            notificationService.createNotification(
                    post.getAuthorId(),
                    Notification.NotificationType.POST_EXPIRED,
                    expiredMsg,
                    null,
                    post.getId(),
                    null
            );

            // Đánh dấu đã gửi thông báo + tự động chuyển vào kho lưu trữ
            post.setExpirationNotified(true);
            post.setArchived(true);
            postRepository.save(post);
        }
    }
}
