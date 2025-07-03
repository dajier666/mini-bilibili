package com.rfid.user.entity.DTO;

import lombok.Data;

import java.util.Date;

@Data
public class LoginResultDTO {
    private Long userID;            // 用户id
    private String username;        // 用户名
    private String token;           // 令牌
    private Date ExpireTime;        // 过期时间
}
