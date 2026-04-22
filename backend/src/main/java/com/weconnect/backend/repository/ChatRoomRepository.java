package com.weconnect.backend.repository;

import com.weconnect.backend.entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    // Tìm phòng direct giữa 2 user
    @Query("SELECT cr FROM ChatRoom cr WHERE cr.type = 'direct' AND cr.id IN " +
            "(SELECT m1.roomId FROM ChatRoomMember m1 WHERE m1.userId = :user1 AND m1.roomId IN " +
            "(SELECT m2.roomId FROM ChatRoomMember m2 WHERE m2.userId = :user2))")
    Optional<ChatRoom> findDirectRoomBetween(@Param("user1") Long user1, @Param("user2") Long user2);

    // Tìm kiếm phòng
    List<ChatRoom> findByTitleContainingIgnoreCase(String query);

    // Tìm phòng chat hoạt động theo postId
    Optional<ChatRoom> findByPostId(Long postId);
}
