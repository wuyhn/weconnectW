package com.weconnect.backend.dto;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Getter
@Setter
public class AuthRequest {
    private String email;
    private String password;
    private String fullName; // Dùng thêm trường này khi Đăng ký
    private String birthday; // Phải trùng tên với Android
    private String gender;
}
