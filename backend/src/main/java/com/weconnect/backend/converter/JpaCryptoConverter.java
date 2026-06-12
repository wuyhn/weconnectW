package com.weconnect.backend.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * JPA Attribute Converter: tự động mã hóa khi ghi xuống DB, giải mã khi đọc lên.
 * Dùng AES-256-GCM — mã hóa có xác thực, chống tamper.
 *
 * Gắn @Convert(converter = JpaCryptoConverter.class) lên field muốn mã hóa.
 * Mọi service/repository gọi entity đều được giải mã ngầm — code nghiệp vụ không đổi.
 */
@Converter
@Component
public class JpaCryptoConverter implements AttributeConverter<String, String> {

    private static final String ALGORITHM    = "AES/GCM/NoPadding";
    private static final int    IV_LENGTH    = 12;   // 96-bit IV — khuyến nghị cho GCM
    private static final int    TAG_BITS     = 128;  // auth tag mặc định GCM

    private static final SecureRandom RANDOM = new SecureRandom();

    /** Key 256-bit: derive từ secret bằng SHA-256. */
    private byte[] aesKey;

    @Value("${app.encryption.secret}")
    public void setSecret(String secret) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            this.aesKey = Arrays.copyOf(
                    sha.digest(secret.getBytes(StandardCharsets.UTF_8)), 32);
        } catch (Exception e) {
            throw new IllegalStateException("JpaCryptoConverter: không thể khởi tạo key", e);
        }
    }

    /** Ghi xuống DB: plaintext → Base64(IV + ciphertext+tag) */
    @Override
    public String convertToDatabaseColumn(String plaintext) {
        if (plaintext == null) return null;
        try {
            byte[] iv = new byte[IV_LENGTH];
            RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(aesKey, "AES"),
                    new GCMParameterSpec(TAG_BITS, iv));

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // Format: [IV 12 bytes][ciphertext + GCM tag 16 bytes]
            byte[] combined = new byte[IV_LENGTH + ciphertext.length];
            System.arraycopy(iv,         0, combined, 0,         IV_LENGTH);
            System.arraycopy(ciphertext, 0, combined, IV_LENGTH, ciphertext.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Mã hóa tin nhắn thất bại", e);
        }
    }

    /** Đọc lên từ DB: Base64(IV + ciphertext+tag) → plaintext */
    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        try {
            byte[] combined    = Base64.getDecoder().decode(dbData);
            byte[] iv          = Arrays.copyOfRange(combined, 0, IV_LENGTH);
            byte[] ciphertext  = Arrays.copyOfRange(combined, IV_LENGTH, combined.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(aesKey, "AES"),
                    new GCMParameterSpec(TAG_BITS, iv));

            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            // Backward-compat: data cũ còn là plaintext → trả về nguyên bản
            return dbData;
        }
    }
}
