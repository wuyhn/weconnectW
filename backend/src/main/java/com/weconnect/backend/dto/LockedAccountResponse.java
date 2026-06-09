package com.weconnect.backend.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Body trả về trong phần "result" kèm HTTP 423 khi tài khoản bị khóa tạm thời.
 * lockUntil được serialize theo ISO-8601 để Android client parse bằng SimpleDateFormat.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LockedAccountResponse {

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime lockUntil;
}
