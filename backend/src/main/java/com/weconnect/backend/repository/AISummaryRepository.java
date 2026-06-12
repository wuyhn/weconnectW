package com.weconnect.backend.repository;

import com.weconnect.backend.entity.AISummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AISummaryRepository extends JpaRepository<AISummary, Long> {

    // Lấy tất cả bản tóm tắt của user trong phòng (để merge vào lịch sử chat)
    List<AISummary> findByRoomIdAndUserIdOrderByCreatedAtAsc(Long roomId, Long userId);

    // Tóm tắt gần nhất của user trong phòng (dùng kiểm tra cooldown)
    Optional<AISummary> findTopByRoomIdAndUserIdOrderByCreatedAtDesc(Long roomId, Long userId);
}
