package com.weconnect.backend.repository;

import com.weconnect.backend.entity.Friendship;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {

    // Tìm friendship giữa 2 user (cả 2 chiều)
    @Query("SELECT f FROM Friendship f WHERE " +
            "(f.senderId = :user1 AND f.receiverId = :user2) OR " +
            "(f.senderId = :user2 AND f.receiverId = :user1)")
    List<Friendship> findBetweenUsers(@Param("user1") Long user1, @Param("user2") Long user2);

    // Danh sách bạn bè (ACCEPTED)
    @Query("SELECT f FROM Friendship f WHERE " +
            "(f.senderId = :userId OR f.receiverId = :userId) AND f.status = 'ACCEPTED'")
    List<Friendship> findFriendsByUserId(@Param("userId") Long userId);

    // Lời mời đã nhận (PENDING, mình là receiver)
    List<Friendship> findByReceiverIdAndStatus(Long receiverId, Friendship.Status status);

    // Lời mời đã gửi (PENDING, mình là sender)
    List<Friendship> findBySenderIdAndStatus(Long senderId, Friendship.Status status);

    // Tất cả friendships liên quan đến user (dùng cho cascade delete)
    List<Friendship> findBySenderIdOrReceiverId(Long senderId, Long receiverId);
}
