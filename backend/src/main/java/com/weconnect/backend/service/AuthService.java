package com.weconnect.backend.service;

import com.weconnect.backend.dto.AuthRequest;
import com.weconnect.backend.dto.AuthResponse;
import com.weconnect.backend.entity.User;
import com.weconnect.backend.repository.UserRepository;
import com.weconnect.backend.security.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public String register(AuthRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            return "Email này đã được sử dụng!";
        }

        User newUser = new User();
        newUser.setEmail(request.getEmail());
        newUser.setFullName(request.getFullName());
        newUser.setPassword(passwordEncoder.encode(request.getPassword()));
        newUser.setBirthday(request.getBirthday());
        newUser.setGender(request.getGender());
        newUser.setBlocked(false);

        userRepository.save(newUser);
        return "Đăng ký tài khoản thành công!";
    }

    public AuthResponse login(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException(
                        "Tài khoản không tồn tại. Vui lòng kiểm tra lại hoặc đăng ký tài khoản mới."));

        if (user.isBlocked()) {
            throw new RuntimeException("Tài khoản của bạn hiện đang bị khóa.");
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("Mật khẩu sai, vui lòng kiểm tra lại.");
        }

        String token = jwtTokenProvider.generateToken(user.getId(), user.getEmail(), user.getRole());

        return AuthResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .token(token)
                .role(user.getRole())
                .message("Đăng nhập thành công!")
                .build();
    }
}
