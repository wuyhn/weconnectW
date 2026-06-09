package com.weconnect.backend.service;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromAddress;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendOtpEmail(String to, String fullName, String otpCode) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            if (fromAddress != null && !fromAddress.isEmpty()) {
                helper.setFrom(fromAddress);
            }
            helper.setTo(to);
            helper.setSubject("WeConnect — Mã xác thực OTP của bạn");
            helper.setText(buildHtmlBody(fullName, otpCode), true);

            mailSender.send(message);
            log.info("OTP email sent to {}", to);
        } catch (Exception e) {
            log.error("Failed to send OTP email to {}: {}", to, e.getMessage());
            throw new RuntimeException("Không thể gửi email xác thực. Vui lòng thử lại.");
        }
    }

    private String buildHtmlBody(String fullName, String otpCode) {
        return """
                <div style="font-family:Arial,sans-serif;max-width:480px;margin:0 auto;padding:32px;border:1px solid #eee;border-radius:12px;">
                  <h2 style="color:#E85B8A;margin-bottom:4px;">WeConnect</h2>
                  <p style="color:#555;">Xin chào <strong>%s</strong>,</p>
                  <p style="color:#555;">Mã OTP để xác thực tài khoản của bạn là:</p>
                  <div style="font-size:36px;font-weight:bold;letter-spacing:10px;text-align:center;color:#E85B8A;padding:20px 0;">%s</div>
                  <p style="color:#888;font-size:13px;">Mã có hiệu lực trong <strong>5 phút</strong>. Không chia sẻ mã này với ai.</p>
                  <hr style="border:none;border-top:1px solid #eee;margin:24px 0;">
                  <p style="color:#aaa;font-size:12px;">Nếu bạn không yêu cầu đăng ký, hãy bỏ qua email này.</p>
                </div>
                """.formatted(fullName, otpCode);
    }

    // ---------------------------------------------------------------
    // Luồng đặt lại mật khẩu
    // ---------------------------------------------------------------

    /**
     * Gửi email chứa mã OTP 6 chữ số để đặt lại mật khẩu.
     *
     * @param to       địa chỉ email người nhận
     * @param fullName tên hiển thị (có thể rỗng nếu user chưa cập nhật tên)
     * @param otpCode  mã OTP 6 chữ số
     */
    public void sendPasswordResetEmail(String to, String fullName, String otpCode) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            if (fromAddress != null && !fromAddress.isEmpty()) {
                helper.setFrom(fromAddress);
            }
            helper.setTo(to);
            helper.setSubject("WeConnect — Đặt lại mật khẩu của bạn");
            helper.setText(buildPasswordResetHtmlBody(fullName, otpCode), true);

            mailSender.send(message);
            log.info("Password reset OTP email sent to {}", to);
        } catch (Exception e) {
            log.error("Failed to send password reset email to {}: {}", to, e.getMessage());
            throw new RuntimeException("Không thể gửi email đặt lại mật khẩu. Vui lòng thử lại.");
        }
    }

    private String buildPasswordResetHtmlBody(String fullName, String otpCode) {
        // Dùng "Người dùng" nếu tên chưa được đặt
        String displayName = (fullName != null && !fullName.isBlank()) ? fullName : "bạn";
        return """
                <div style="font-family:Arial,sans-serif;max-width:480px;margin:0 auto;padding:32px;border:1px solid #eee;border-radius:12px;">
                  <h2 style="color:#E85B8A;margin-bottom:4px;">WeConnect</h2>
                  <p style="color:#555;">Xin chào <strong>%s</strong>,</p>
                  <p style="color:#555;">Chúng tôi nhận được yêu cầu <strong>đặt lại mật khẩu</strong> cho tài khoản của bạn.</p>
                  <p style="color:#555;">Mã OTP xác thực của bạn là:</p>
                  <div style="font-size:36px;font-weight:bold;letter-spacing:10px;text-align:center;color:#E85B8A;padding:20px 0;background:#FFF5F8;border-radius:8px;margin:16px 0;">%s</div>
                  <p style="color:#888;font-size:13px;">Mã có hiệu lực trong <strong>5 phút</strong>. Không chia sẻ mã này với ai.</p>
                  <hr style="border:none;border-top:1px solid #eee;margin:24px 0;">
                  <p style="color:#aaa;font-size:12px;">Nếu bạn <strong>không</strong> yêu cầu đặt lại mật khẩu, hãy bỏ qua email này. Tài khoản của bạn vẫn an toàn.</p>
                </div>
                """.formatted(displayName, otpCode);
    }
}
