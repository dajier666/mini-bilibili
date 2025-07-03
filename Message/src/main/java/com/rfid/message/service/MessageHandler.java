package com.rfid.message.service;

import com.rfid.message.entity.Message;

public interface MessageHandler {
    void handle(Message message);
}