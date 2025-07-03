package com.rfid.user.entity;

import lombok.Data;

import java.time.LocalDateTime;

// 用户关系实体
@Data
public class UserRelation {
    private Long id;                // 关系ID
    private Long userId;            // 用户ID
    private Long targetId;          // 目标用户ID
    private Integer type;           // 关系类型(1关注,2粉丝,3黑名单)
    private LocalDateTime createTime; // 创建时间
    // getter/setter
}