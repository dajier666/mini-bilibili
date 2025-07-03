package com.rfid.message.service;

import com.rfid.message.entity.Message;
import com.rfid.message.enums.MessageType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class MessageConsumer {
    @Autowired
    private MessageHandlerFactory messageHandlerFactory;

    // 发送给个人
    @KafkaListener(topics = "message_topic", groupId = "group_01")
    public void handleUserMessage(Message message) {
        if (message != null) {
            MessageHandler handler = messageHandlerFactory.getHandler(message);
            if (handler != null) {
                handler.handle(message);
            }
        }
    }

    // 发送给群体
}
