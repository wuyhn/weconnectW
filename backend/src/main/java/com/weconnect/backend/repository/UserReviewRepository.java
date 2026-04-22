package com.weconnect.backend.repository;

import com.weconnect.backend.entity.UserReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserReviewRepository extends JpaRepository<UserReview, Long> {

    List<UserReview> findByReviewedUserIdOrderByCreatedAtDesc(Long reviewedUserId);

    boolean existsByReviewerIdAndReviewedUserId(Long reviewerId, Long reviewedUserId);

    // Tất cả reviews liên quan đến user (dùng cho cascade delete)
    List<UserReview> findByReviewerIdOrReviewedUserId(Long reviewerId, Long reviewedUserId);
}
