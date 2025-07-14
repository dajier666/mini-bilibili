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
    //发送系统消息
    @Async
    public void sendSystemMessage(Long userId, String content)  {
        Message message = new Message();
        message.setUserId(userId);
        message.setContent(content);
        message.setType(MessageType.SYSTEM.getCode());
        message.setCreateTime(LocalDateTime.now());
        kafkaTemplate.send(TOPIC, message);
    }
    //发送推广消息
    @Async
    public void sendPromotionMessage(Long userId, String content)  {
        Message message = new Message();
        message.setUserId(userId);
        message.setContent(content);
        message.setType(MessageType.PROMOTION.getCode());
        message.setCreateTime(LocalDateTime.now());
        kafkaTemplate.send(TOPIC, message);
    }
    //发送私信消息
    @Async
    public void sendPrivateMessage(Long userId, Long targetId, String content)  {
        Message message = new Message();
        message.setUserId(userId);
        message.setTargetId(targetId);
        message.setContent(content);
        message.setType(MessageType.PRIVATE.getCode());
        message.setCreateTime(LocalDateTime.now());
        kafkaTemplate.send(TOPIC, message);
    }
}