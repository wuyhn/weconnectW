package com.weconnect.backend.service;

import com.weconnect.backend.dto.AuthRequest;
import com.weconnect.backend.dto.AuthResponse;
import com.weconnect.backend.entity.PendingRegistration;
import com.weconnect.backend.entity.User;
import com.weconnect.backend.repository.PendingRegistrationRepository;
import com.weconnect.backend.repository.UserRepository;
import com.weconnect.backend.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PendingRegistrationRepository pendingRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final EmailService emailService;

    @Value("${app.otp.expiry-minutes:5}")
    private int otpExpiryMinutes;

    @Value("${app.otp.max-attempts:5}")
    private int maxAttempts;

    @Value("${app.otp.resend-cooldown-seconds:60}")
    private int resendCooldownSeconds;

    private static final SecureRandom RANDOM = new SecureRandom();

    public AuthService(UserRepository userRepository,
                       PendingRegistrationRepository pendingRepo,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider,
                       EmailService emailService) {
        this.userRepository = userRepository;
        this.pendingRepo = pendingRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.emailService = emailService;
    }

    public String register(AuthRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new RuntimeException("Email này đã được sử dụng!");
        }

        String otp = savePendingRegistration(request);

        try {
            emailService.sendOtpEmail(request.getEmail(), "", otp);
        } catch (Exception e) {
            log.warn("OTP email failed for {}: {} — user can resend from OTP screen", request.getEmail(), e.getMessage());
        }

        return "OTP đã được gửi đến email của bạn. Vui lòng kiểm tra hộp thư và nhập mã xác thực.";
    }

    @Transactional
    protected String savePendingRegistration(AuthRequest request) {
        pendingRepo.deleteByEmail(request.getEmail());

        String otp = generateOtp();
        LocalDateTime now = LocalDateTime.now();

        PendingRegistration pending = new PendingRegistration();
        pending.setEmail(request.getEmail());
        pending.setPasswordEncoded(passwordEncoder.encode(request.getPassword()));
        pending.setOtpCode(otp);
        pending.setOtpExpiresAt(now.plusMinutes(otpExpiryMinutes));
        pending.setCreatedAt(now);
        pending.setLastSentAt(now);
        pending.setAttemptCount(0);

        pendingRepo.save(pending);
        return otp;
    }

    @Transactional
    public AuthResponse verifyOtp(String email, String otpCode) {
        PendingRegistration pending = pendingRepo.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy yêu cầu đăng ký cho email này."));

        if (pending.getAttemptCount() >= maxAttempts) {
            throw new RuntimeException("Đã vượt quá số lần thử. Vui lòng đăng ký lại để nhận mã mới.");
        }

        if (LocalDateTime.now().isAfter(pending.getOtpExpiresAt())) {
            throw new RuntimeException("Mã OTP đã hết hạn. Vui lòng gửi lại mã mới.");
        }

        if (!pending.getOtpCode().equals(otpCode)) {
            pending.setAttemptCount(pending.getAttemptCount() + 1);
            pendingRepo.save(pending);
            int remaining = maxAttempts - pending.getAttemptCount();
            throw new RuntimeException("Mã OTP không đúng. Còn " + remaining + " lần thử.");
        }

        // OTP hợp lệ — tạo tài khoản
        User newUser = new User();
        newUser.setEmail(pending.getEmail());
        newUser.setPassword(pending.getPasswordEncoded());
        newUser.setBlocked(false);
        newUser.setReputationScore(100);

        userRepository.save(newUser);
        pendingRepo.deleteByEmail(email);

        String token = jwtTokenProvider.generateToken(newUser.getId(), newUser.getEmail(), newUser.getRole());

        return AuthResponse.builder()
                .id(newUser.getId())
                .email(newUser.getEmail())
                .fullName(newUser.getFullName())
                .token(token)
                .role(newUser.getRole())
                .message("Đăng ký tài khoản thành công!")
                .build();
    }

    @Transactional
    public String resendOtp(String email) {
        PendingRegistration pending = pendingRepo.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy yêu cầu đăng ký cho email này."));

        LocalDateTime cooldownEnd = pending.getLastSentAt().plusSeconds(resendCooldownSeconds);
        if (LocalDateTime.now().isBefore(cooldownEnd)) {
            long secondsLeft = java.time.Duration.between(LocalDateTime.now(), cooldownEnd).getSeconds();
            throw new RuntimeException("Vui lòng đợi " + secondsLeft + " giây trước khi gửi lại mã.");
        }

        String otp = generateOtp();
        LocalDateTime now = LocalDateTime.now();

        pending.setOtpCode(otp);
        pending.setOtpExpiresAt(now.plusMinutes(otpExpiryMinutes));
        pending.setLastSentAt(now);
        pending.setAttemptCount(0);
        pendingRepo.save(pending);

        emailService.sendOtpEmail(email, "", otp);
        return "Mã OTP mới đã được gửi đến email của bạn.";
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

    @Scheduled(fixedDelay = 300_000)
    @Transactional
    public void cleanExpiredPending() {
        pendingRepo.deleteByOtpExpiresAtBefore(LocalDateTime.now());
    }

    private String generateOtp() {
        int otp = 100_000 + RANDOM.nextInt(900_000);
        return String.valueOf(otp);
    }
}
