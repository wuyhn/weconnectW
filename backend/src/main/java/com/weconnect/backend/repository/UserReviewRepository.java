package com.weconnect.backend.repository;

import com.weconnect.backend.entity.UserReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserReviewRepository extends JpaRepository<UserReview, Long> {

    List<UserReview> findByReviewedUserIdOrderByCreatedAtDesc(Long reviewedUserId);

    boolean existsByReviewerIdAndReviewedUserId(Long reviewerId, Long reviewedUserId);

    Optional<UserReview> findByReviewerIdAndReviewedUserId(Long reviewerId, Long reviewedUserId);

    // Tất cả reviews liên quan đến user (dùng cho cascade delete)
    List<UserReview> findByReviewerIdOrReviewedUserId(Long reviewerId, Long reviewedUserId);

    // Đếm tổng số review một user nhận được
    int countByReviewedUserId(Long reviewedUserId);

    // Đếm số review một user nhận được trong một hoạt động (để tính completion bonus)
    int countByReviewedUserIdAndPostId(Long reviewedUserId, Long postId);

    long countByCreatedAtBetween(java.time.LocalDateTime start, java.time.LocalDateTime end);
}
