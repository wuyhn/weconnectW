package com.weconnect.backend.repository;

import com.weconnect.backend.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByRoomIdOrderByCreatedAtAsc(Long roomId);

    // Lấy tin nhắn mới sau 1 ID (cho polling)
    List<ChatMessage> findByRoomIdAndIdGreaterThanOrderByCreatedAtAsc(Long roomId, Long afterId);
}
