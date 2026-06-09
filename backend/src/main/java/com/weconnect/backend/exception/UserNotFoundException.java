package com.weconnect.backend.exception;

/**
 * Ném khi không tìm thấy tài khoản theo email trong luồng quên mật khẩu.
 * Controller bắt exception này và trả về HTTP 404 thay vì 400 thông thường.
 */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(String message) {
        super(message);
    }
}
