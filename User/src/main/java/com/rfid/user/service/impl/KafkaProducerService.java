package com.rfid.user.service.impl;

import com.rfid.user.entity.Message;
import com.rfid.user.enums.MessageType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class KafkaProducerService {
    private final KafkaTemplate<String, Message> kafkaTemplate;
    private static final String TOPIC = "message_topic";

    public KafkaProducerService(KafkaTemplate<String, Message> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }
    //发送关注消息
    @Async
    public void sendFollowMessage(Long userId, Long targetId)  {
        Message message = new Message();
        message.setUserId(userId);
        message.setTargetId(targetId);
        message.setType(MessageType.FOLLOW.getCode());
        message.setCreateTime(LocalDateTime.now());
        kafkaTemplate.send(TOPIC, message);
    }
}