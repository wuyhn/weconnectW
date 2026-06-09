package com.weconnect.backend.repository;

import com.weconnect.backend.entity.PostMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PostMemberRepository extends JpaRepository<PostMember, Long> {

    List<PostMember> findByPostId(Long postId);

    List<PostMember> findByPostIdAndStatus(Long postId, PostMember.Status status);

    Optional<PostMember> findByPostIdAndUserId(Long postId, Long userId);

    int countByPostIdAndStatus(Long postId, PostMember.Status status);

    boolean existsByPostIdAndUserId(Long postId, Long userId);

    // Lấy danh sách bài đăng mà user tham gia theo trạng thái
    List<PostMember> findByUserIdAndStatus(Long userId, PostMember.Status status);

    // Tất cả memberships của user (dùng cho cascade delete)
    List<PostMember> findByUserId(Long userId);

    // Xóa tất cả members của một post
    void deleteByPostId(Long postId);

    @org.springframework.transaction.annotation.Transactional
    void deleteByPostIdAndUserId(Long postId, Long userId);

    // Cập nhật hàng loạt: đánh dấu REJECTED_BY_SYSTEM cho tất cả PENDING của một post
    // Dùng trong handleHostSanctionEvent khi Host bị khóa lúc hoạt động đang diễn ra
    @Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("UPDATE PostMember pm SET pm.status = 'REJECTED_BY_SYSTEM' " +
           "WHERE pm.postId = :postId AND pm.status = 'PENDING'")
    int rejectAllPendingByPostId(@Param("postId") Long postId);

    // Lấy danh sách userId của các thành viên đã được APPROVED trong một post
    // Dùng để gửi FCM thông báo hủy hoạt động sắp diễn ra
    @Query("SELECT pm.userId FROM PostMember pm WHERE pm.postId = :postId AND pm.status = 'APPROVED'")
    List<Long> findApprovedUserIdsByPostId(@Param("postId") Long postId);
}
