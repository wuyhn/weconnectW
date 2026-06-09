package com.weconnect.backend.repository;

import com.weconnect.backend.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByRoomIdOrderByCreatedAtAsc(Long roomId);

    // Lấy tin nhắn mới sau 1 ID (cho polling)
    List<ChatMessage> findByRoomIdAndIdGreaterThanOrderByCreatedAtAsc(Long roomId, Long afterId);

    // Đếm tin chưa đọc (id > lastReadMessageId)
    int countByRoomIdAndIdGreaterThan(Long roomId, Long lastReadMessageId);

    // Lấy id tin nhắn mới nhất trong phòng
    java.util.Optional<ChatMessage> findTopByRoomIdOrderByIdDesc(Long roomId);

    // Lấy 50 tin nhắn thực (loại trừ SUMMARY và SYSTEM) gần nhất cho AI tóm tắt
    @Query("SELECT m FROM ChatMessage m WHERE m.roomId = :roomId AND (m.type IS NULL OR (m.type != 'SUMMARY' AND m.type != 'SYSTEM')) ORDER BY m.createdAt DESC LIMIT 50")
    List<ChatMessage> findTop50RealMessagesByRoomId(@Param("roomId") Long roomId);

    // Đếm tin nhắn của một user cụ thể trong phòng (dùng để kiểm tra giới hạn 5 tin khi PENDING)
    long countByRoomIdAndSenderId(Long roomId, Long senderId);
}
