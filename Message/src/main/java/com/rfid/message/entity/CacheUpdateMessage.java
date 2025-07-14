package com.rfid.message.entity;

import lombok.Data;

@Data
public class CacheUpdateMessage {
    private String eventType; // INSERT, UPDATE, DELETE
    private String tableName;
    private Long primaryKey;
    private Long userId;
    private Long targetId;
    // 可添加更多字段
}