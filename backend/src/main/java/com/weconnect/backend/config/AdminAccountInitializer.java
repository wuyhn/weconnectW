package com.weconnect.backend.config;

import com.weconnect.backend.entity.User;
import com.weconnect.backend.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

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
}
