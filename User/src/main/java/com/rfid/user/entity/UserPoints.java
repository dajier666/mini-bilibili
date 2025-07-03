package com.rfid.user.entity;

import lombok.Data;

import java.time.LocalDateTime;

// 用户积分实体
@Data
public class UserPoints {
    private Long id;                // ID
    private Long userId;            // 用户ID
    private Integer points;         // 当前积分
    private Integer level;          // 用户等级
    private Integer exp;            // 经验值
    private LocalDateTime lastUpdateTime; // 最后更新时间
    // getter/setter
}