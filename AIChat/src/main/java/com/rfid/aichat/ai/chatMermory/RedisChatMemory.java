package com.rfid.aichat.ai.chatMermory;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;

import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 使用Redisson实现的分布式对话记忆存储
 */
public class RedisChatMemory implements ChatMemory {

    private final String memoryId;
    private final RMap<String, Object> memoryMap;
    private final int maxMessages;
    private final Duration ttl;

    /**
     * 创建Redisson对话记忆实例
     * @param redissonClient Redisson客户端
     * @param memoryId 记忆ID，用于区分不同对话
     * @param maxMessages 最大消息数量
     * @param ttl 记忆的过期时间
     */
    public RedisChatMemory(RedissonClient redissonClient, String memoryId, int maxMessages, Duration ttl) {
        this.memoryId = memoryId;
        this.memoryMap = redissonClient.getMap("langchain4j:chat:memory:" + memoryId);
        this.maxMessages = maxMessages;
        this.ttl = ttl;

        // 设置过期时间
        if (ttl != null && !ttl.isZero() && !ttl.isNegative()) {
            this.memoryMap.expire(ttl);
        }
    }

    /**
     * 创建默认配置的Redisson对话记忆构建器
     */
    public static RedissonChatMemoryBuilder builder() {
        return new RedissonChatMemoryBuilder();
    }

    @Override
    public List<ChatMessage> messages() {
        // 从Redisson获取消息列表
        List<?> messages = (List<?>) memoryMap.get("messages");
        if (messages == null) {
            return List.of();
        }

        // 转换为ChatMessage列表
        return messages.stream()
                .filter(m -> m instanceof ChatMessage)
                .map(m -> (ChatMessage) m)
                .collect(Collectors.toList());
    }


    @Override
    public void clear() {
        memoryMap.clear();
        // 重新设置过期时间
        if (ttl != null && !ttl.isZero() && !ttl.isNegative()) {
            memoryMap.expire(ttl);
        }
    }

    @Override
    public String id() {
        return memoryId;
    }

    @Override
    public void add(ChatMessage chatMessage) {
        updateMessages(messages -> {
            messages.add(chatMessage);
            // 确保不超过最大消息数量
            trimMessagesIfNeeded(messages);
        });
    }

    private void updateMessages(java.util.function.Consumer<List<ChatMessage>> updater) {
        List<ChatMessage> messages = messages();
        updater.accept(messages);
        memoryMap.put("messages", messages);
        // 更新过期时间
        if (ttl != null && !ttl.isZero() && !ttl.isNegative()) {
            memoryMap.expire(ttl);
        }
    }

    private void trimMessagesIfNeeded(List<ChatMessage> messages) {
        if (maxMessages <= 0) {
            return;
        }

        while (messages.size() > maxMessages) {
            messages.remove(0); // 移除最早的消息
        }
    }

    /**
     * Redisson对话记忆构建器
     */
    public static class RedissonChatMemoryBuilder {
        private RedissonClient redissonClient;
        private String memoryId = UUID.randomUUID().toString();
        private int maxMessages = 10;
        private Duration ttl = Duration.ofHours(24);

        public RedissonChatMemoryBuilder redissonClient(RedissonClient redissonClient) {
            this.redissonClient = redissonClient;
            return this;
        }

        public RedissonChatMemoryBuilder memoryId(String memoryId) {
            this.memoryId = memoryId;
            return this;
        }

        public RedissonChatMemoryBuilder maxMessages(int maxMessages) {
            this.maxMessages = maxMessages;
            return this;
        }

        public RedissonChatMemoryBuilder ttl(Duration ttl) {
            this.ttl = ttl;
            return this;
        }

        public RedisChatMemory build() {
            if (redissonClient == null) {
                throw new IllegalArgumentException("RedissonClient must be set");
            }
            return new RedisChatMemory(redissonClient, memoryId, maxMessages, ttl);
        }
    }
}