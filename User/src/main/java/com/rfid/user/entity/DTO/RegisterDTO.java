package com.rfid.user.entity;

import lombok.Data;

@Data
public class RegisterDTO {
    private String username;        // 用户名
    private String password;        // 密码
    private String email;           // 邮箱
    private String phone;           // 手机号
}