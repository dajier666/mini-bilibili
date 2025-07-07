fid\message\service\Impl\MessageService.java
package com.rfid.message.service.Impl;

import com.rfid.message.entity.DTO.MessageDTO;
import com.rfid.message.entity.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Service
public class MessageService {
    @Autowired
    private MessageMapperServiceImpl messageMapperServiceImpl;

    @Autowired
    private RedisTemplate<String, Message> redisTemplate;

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    // 存储消息时，根据创建时间选择存储层
    public void saveMessage(Message message) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime sevenDaysAgo = now.minus(7, ChronoUnit.DAYS);
        LocalDateTime threeMonthsAgo = now.minus(3, ChronoUnit.MONTHS);

        if (message.getCreateTime().isAfter(sevenDaysAgo)) {
            // 热数据层：Redis
            String sessionId = getSessionId(message.getUserId(), message.getTargetId());
            redisTemplate.opsForList().leftPush(sessionId, message);
        } else if (message.getCreateTime().isAfter(threeMonthsAgo)) {
            // 温数据层：Elasticsearch
            String indexName = "message-" + message.getCreateTime().getYear() + "-" + String.format("%02d", message.getCreateTime().getMonthValue());
            elasticsearchOperations.save(message, elasticsearchOperations.getIndexCoordinatesFor(Message.class).withSuffix(indexName));
        } else {
            // 冷数据层：MySQL
            messageMapperServiceImpl.save(message);
        }
    }

    // 获取会话 ID
    private String getSessionId(Long userId, Long targetId) {
        return userId < targetId ? userId + "-" + targetId : targetId + "-" + userId;
    }

    /**
     * 获取一个用户与其他所有用户的最近一条消息以及未读消息数量
     * @param userId 当前用户 ID
     * @return 包含对方用户 ID、最近消息、未读消息数量的列表
     */
    public MessageDTO getLatestMessagesAndUnreadCounts(Long userId) {
        // 先从 Redis 查找热数据
        // 若未找到，再从 Elasticsearch 查找温数据
        // 最后从 MySQL 查找冷数据
        // 此处仅为示例，实际需实现完整逻辑
        return messageMapperServiceImpl.getLatestMessagesAndUnreadCounts(userId);
    }

    /**
     * 获取与某个用户的所有消息
     * @param userId 当前用户 ID
     * @param targetUserId 目标用户 ID
     * @return 消息列表
     */
    public List<Map<String, Object>> getAllMessagesBetweenUsers(Long userId, Long targetUserId) {
        // 先从 Redis 查找热数据
        // 若未找到，再从 Elasticsearch 查找温数据
        // 最后从 MySQL 查找冷数据
        // 此处仅为示例，实际需实现完整逻辑
        return messageMapperServiceImpl.getAllMessagesBetweenUsers(userId, targetUserId);
    }

    /**
     * 删除消息
     * @param messageId 消息 ID
     * @return 删除成功返回 true，失败返回 false
     */
    public boolean deleteMessage(Long messageId) {
        // 需要从三个存储层都删除对应消息
        // 此处仅为示例，实际需实现完整逻辑
        int result = messageMapperServiceImpl.deleteMessage(messageId);
        return result > 0;
    }
}

fid\message\service\Impl\MessageConsumer.java
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
    private MessageService messageService;

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
                MessageHandler handler = messageHandlerFactory.getHandler(message);
                if (handler != null) {
                    handler.handle(message);
                }
                cacheMessage(message);
            }
        }
    }

    // 缓存消息
    private void cacheMessage(Message message) {
        synchronized (lock) {
            messageCache.add(message