package com.rfid.user.entity.DTO;

import lombok.Data;

@Data
public class LoginDTO {
    private String phone;           // 用户电话
    private String email;           // 用户邮箱
    private String username;        // 用户名
    private String password;        // 密码
}
