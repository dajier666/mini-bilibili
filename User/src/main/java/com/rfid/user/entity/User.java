package com.rfid.user.entity;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

// 用户核心实体
@Data
public class User {
    private Long id;                // 用户ID
    private String username;        // 用户名
    private String password;        // 加密密码
    private String email;           // 邮箱
    private String phone;           // 手机号
    private Integer gender;         // 性别
    private LocalDate birthday;     // 生日
    private String avatar;          // 头像URL
    private String signature;       // 个性签名
    private Integer status;         // 用户状态(1正常,0封禁,-1注销)
    private LocalDateTime createTime; // 创建时间
    private LocalDateTime updateTime; // 更新时间
}
