package com.weconnect.backend.repository;

import com.weconnect.backend.entity.SystemViolationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SystemViolationLogRepository extends JpaRepository<SystemViolationLog, Long> {

    List<SystemViolationLog> findByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * Tổng điểm phạt ròng của một user:
     *   dương = phạt tích lũy, âm = đã được tha phạt (giá trị COALESCE về 0 nếu chưa có record nào).
     * recalculateReputation() clamp giá trị này về Math.max(0, sum) trước khi trừ.
     */
    @Query("SELECT COALESCE(SUM(v.penaltyPoint), 0) FROM SystemViolationLog v WHERE v.userId = :userId")
    int sumPenaltyByUserId(@Param("userId") Long userId);
}
