package com.rfid.message.service;

import com.rfid.message.entity.Message;

import java.time.LocalDateTime;
import java.util.List;

public interface MessageStorageService {
    void storeMessage(Message message);
    List<Message> getMessagesBySessionId(String sessionId, LocalDateTime start, LocalDateTime end);
    // 其他操作方法
}