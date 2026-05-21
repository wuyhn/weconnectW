package com.weconnect.backend.repository;

import com.weconnect.backend.entity.PostMember;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
