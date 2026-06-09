package com.weconnect.backend.config;

import com.weconnect.backend.entity.User;
import com.weconnect.backend.repository.UserRepository;
import com.weconnect.backend.service.ReviewService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;

import java.util.List;

@Configuration
public class AdminAccountInitializer {

    private static final String DEFAULT_ADMIN_NAME = "Admin WeConnect";

    @Value("${app.admin.email:admin@weconnect.com}")
    private String adminEmail;

    @Value("${app.admin.password:}")
    private String adminPassword;

    @Value("${app.admin.full-name:Admin WeConnect}")
    private String adminFullName;

    @Value("${app.migration.report-status-on-startup:false}")
    private boolean migrateReportStatusOnStartup;

    @Value("${app.migration.recalculate-reputation-on-startup:false}")
    private boolean recalculateReputationOnStartup;

    @Bean
    @Order(0)
    public CommandLineRunner migrateReportStatuses(JdbcTemplate jdbcTemplate) {
        return args -> {
            if (!migrateReportStatusOnStartup) {
                return;
            }

            try {
                jdbcTemplate.execute("ALTER TABLE notifications MODIFY COLUMN type VARCHAR(50) NOT NULL");
                System.out.println("Notification migration: notifications.type = VARCHAR(50)");
            } catch (Exception ignored) {
            }

            try {
                jdbcTemplate.execute("ALTER TABLE reports MODIFY COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PENDING'");
            } catch (Exception ignored) {
            }

            int toValid = jdbcTemplate.update("UPDATE reports SET status = 'VALID' WHERE status = 'RESOLVED'");
            int toPending = jdbcTemplate.update("UPDATE reports SET status = 'PENDING' WHERE status = 'REVIEWED'");
            if (toValid > 0 || toPending > 0) {
                System.out.println("Report migration: " + toValid + " RESOLVED->VALID, " + toPending + " REVIEWED->PENDING");
            }
        };
    }

    @Bean
    @Order(1)
    public CommandLineRunner initAdminAccount(UserRepository userRepository,
                                              PasswordEncoder passwordEncoder) {
        return args -> {
            String email = adminEmail == null ? "admin@weconnect.com" : adminEmail.trim();
            String fullName = StringUtils.hasText(adminFullName) ? adminFullName.trim() : DEFAULT_ADMIN_NAME;

            var existingAdmin = userRepository.findByEmail(email);
            if (existingAdmin.isPresent()) {
                if (StringUtils.hasText(adminPassword)) {
                    User admin = existingAdmin.get();
                    admin.setPassword(passwordEncoder.encode(adminPassword));
                    admin.setRole(1);
                    if (!StringUtils.hasText(admin.getFullName())) {
                        admin.setFullName(fullName);
                    }
                    userRepository.save(admin);
                    System.out.println("Admin account password updated from ADMIN_DEFAULT_PASSWORD: " + email);
                } else {
                    System.out.println("Admin account already exists: " + email);
                }
                return;
            }

            if (!StringUtils.hasText(adminPassword)) {
                System.out.println("Admin account was not created because ADMIN_DEFAULT_PASSWORD is not set.");
                return;
            }

            User admin = User.builder()
                    .email(email)
                    .password(passwordEncoder.encode(adminPassword))
                    .fullName(fullName)
                    .role(1)
                    .isBlocked(false)
                    .build();
            userRepository.save(admin);
            System.out.println("Admin account created from environment config: " + email);
        };
    }

    @Bean
    @Order(2)
    public CommandLineRunner migrateReputationScores(UserRepository userRepository,
                                                     ReviewService reviewService) {
        return args -> {
            if (!recalculateReputationOnStartup) {
                return;
            }

            List<User> users = userRepository.findAll();
            for (User user : users) {
                user.setAdminPenalty(0.0);
                userRepository.save(user);
                reviewService.recalculateReputation(user.getId());
            }
            System.out.println("Migrated " + users.size() + " user(s): reputationScore = rating-based - reportPenalty");
        };
    }
}
