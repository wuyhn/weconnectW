package com.weconnect.backend.service;

import com.weconnect.backend.dto.AuthRequest;
import com.weconnect.backend.dto.AuthResponse;
import com.weconnect.backend.dto.RegisterRequest;
import com.weconnect.backend.entity.PasswordResetOtp;
import com.weconnect.backend.entity.PendingRegistration;
import com.weconnect.backend.entity.User;
import com.weconnect.backend.exception.AccountSanctionException;
import com.weconnect.backend.exception.LockedAccountException;
import com.weconnect.backend.exception.UserNotFoundException;
import com.weconnect.backend.repository.PasswordResetOtpRepository;
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
import java.util.regex.Pattern;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(RegisterRequest.PASSWORD_REGEX);

    private final UserRepository userRepository;
    private final PendingRegistrationRepository pendingRepo;
    private final PasswordResetOtpRepository passwordResetOtpRepo;
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
                       PasswordResetOtpRepository passwordResetOtpRepo,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider,
                       EmailService emailService) {
        this.userRepository = userRepository;
        this.pendingRepo = pendingRepo;
        this.passwordResetOtpRepo = passwordResetOtpRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.emailService = emailService;
    }

    @Transactional
    public String register(RegisterRequest request) {
        validateRegisterPassword(request.getPassword());
        String email = request.getEmail().trim();

        userRepository.findByEmail(email).ifPresent(existingUser -> {
            if (User.STATUS_PENDING.equals(existingUser.getStatus())) {
                // Dọn dữ liệu legacy nếu trước đây hệ thống từng tạo User ở trạng thái PENDING.
                // User ACTIVE/LOCKED/BANNED vẫn được xem là email đã thuộc về một tài khoản thật.
                userRepository.deleteByEmailAndStatus(email, User.STATUS_PENDING);
                return;
            }
            throw new RuntimeException("Email này đã được sử dụng!");
        });

        // Luồng đăng ký hiện tại lưu dữ liệu chờ xác thực ở bảng pending_registrations.
        // Nếu user quay lại từ màn OTP và đăng ký lại cùng email, bản ghi pending cũ và OTP cũ
        // được xóa trong cùng transaction, sau đó lưu mật khẩu mới và OTP mới.
        pendingRepo.deleteByEmail(email);
        String otp = generateOtp();
        LocalDateTime now = LocalDateTime.now();

        PendingRegistration pending = new PendingRegistration();
        pending.setEmail(email);
        pending.setPasswordEncoded(passwordEncoder.encode(request.getPassword()));
        pending.setOtpCode(otp);
        pending.setOtpExpiresAt(now.plusMinutes(otpExpiryMinutes));
        pending.setCreatedAt(now);
        pending.setLastSentAt(now);
        pending.setAttemptCount(0);

        pendingRepo.save(pending);

        try {
            emailService.sendOtpEmail(email, "", otp);
        } catch (Exception e) {
            log.warn("OTP email failed for {}: {} — user can resend from OTP screen", email, e.getMessage());
        }

        return "OTP đã được gửi đến email của bạn. Vui lòng kiểm tra hộp thư và nhập mã xác thực.";
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

        // Tài khoản mới luôn bắt đầu ở trạng thái sạch: 60 điểm uy tín, chưa có phạt và đang ACTIVE.
        newUser.setReputationScore(60);
        newUser.setViolationPenaltySum(0);
        newUser.setViolationCount(0);
        newUser.setStatus(User.STATUS_ACTIVE);

        userRepository.save(newUser);
        pendingRepo.deleteByEmail(email);

        String token = jwtTokenProvider.generateToken(newUser.getId(), newUser.getEmail(), newUser.getRole());

        return AuthResponse.builder()
                .id(newUser.getId())
                .email(newUser.getEmail())
                .fullName(newUser.getFullName())
                .token(token)
                .role(newUser.getRole())
                .reputationScore(newUser.getReputationScore())
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

    @Transactional
    public AuthResponse login(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException(
                        "Tài khoản không tồn tại. Vui lòng kiểm tra lại hoặc đăng ký tài khoản mới."));

        if (User.STATUS_LOCKED_TEMP.equals(user.getStatus())) {
            LocalDateTime lockUntil = user.getLockUntil();
            if (lockUntil != null && LocalDateTime.now().isAfter(lockUntil)) {
                // Khóa tạm đã hết hạn: mở lại tài khoản ngay trong transaction login.
                // Không gọi recalculateReputation() ở đây vì penalty cũ có thể kéo điểm về 0 lại ngay.
                user.setStatus(User.STATUS_ACTIVE);
                user.setBlocked(false);
                user.setLockUntil(null);

                // Cấp lại mức sàn 30 điểm để user có cơ hội tích lũy uy tín sau thời gian phạt.
                user.setReputationScore(30);

                // Khấu trừ bớt penalty tích lũy để lần recalculate sau không lập tức kéo điểm về 0.
                user.setViolationPenaltySum(Math.max(0, user.getViolationPenaltySum() - 30));
                userRepository.save(user);
            } else {
                // Kịch bản 3: User bị khóa cố tình đăng nhập lại.
                // Ném LockedAccountException (thay AccountSanctionException) để AuthController
                // trả về HTTP 423 kèm lockUntil — Android hiển thị Toast với ngày mở khóa.
                throw new LockedAccountException(
                        "Tài khoản đang bị khóa tạm thời do vi phạm chính sách uy tín. Vui lòng thử lại sau.",
                        lockUntil);
            }
        }

        if (User.STATUS_BANNED.equals(user.getStatus())) {
            throw new AccountSanctionException(
                    "Tài khoản của bạn đã bị khóa vĩnh viễn do vi phạm nghiêm trọng tiêu chuẩn cộng đồng.");
        }

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
                .reputationScore(user.getReputationScore())
                .message("Đăng nhập thành công!")
                .build();
    }

    // ---------------------------------------------------------------
    // Luồng quên mật khẩu — Endpoint 1: Gửi OTP về email
    // ---------------------------------------------------------------

    /**
     * Bước 1: Người dùng nhập email, hệ thống sinh OTP và gửi về hộp thư.
     * <p>
     * - Nếu email không tồn tại trong DB → ném {@link UserNotFoundException} (→ HTTP 404).
     * - Nếu đã có OTP cũ của email này → ghi đè (xóa cũ, tạo mới) để tránh bản ghi thừa.
     * - OTP có hiệu lực {@code otpExpiryMinutes} phút (mặc định 5 phút từ application.properties).
     *
     * @param email email của tài khoản cần đặt lại mật khẩu
     * @return thông báo thành công
     * @throws UserNotFoundException nếu email không tồn tại trong hệ thống
     */
    @Transactional
    public String forgotPassword(String email) {
        // Kiểm tra email có tồn tại không — ném 404 nếu không tìm thấy
        User user = userRepository.findByEmail(email.trim())
                .orElseThrow(() -> new UserNotFoundException(
                        "Email này chưa được đăng ký trong hệ thống."));

        // Xóa OTP cũ nếu có (ghi đè — mỗi email chỉ có 1 OTP reset tại một thời điểm)
        passwordResetOtpRepo.findByEmail(email.trim()).ifPresent(old ->
                passwordResetOtpRepo.deleteByEmail(email.trim()));

        // Sinh OTP mới và lưu vào DB
        String otpCode = generateOtp();
        LocalDateTime now = LocalDateTime.now();

        PasswordResetOtp resetOtp = new PasswordResetOtp();
        resetOtp.setEmail(email.trim());
        resetOtp.setOtpCode(otpCode);
        resetOtp.setExpiryDate(now.plusMinutes(otpExpiryMinutes));
        resetOtp.setCreatedAt(now);
        passwordResetOtpRepo.save(resetOtp);

        // Gửi email HTML kèm OTP cho người dùng
        String fullName = user.getFullName() != null ? user.getFullName() : "";
        emailService.sendPasswordResetEmail(email.trim(), fullName, otpCode);

        log.info("Password reset OTP generated for email: {}", email);
        return "Mã OTP đã được gửi đến email của bạn. Vui lòng kiểm tra hộp thư.";
    }

    // ---------------------------------------------------------------
    // Luồng quên mật khẩu — Endpoint 2: Xác thực OTP + đặt mật khẩu mới
    // ---------------------------------------------------------------

    /**
     * Bước 2: Người dùng nhập OTP từ email + mật khẩu mới để hoàn tất đặt lại.
     * <p>
     * Kiểm tra theo thứ tự:
     * <ol>
     *   <li>Bản ghi OTP có tồn tại cho email này không → 400 nếu không</li>
     *   <li>OTP đã hết hạn chưa → 400 nếu quá hạn</li>
     *   <li>Mã OTP có khớp không → 400 nếu sai</li>
     *   <li>Mật khẩu mới hợp lệ (không rỗng, ≥ 6 ký tự) → 400 nếu không đạt</li>
     * </ol>
     * Sau khi xác thực thành công: mã hóa mật khẩu mới bằng BCrypt, lưu vào User,
     * xóa bản ghi OTP đã dùng.
     *
     * @param email       email tài khoản
     * @param otpCode     mã OTP 6 chữ số từ email
     * @param newPassword mật khẩu mới (plain text, sẽ được BCrypt trước khi lưu)
     * @return thông báo thành công
     * @throws RuntimeException nếu OTP không hợp lệ, hết hạn, hoặc mật khẩu không đạt yêu cầu
     */
    @Transactional
    public String resetPassword(String email, String otpCode, String newPassword) {
        // Kiểm tra bản ghi OTP có tồn tại không
        PasswordResetOtp resetOtp = passwordResetOtpRepo.findByEmail(email.trim())
                .orElseThrow(() -> new RuntimeException(
                        "Không tìm thấy yêu cầu đặt lại mật khẩu. Vui lòng gửi lại mã OTP."));

        // Kiểm tra OTP có hết hạn chưa
        if (LocalDateTime.now().isAfter(resetOtp.getExpiryDate())) {
            throw new RuntimeException("Mã OTP đã hết hạn. Vui lòng gửi lại mã mới.");
        }

        // Kiểm tra OTP có đúng không
        if (!resetOtp.getOtpCode().equals(otpCode)) {
            throw new RuntimeException("Mã OTP không đúng. Vui lòng kiểm tra lại.");
        }

        // Kiểm tra mật khẩu mới hợp lệ (tối thiểu 6 ký tự)
        if (newPassword == null || newPassword.trim().length() < 6) {
            throw new RuntimeException("Mật khẩu mới phải có ít nhất 6 ký tự.");
        }

        // Lấy tài khoản và cập nhật mật khẩu
        User user = userRepository.findByEmail(email.trim())
                .orElseThrow(() -> new RuntimeException("Tài khoản không tồn tại."));

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Xóa OTP đã dùng — tránh tái sử dụng mã cũ
        passwordResetOtpRepo.deleteByEmail(email.trim());

        log.info("Password successfully reset for email: {}", email);
        return "Đặt lại mật khẩu thành công! Vui lòng đăng nhập bằng mật khẩu mới.";
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

    private void validateRegisterPassword(String password) {
        // Lớp DTO đã có @Pattern để chặn request HTTP sai định dạng. Kiểm tra này là lớp phòng vệ
        // cho các luồng gọi service trực tiếp, đảm bảo không bao giờ lưu mật khẩu yếu vào pending registration.
        if (password == null || !PASSWORD_PATTERN.matcher(password).matches()) {
            throw new IllegalArgumentException(RegisterRequest.PASSWORD_SECURITY_MESSAGE);
        }
    }
}
