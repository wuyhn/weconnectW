package com.weconnect.backend.repository;

import com.weconnect.backend.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportRepository extends JpaRepository<Report, Long> {

    List<Report> findAllByOrderByCreatedAtDesc();

    List<Report> findByTargetTypeOrderByCreatedAtDesc(Report.TargetType targetType);

    List<Report> findByStatusOrderByCreatedAtDesc(Report.Status status);

    List<Report> findByTargetTypeAndStatusOrderByCreatedAtDesc(
            Report.TargetType targetType, Report.Status status);

    // Đếm reports theo status (cho dashboard)
    long countByStatus(Report.Status status);

    // Admin notification: đếm reports chưa xem
    int countByAdminViewedFalse();

    // Admin notification: lấy reports chưa xem
    List<Report> findByAdminViewedFalseOrderByCreatedAtDesc();
}
