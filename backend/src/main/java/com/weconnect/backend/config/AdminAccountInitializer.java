package com.weconnect.backend.config;

import com.weconnect.backend.entity.User;
import com.weconnect.backend.entity.UserReview;
import com.weconnect.backend.repository.UserRepository;
import com.weconnect.backend.repository.UserReviewRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

/**
 * Tự động tạo tài khoản Admin duy nhất khi khởi động server
 * nếu chưa tồn tại trong database.
 *
 * Email: admin@weconnect.com
 * Password: admin123
 */
@Configuration
public class AdminAccountInitializer {

    private static final String ADMIN_EMAIL = "admin@weconnect.com";
    private static final String ADMIN_PASSWORD = "admin123";
    private static final String ADMIN_NAME = "Admin WeConnect";

    @Bean
    @Order(1)
    public CommandLineRunner initAdminAccount(UserRepository userRepository,
                                              PasswordEncoder passwordEncoder) {
        return args -> {
            // Kiểm tra nếu admin chưa tồn tại thì tạo mới
            if (userRepository.findByEmail(ADMIN_EMAIL).isEmpty()) {
                User admin = User.builder()
                        .email(ADMIN_EMAIL)
                        .password(passwordEncoder.encode(ADMIN_PASSWORD))
                        .fullName(ADMIN_NAME)
                        .role(1) // 1 = Admin
                        .isBlocked(false)
                        .build();
                userRepository.save(admin);
                System.out.println("✅ Tài khoản Admin đã được tạo: " + ADMIN_EMAIL);
            } else {
                System.out.println("ℹ️ Tài khoản Admin đã tồn tại: " + ADMIN_EMAIL);
            }
        };
    }

    // Migration: tính lại reputationScore cho tất cả user có score = 0 (chưa được khởi tạo).
    // Bắt đầu từ 100, áp dụng delta của toàn bộ review đã nhận + completion bonus.
    @Bean
    @Order(2)
    public CommandLineRunner migrateReputationScores(UserRepository userRepository,
                                                     UserReviewRepository reviewRepository) {
        return args -> {
            long count = 0;
            for (User user : userRepository.findAll()) {
                if (user.getReputationScore() == 0.0) {
                    List<UserReview> reviews =
                            reviewRepository.findByReviewedUserIdOrderByCreatedAtDesc(user.getId());

                    double score = 100.0;

                    // Áp dụng delta theo từng rating đã nhận
                    for (UserReview review : reviews) {
                        if (review.getRating() != null) {
                            score += ratingDelta(review.getRating());
                        }
                    }

                    // Cộng completion bonus: +1 cho mỗi hoạt động duy nhất đã nhận review
                    long uniqueActivities = reviews.stream()
                            .filter(r -> r.getPostId() != null)
                            .map(UserReview::getPostId)
                            .distinct()
                            .count();
                    score += uniqueActivities;

                    // Clamp 0–100
                    score = Math.max(0, Math.min(100, score));

                    user.setReputationScore(score);
                    userRepository.save(user);
                    count++;
                }
            }
            if (count > 0) {
                System.out.println("✅ Migrated " + count + " user(s): reputationScore tính lại từ review history");
            }
        };
    }

    private double ratingDelta(int rating) {
        return switch (rating) {
            case 1 -> -8.0;
            case 2 -> -5.0;
            case 3 -> -2.0;
            case 4 ->  0.5;
            case 5 ->  1.0;
            default -> 0.0;
        };
    }
}
