package com.rfid.message.service.Impl;

import com.github.benmanes.caffeine.cache.*;
import com.rfid.message.entity.CacheUpdateMessage;
import com.rfid.message.entity.Message;
import com.rfid.message.task.BloomFilterTask;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.*;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 消息缓存服务实现类，负责管理消息相关的本地缓存和Redis缓存
 */
@Slf4j
@Service
public class  MessageCacheServiceImpl {
    // Redisson客户端，用于操作Redis分布式数据结构
    private final RedissonClient redissonClient;
    // 消息映射服务，用于数据库操作
    private final MessageMapperServiceImpl messageService;

    // Caffeine本地缓存：存储两用户间的热门消息会话
    private final LoadingCache<String, List<Message>> hotConversations;

    // 热点会话追踪器：记录每个会话的访问次数
    private final ConcurrentHashMap<String, AtomicInteger> conversationAccessCounter;

    // 热点用户ID集合：使用线程安全的CopyOnWriteArraySet存储
    private final CopyOnWriteArraySet<Long> hotUserIds;

    // 缓存加载线程池：用于异步加载缓存数据
    private final ExecutorService cacheLoaderExecutor;

    // 缓存加载超时时间（毫秒）
    private static final long CACHE_LOAD_TIMEOUT = 500;

    // 布隆过滤器：用于快速判断用户是否存在，防止缓存穿透
    private final RBloomFilter<Long> userBloomFilter;
    /**
     * 构造函数，初始化各种缓存和组件
     */
    public MessageCacheServiceImpl(RedissonClient redissonClient,
                            MessageMapperServiceImpl messageService,
                                   BloomFilterTask bloomFilterTask) {
        this.redissonClient = redissonClient;  // 注入Redisson客户端，用于分布式锁和Redis操作
        this.messageService = messageService;  // 注入消息服务，用于数据库操作
        this.userBloomFilter = bloomFilterTask.getUserBloomFilter();  // 获取用户布隆过滤器，用于快速判断用户是否存在

        // 配置缓存加载线程池
        this.cacheLoaderExecutor = Executors.newFixedThreadPool(20, new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                // 创建命名线程，便于问题排查
                return new Thread(r, "cache-loader-" + threadNumber.getAndIncrement());
            }
        });

        // 配置热点会话本地缓存（启用异步加载）
        this.hotConversations = Caffeine.newBuilder()
                .maximumSize(2000)  // 设置缓存最大容量为2000条
                .expireAfterAccess(1, TimeUnit.HOURS)  // 设置访问后1小时过期
                .weigher((String key, List<Message> value) -> Math.min(value.size(), 100))  // 设置权重计算方式
                .executor(cacheLoaderExecutor)  // 指定缓存加载使用的线程池
                .recordStats()  // 开启缓存统计功能
                .build(this::loadMessagesFromRedis);  // 设置缓存加载方法

        // 初始化会话访问计数器，使用线程安全的ConcurrentHashMap
        this.conversationAccessCounter = new ConcurrentHashMap<>();

        // 初始化热点用户集合，使用线程安全的CopyOnWriteArraySet
        this.hotUserIds = new CopyOnWriteArraySet<>();
    }

    // 缓存预热：启动时加载热门数据和初始化布隆过滤器
    @PostConstruct
    public void initCache() {
        log.info("Starting cache preheating...");

        // 任务1：加载热点用户ID
        CompletableFuture<Void> hotUserIdsFuture = CompletableFuture.runAsync(
                this::loadHotUserIds, cacheLoaderExecutor
        );

        // 任务2：依赖热点用户ID加载完成后，加载热点用户消息
        CompletableFuture<Void> hotUserMessagesFuture = hotUserIdsFuture.thenRunAsync(
                this::loadHotUserMessages, cacheLoaderExecutor
        );

        // 等待所有任务完成，处理异常
        CompletableFuture.allOf(
                hotUserIdsFuture,
                hotUserMessagesFuture
        ).exceptionally(ex -> {
            log.error("Cache preheating failed", ex);
            return null;
        }).join();

        log.info("Cache preheating completed");
    }

    // 检测热点数据
    @Scheduled(fixedRate = 600_000) // 每10分钟执行一次（600,000毫秒）
    public void detectHotData() {
        try {
            log.info("Starting hot data detection...");
            detectHotUsers();
            detectHotConversations();
            resetAccessCounters();
            log.info("Hot data detection completed");
        } catch (Exception e) {
            log.error("Hot data detection failed", e);
        }
    }

    // 处理Kafka消息
    @KafkaListener(topics = "message_cache_topic", groupId = "message-cache-group")
    public void handleCacheUpdateMessage(CacheUpdateMessage updateMessage) {
        try {
            // 根据事件类型更新缓存
            if ("INSERT".equals(updateMessage.getEventType())) {
                // 从数据库加载最新消息
                Message message = messageService.getById(updateMessage.getPrimaryKey());
                if (message != null) {
                    cacheMessagesBetweenUsers(updateMessage.getUserId(), updateMessage.getTargetId(), Collections.singletonList(message));
                }
            } else if ("DELETE".equals(updateMessage.getEventType())||
                    "UPDATE".equals(updateMessage.getEventType())) {
                clearMessagesBetweenUsers(updateMessage.getUserId(), updateMessage.getTargetId());
            }
        } catch (Exception e) {
            log.error("处理缓存更新消息失败", e);
        }
    }

      // 从Redis加载会话消息（异步加载后备方法）
    private List<Message> loadMessagesFromRedis(String cacheKey) {
        RList<Message> messageList = redissonClient.getList(cacheKey);

        // 缓存穿透处理
        if (messageList.isEmpty()) {
            String[] parts = cacheKey.split(":")[2].split("_");
            Long userId1 = Long.parseLong(parts[0]);
            Long userId2 = Long.parseLong(parts[1]);

            RLock lock = redissonClient.getLock("lock-"+cacheKey);
            try {
                if (lock.tryLock(CACHE_LOAD_TIMEOUT, TimeUnit.MILLISECONDS)) {
                    try {
                        if (messageList.isEmpty()) {
                            log.info("Loading messages from DB for conversation: {}<->{}", userId1, userId2);
                            // 从数据库加载最近消息
                            List<Message> dbMessages = messageService.getBaseMapper().getMessages(String.valueOf(userId1), String.valueOf(userId2));

                            if (!dbMessages.isEmpty()) {
                                // 缓存到Redis
                                messageList.addAll(dbMessages);
                                messageList.expire(7, TimeUnit.DAYS);
                                log.info("Cached {} messages for conversation: {}<->{}",
                                        dbMessages.size(), userId1, userId2);
                            } else {
                                log.info("No messages found for conversation: {}<->{}", userId1, userId2);
                            }

                            return dbMessages;
                        }
                    } finally {
                        lock.unlock();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Interrupted while loading cache for conversation: {}<->{}",
                        userId1, userId2, e);
                return Collections.emptyList();
            }
        }

        return new ArrayList<>(messageList);
    }

    // 获取两个用户之间的消息（优化版）
    public List<Message> getMessagesBetweenUsers(Long userId1, Long userId2) {
        // 1. 使用布隆过滤器快速判断用户是否存在
        if (!userBloomFilter.contains(userId1) || !userBloomFilter.contains(userId2)) {
            log.debug("User not found in bloom filter: {} or {}", userId1, userId2);
            return Collections.emptyList();
        }

        String cacheKey = getMessagesKey(userId1, userId2);

        // 2. 记录会话访问
        conversationAccessCounter.computeIfAbsent(cacheKey, k -> new AtomicInteger(0)).incrementAndGet();

        // 3. 从本地缓存获取（自动触发异步加载）
        return hotConversations.get(cacheKey);
    }

      /**
     * 获取两个用户之间的消息（分页版）
     * @param userId1 用户ID1
     * @param userId2 用户ID2
     * @param pageNum 页码（从1开始）
     * @param pageSize 每页大小
     * @return 分页后的消息列表
     */
    public List<Message> getMessagesBetweenUsers(Long userId1, Long userId2, int pageNum, int pageSize) {
        // 参数校验与边界处理
        if (pageNum < 1) pageNum = 1;
        if (pageSize < 1 || pageSize > 100) pageSize = 20; // 限制最大页大小为100

        // 1. 使用布隆过滤器快速判断用户是否存在
        if (!userBloomFilter.contains(userId1) || !userBloomFilter.contains(userId2)) {
            log.debug("User not found in bloom filter: {} or {}", userId1, userId2);
            return Collections.emptyList();
        }

        String cacheKey = getMessagesKey(userId1, userId2);

        // 2. 记录会话访问
        conversationAccessCounter.computeIfAbsent(cacheKey, k -> new AtomicInteger(0)).incrementAndGet();

        // 3. 从本地缓存获取完整消息列表
        List<Message> allMessages = hotConversations.get(cacheKey);

        // 4. 按时间降序排序（最新消息在前）
        allMessages.sort((m1, m2) -> m2.getCreateTime().compareTo(m1.getCreateTime()));

        // 5. 计算分页参数
        int startIndex = (pageNum - 1) * pageSize;
        if (startIndex >= allMessages.size()) {
            return Collections.emptyList(); // 超出总条数，返回空列表
        }
        int endIndex = Math.min(startIndex + pageSize, allMessages.size());

        // 6. 返回分页后的数据
        return allMessages.subList(startIndex, endIndex);
    }

    // 缓存两个用户之间的消息
    public void cacheMessagesBetweenUsers(Long userId1, Long userId2, List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }

        String cacheKey = getMessagesKey(userId1, userId2);

        // 1. 存储到Redis
        RList<Message> messageList = redissonClient.getList(cacheKey);
        messageList.addAll(messages);
        messageList.expire(30, TimeUnit.DAYS);

        // 2. 如果是热点用户，同时存入本地缓存
        if (hotUserIds.contains(userId1) || hotUserIds.contains(userId2)) {
            hotConversations.put(cacheKey, messages);
        }

        log.info("Cached {} messages for conversation: {}<->{}",
                messages.size(), userId1, userId2);
    }

    // 清除会话消息缓存
    public void clearMessagesBetweenUsers(Long userId1, Long userId2) {
        String cacheKey = getMessagesKey(userId1, userId2);
        RLock lock = redissonClient.getLock("lock:" + cacheKey);
        try {
            lock.lock();
            RList<Message> messageList = redissonClient.getList(cacheKey);
            messageList.clear();
            
            // 清除本地缓存 
            hotConversations.invalidate(cacheKey);
        } finally {
            lock.unlock();
        }
        
        log.info("Cleared messages for conversation: {}<->{}", userId1, userId2);
    }


    // 检测热点用户
    private void detectHotUsers() {
        // 1. 从访问计数器统计热门用户
        List<Long> activeUsers = conversationAccessCounter.entrySet().stream()
                .filter(e -> e.getValue().get() > 100) // 会话访问超过100次
                .map(e -> {
                    String key = e.getKey();
                    if (key.startsWith("user_")) {
                        return Long.parseLong(key.substring(5));
                    } else {
                        String[] parts = key.split("_");
                        return Long.parseLong(parts[0]);
                    }
                })
                .toList();

        // 2. 结合在线用户和消息量综合判断
//        List<Long> onlineUsers = userService.getOnlineUserIds();
//        List<Long> highMsgUsers = messageService.getHighMessageUsers(100);

        // 3. 更新热点用户集合
        hotUserIds.clear();
        hotUserIds.addAll(activeUsers);
//        hotUserIds.addAll(onlineUsers);
//        hotUserIds.addAll(highMsgUsers);

        log.info("Detected {} hot users", hotUserIds.size());
    }

    // 检测热点会话
    private void detectHotConversations() {
        // 获取访问次数超过阈值的会话
        List<String> hotKeys = conversationAccessCounter.entrySet().stream()
                .filter(e -> e.getValue().get() > 50) // 阈值可调整
                .filter(e -> !e.getKey().startsWith("user_"))
                .map(Map.Entry::getKey)
                .toList();

        log.info("Detected {} hot conversations", hotKeys.size());

        // 预加载热点会话到缓存
        for (String key : hotKeys) {
            String[] parts = key.split("_");
            Long userId1 = Long.parseLong(parts[0]);
            Long userId2 = Long.parseLong(parts[1]);

            // 从数据库加载并缓存
            CompletableFuture.runAsync(() -> {
                try {
                    List<Message> messages = messageService.getBaseMapper().getMessages(String.valueOf(userId1), String.valueOf(userId2));
                    if (!messages.isEmpty()) {
                        cacheMessagesBetweenUsers(userId1, userId2, messages);
                    }
                } catch (Exception e) {
                    log.error("Failed to load hot conversation: {}<->{}", userId1, userId2, e);
                }
            }, cacheLoaderExecutor);
        }
    }

    // 重置访问计数器
    private void resetAccessCounters() {
        conversationAccessCounter.forEach((k, v) -> {
            // 衰减访问计数，避免长期累积
            int newValue = Math.max(1, v.get() / 2);
            v.set(newValue);
        });
    }

    // 缓存预热：加载热点用户ID
    private void loadHotUserIds() {
//        hotUserIds.addAll(userService.getHotUserIds(500));
        log.info("Preloaded {} hot user IDs", hotUserIds.size());
    }

    // 缓存预热：加载热点用户消息
    private void loadHotUserMessages() {
        log.info("Preloading hot user messages...");

        int count = 0;
        for (Long userId : hotUserIds) {
            // 获取该用户最近的消息交互
            List<Long> relatedUserIds = messageService.getBaseMapper().getRelatedUserIds(userId, 10);
            for (Long relatedId : relatedUserIds) {
                try {
                    List<Message> messages = messageService.getBaseMapper().getMessages(String.valueOf(userId), String.valueOf(relatedId));
                    if (!messages.isEmpty()) {
                        cacheMessagesBetweenUsers(userId, relatedId, messages);
                        count++;
                    }
                } catch (Exception e) {
                    log.error("Failed to preload messages for user: {}<->{}", userId, relatedId, e);
                }
            }
        }

        log.info("Preloaded {} hot conversations", count);
    }


    // 生成用户间消息缓存键
    private String getMessagesKey(Long userId1, Long userId2) {
        // 确保键的生成顺序一致
        if (userId1 < userId2) {
            return "chat:messages:" + userId1 + "_" + userId2;
        } else {
            return "chat:messages:" + userId2 + "_" + userId1;
        }
    }


    // 获取缓存统计信息
    public String getCacheStats() {
        return
                "HotConversations: " + hotConversations.stats() +
                "\nHotUsers: " + hotUserIds.size() +
                "\nBloomFilter: " + userBloomFilter.count() + " elements";
    }




}