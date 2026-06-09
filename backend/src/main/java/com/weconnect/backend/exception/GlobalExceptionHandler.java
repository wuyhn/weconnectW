package com.weconnect.backend.exception;

import com.weconnect.backend.dto.LockedAccountResponse;
import com.weconnect.backend.dto.request.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handlingMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .filter(errorMessage -> errorMessage != null && !errorMessage.isBlank())
                .findFirst()
                .orElse("Dữ liệu yêu cầu không hợp lệ.");

        return ResponseEntity.badRequest().body(
                ApiResponse.<Void>builder()
                        .code(1005)
                        .message(message)
                        .build()
        );
    }

    @ExceptionHandler(value = IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handlingIllegalArgument(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(
                ApiResponse.<Void>builder()
                        .code(1005)
                        .message(exception.getMessage())
                        .build()
        );
    }

    /**
     * Kịch bản 1 & 2 & 3: Tài khoản bị khóa tạm thời (LOCKED_TEMP).
     * Trả về HTTP 423 Locked kèm lockUntil để client hiển thị ngày mở khóa.
     * Phải đứng trước handler RuntimeException để không bị che khuất.
     */
    @ExceptionHandler(value = LockedAccountException.class)
    public ResponseEntity<ApiResponse<LockedAccountResponse>> handlingLockedAccount(
            LockedAccountException exception) {
        return ResponseEntity.status(423).body(
                ApiResponse.<LockedAccountResponse>builder()
                        .code(1007)
                        .message(exception.getMessage())
                        .result(LockedAccountResponse.builder()
                                .lockUntil(exception.getLockUntil())
                                .build())
                        .build()
        );
    }

    @ExceptionHandler(value = RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handlingRuntimeException(RuntimeException exception) {
        return ResponseEntity.status(401).body(
                ApiResponse.<Void>builder()
                        .code(1001)
                        .message(exception.getMessage())
                        .build()
        );
    }
}
