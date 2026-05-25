package com.weconnect.backend.config;

import com.weconnect.backend.entity.User;
import com.weconnect.backend.repository.UserRepository;
import com.weconnect.backend.service.ReviewService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

@Configuration
public class AdminAccountInitializer {

    private static final String ADMIN_EMAIL = "admin@weconnect.com";
    private static final String ADMIN_PASSWORD = "admin123";
    private static final String ADMIN_NAME = "Admin WeConnect";

    // Migrate: đổi cột status từ ENUM sang VARCHAR rồi cập nhật giá trị cũ
    @Bean
    @Order(0)
    public CommandLineRunner migrateReportStatuses(JdbcTemplate jdbcTemplate) {
        return args -> {
            // Đổi ENUM → VARCHAR để thoát khỏi ràng buộc enum cũ (PENDING/REVIEWED/RESOLVED)
            // Nếu column đã là VARCHAR thì lệnh này vẫn chạy được (idempotent)
            try {
                jdbcTemplate.execute(
                    "ALTER TABLE reports MODIFY COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PENDING'");
            } catch (Exception ignored) {}

            int toValid = jdbcTemplate.update(
                    "UPDATE reports SET status = 'VALID' WHERE status = 'RESOLVED'");
            int toPending = jdbcTemplate.update(
                    "UPDATE reports SET status = 'PENDING' WHERE status = 'REVIEWED'");
            if (toValid > 0 || toPending > 0) {
                System.out.println("✅ Report migration: " + toValid + " RESOLVED→VALID, " + toPending + " REVIEWED→PENDING");
            }
        };
    }

    @Bean
    @Order(1)
    public CommandLineRunner initAdminAccount(UserRepository userRepository,
                                              PasswordEncoder passwordEncoder) {
        return args -> {
            if (userRepository.findByEmail(ADMIN_EMAIL).isEmpty()) {
                User admin = User.builder()
                        .email(ADMIN_EMAIL)
                        .password(passwordEncoder.encode(ADMIN_PASSWORD))
                        .fullName(ADMIN_NAME)
                        .role(1)
                        .isBlocked(false)
                        .build();
                userRepository.save(admin);
                System.out.println("✅ Tài khoản Admin đã được tạo: " + ADMIN_EMAIL);
            } else {
                System.out.println("ℹ️ Tài khoản Admin đã tồn tại: " + ADMIN_EMAIL);
            }
        };
    }

    // Tính lại reputationScore cho tất cả user theo công thức mới:
    // ratingScore = avgRating/5*100 (hoặc 60 nếu chưa có review)
    // reportPenalty = tổng penaltyPoint từ báo cáo VALID
    // score = clamp(ratingScore - reportPenalty, 0, 100)
    @Bean
    @Order(2)
    public CommandLineRunner migrateReputationScores(UserRepository userRepository,
                                                     ReviewService reviewService) {
        return args -> {
            List<User> users = userRepository.findAll();
            for (User user : users) {
                // Reset adminPenalty (không còn dùng trong công thức mới)
                user.setAdminPenalty(0.0);
                userRepository.save(user);
                reviewService.recalculateReputation(user.getId());
            }
            System.out.println("✅ Migrated " + users.size() + " user(s): reputationScore = rating-based - reportPenalty");
        };
    }
}
