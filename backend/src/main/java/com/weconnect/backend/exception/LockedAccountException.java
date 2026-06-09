package com.weconnect.backend.exception;

import java.time.LocalDateTime;

/**
 * Ném ra khi tài khoản đang bị khóa tạm thời (status = LOCKED_TEMP).
 * Mang theo lockUntil để client hiển thị ngày mở khóa chính xác.
 * GlobalExceptionHandler sẽ bắt ngoại lệ này và trả về HTTP 423 Locked.
 */
public class LockedAccountException extends RuntimeException {

    private final LocalDateTime lockUntil;

    public LockedAccountException(String message, LocalDateTime lockUntil) {
        super(message);
        this.lockUntil = lockUntil;
    }

    public LocalDateTime getLockUntil() {
        return lockUntil;
    }
}
