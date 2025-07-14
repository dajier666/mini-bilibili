package com.rfid.video.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LogMessage {
    private String timestamp;
    private String level;
    private String logger;
    private String message;
    private String thread;

    // 构造函数
    public LogMessage(String level, String logger, String message, String thread) {
        this.timestamp = LocalDateTime.now().toString();
        this.level = level;
        this.logger = logger;
        this.message = message;
        this.thread = thread;
    }
}
