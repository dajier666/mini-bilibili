package com.rfid.video.entity;

import lombok.Data;

@Data
public class VideoUpdateMessage {
    private String eventType; // INSERT, UPDATE, DELETE
    private String tableName;
    private Long primaryKey;
    private String title;
    private String description;
    private String category;
    private String tags;
    // 其他需要的字段
}