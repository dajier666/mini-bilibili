package com.rfid.message.service.Impl;

import com.rfid.message.entity.GroupMessage;
import com.rfid.message.entity.Message;
import com.rfid.message.enums.MessageType;
import com.rfid.message.service.MessageHandler;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

@Service
public class MessageConsumer {
    @Autowired
    private MessageHandlerFactory messageHandlerFactory;

    @Autowired
    private MessageMapperServiceImpl messageMapperServiceImpl;

    @Autowired
    private MessageCacheServiceImpl messageCacheServiceImpl;

    private final List<Message> messageCache = new ArrayList<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final Object lock = new Object();

    @PostConstruct
    public void init() {
        // 每 2 秒执行一次写入操作
        scheduler.scheduleAtFixedRate(this::flushMessages, 2, 2, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void destroy() {
        // 关闭前执行一次写入操作
        flushMessages();
        scheduler.shutdown();
    }

    // 发送给个人
    @KafkaListener(topics = "message_topic", groupId = "group_01")
    public void handleUserMessage(Message message) {
        if (message != null) {
            MessageHandler handler = messageHandlerFactory.getHandler(message);
            if (handler != null) {
                handler.handle(message);
            }
            cacheMessage(message);
        }
    }

    // 发送给群体
    @KafkaListener(topics = "group_message_topic", groupId = "group_01")
    public void handleGroupMessage(GroupMessage groupMessage) {
        if (groupMessage != null) {
            for (Long targetId : groupMessage.getGroupIds()) {
                Message message = GroupMessageToMessage(groupMessage, targetId);
                handleUserMessage(message);
            }
        }
    }


    // 缓存消息
    private void cacheMessage(Message message) {
        synchronized (lock) {
            messageCache.add(message);
            messageCacheServiceImpl.cacheMessagesBetweenUsers(message.getUserId(), message.getTargetId(), List.of(message));
            if (messageCache.size() >= 1000) {
                flushMessages();
            }
        }
    }

    private void flushMessages() {
        List<Message> messagesToFlush;
        synchronized (lock) {
            if (messageCache.isEmpty()) {
                return;
            }
            messagesToFlush = new ArrayList<>(messageCache);
            messageCache.clear();
        }
        // 使用存储服务存储消息
        messageMapperServiceImpl.saveBatch(messagesToFlush);
    }

    // 群发消息转换为个人消息
    public Message GroupMessageToMessage(GroupMessage groupMessage, Long TargetId) {
        Message message = new Message();
        message.setTargetId(TargetId);
        message.setContent(groupMessage.getContent());
        message.setCreateTime(groupMessage.getCreateTime());
        message.setUserId(groupMessage.getUserId());
        message.setType(MessageType.GROUP.getCode());
        return message;
    }
}
