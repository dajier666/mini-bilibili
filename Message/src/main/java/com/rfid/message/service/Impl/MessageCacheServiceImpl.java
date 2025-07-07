package com.rfid.message.service.Impl;

import com.github.benmanes.caffeine.cache.*;
import com.rfid.message.entity.Message;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
@Slf4j
@Service
/**
 * 消息缓存服务实现类，负责管理消息相关的本地缓存和Redis缓存
 */
public class MessageCacheServiceImpl {
    // Redisson客户端，用于操作Redis分布式数据结构
    private final RedissonClient redissonClient;
    // 消息映射服务，用于数据库操作
    private final MessageMapperServiceImpl messageService;

    // Caffeine本地缓存：存储用户与所有粉丝的最近消息
    private final LoadingCache<Long, Map<Long, Message>> userLatestFanMessages;

    // Caffeine本地缓存：存储两用户间的热门消息会话
    private final LoadingCache<String, List<Message>> hotConversations;

    // 热点会话追踪器：记录每个会话的访问次数
    private final ConcurrentHashMap<String, AtomicInteger> conversationAccessCounter;

    // 热点用户ID集合：使用线程安全的CopyOnWriteArraySet存储
    private final CopyOnWriteArraySet<Long> hotUserIds;

    // 布隆过滤器：用于快速判断用户是否存在，防止缓存穿透
    private final RBloomFilter<Long> userBloomFilter;

    // 缓存加载线程池：用于异步加载缓存数据
    private final ExecutorService cacheLoaderExecutor;

    // 缓存加载超时时间（毫秒）
    private static final long CACHE_LOAD_TIMEOUT = 500;

    /**
     * 构造函数，初始化各种缓存和组件
     */
    public MessageCacheServiceImpl(RedissonClient redissonClient,
                            MessageMapperServiceImpl messageService) {
        this.redissonClient = redissonClient;
        this.messageService = messageService;

        // 初始化布隆过滤器（预计100万用户，误判率0.01%）
        this.userBloomFilter = redissonClient.getBloomFilter("userBloomFilter");
        userBloomFilter.tryInit(1000000, 0.0001);

        // 配置缓存加载线程池
        this.cacheLoaderExecutor = Executors.newFixedThreadPool(20, new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "cache-loader-" + threadNumber.getAndIncrement());
            }
        });

        // 配置用户与粉丝最近消息的本地缓存（启用异步加载）
        this.userLatestFanMessages = Caffeine.newBuilder()
                .maximumSize(5000)  // 最大缓存项数
                .expireAfterAccess(30, TimeUnit.MINUTES)  // 30分钟未访问则过期
                .refreshAfterWrite(10, TimeUnit.MINUTES)  // 写入10分钟后刷新
                .executor(cacheLoaderExecutor)  // 使用指定的线程池
                .recordStats()  // 记录缓存统计信息
                .build(this::loadLatestMessagesFromRedis);  // 指定加载方法

        // 配置热点会话本地缓存（启用异步加载）
        this.hotConversations = Caffeine.newBuilder()
                .maximumSize(2000)  // 最大缓存项数
                .expireAfterAccess(1, TimeUnit.HOURS)  // 1小时未访问则过期
                .weigher((String key, List<Message> value) -> Math.min(value.size(), 100))  // 权重计算器
                .executor(cacheLoaderExecutor)  // 使用指定的线程池
                .recordStats()  // 记录缓存统计信息
                .build(this::loadMessagesFromRedis);  // 指定加载方法

        // 初始化会话访问计数器
        this.conversationAccessCounter = new ConcurrentHashMap<>();

        // 初始化热点用户集合
        this.hotUserIds = new CopyOnWriteArraySet<>();
    }

    // 从Redis加载用户最近消息（异步加载后备方法）
    private Map<Long, Message> loadLatestMessagesFromRedis(Long userId) {
        String cacheKey = getLatestFanMessageKey(userId);
        RMap<Long, Message> latestMessages = redissonClient.getMap(cacheKey);

        // 缓存穿透处理
        if (latestMessages.isEmpty()) {
            RLock lock = redissonClient.getLock("lock-"+cacheKey);
            try {
                if (lock.tryLock(CACHE_LOAD_TIMEOUT, TimeUnit.MILLISECONDS)) {
                    try {
                        if (latestMessages.isEmpty()) {
                            log.info("Loading latest messages from DB for user: {}", userId);
                            // 从数据库加载
                            Map<Long, Message> dbMessages = messageService.getBaseMapper().getLatestMessages(String.valueOf(userId));
                            // 缓存到Redis
                            latestMessages.putAll(dbMessages);
                            latestMessages.expire(24, TimeUnit.HOURS);
                            log.info("Cached latest messages for user: {}", userId);
                        } else {
                            log.info("No latest messages found for user: {}", userId);
                        }
                    } finally {
                        lock.unlock();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Interrupted while loading cache for user: {}", userId, e);
                return Collections.emptyMap();
            }
        }

        return latestMessages.readAllMap();
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

    // 获取用户与所有粉丝的最近消息（优化版）
    public Map<Long, Message> getLatestMessagesWithFans(Long userId) {
        // 1. 使用布隆过滤器快速判断用户是否存在
        if (!userBloomFilter.contains(userId)) {
            log.debug("User not found in bloom filter: {}", userId);
            return Collections.emptyMap();
        }

        // 2. 记录用户访问
        conversationAccessCounter.computeIfAbsent("user_" + userId, k -> new AtomicInteger(0)).incrementAndGet();

        // 3. 从本地缓存获取（自动触发异步加载）
        return userLatestFanMessages.get(userId);
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

    // 缓存用户与粉丝的最近消息
    public void cacheLatestMessageWithFan(Long userId, Long fanId, Message message) {
        if (message == null) {
            return;
        }

        String cacheKey = getLatestFanMessageKey(userId);

        // 1. 存储到Redis
        RMap<Long, Message> latestMessages = redissonClient.getMap(cacheKey);
        latestMessages.put(fanId, message);
        latestMessages.expire(24, TimeUnit.HOURS);

        // 2. 如果是热点用户，更新本地缓存
        if (hotUserIds.contains(userId)) {
            userLatestFanMessages.asMap().compute(userId, (k, v) -> {
                if (v == null) {
                    v = new HashMap<>();
                }
                v.put(fanId, message);
                return v;
            });
        }

        log.info("Cached latest message for user: {} from fan: {}", userId, fanId);
    }

    // 缓存预热：启动时加载热门数据和初始化布隆过滤器
    @PostConstruct
    public void initCache() {
        log.info("Starting cache preheating...");
        // 任务1：初始化布隆过滤器
        CompletableFuture<Void> bloomFilterFuture = CompletableFuture.runAsync(
                this::initUserBloomFilter, cacheLoaderExecutor
        );

        // 任务2：加载热点用户ID
        CompletableFuture<Void> hotUserIdsFuture = CompletableFuture.runAsync(
                this::loadHotUserIds, cacheLoaderExecutor
        );

        // 任务3：依赖热点用户ID加载完成后，加载热点用户消息
        CompletableFuture<Void> hotUserMessagesFuture = hotUserIdsFuture.thenRunAsync(
                this::loadHotUserMessages, cacheLoaderExecutor
        );

        // 任务4：依赖热点用户ID加载完成后，加载用户最新粉丝消息
        CompletableFuture<Void> latestFanMessagesFuture = hotUserIdsFuture.thenRunAsync(
                this::loadUserLatestFanMessages, cacheLoaderExecutor
        );

        // 等待所有任务完成，处理异常
        CompletableFuture.allOf(
                bloomFilterFuture,
                hotUserMessagesFuture,
                latestFanMessagesFuture
        ).exceptionally(ex -> {
            log.error("Cache preheating failed", ex);
            return null;
        }).join();

        log.info("Cache preheating completed");
    }

    // 初始化用户布隆过滤器
    private void initUserBloomFilter() {
        log.info("Initializing user bloom filter...");

        // 从数据库加载所有用户ID到布隆过滤器
        List<Long> allUserIds = messageService.getBaseMapper().getAllUserIds();
        for (Long userId : allUserIds) {
            userBloomFilter.add(userId);
        }

        log.info("User bloom filter initialized with {} users", allUserIds.size());

        // 定期更新布隆过滤器
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(() -> {
            try {
                long currentCount = userBloomFilter.count();
                List<Long> newUserIds = messageService.getBaseMapper().getNewUserIds(currentCount);
                for (Long userId : newUserIds) {
                    userBloomFilter.add(userId);
                }
                log.info("Updated user bloom filter with {} new users", newUserIds.size());
            } catch (Exception e) {
                log.error("Failed to update user bloom filter", e);
            }
        }, 1, 1, TimeUnit.HOURS);
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

    // 缓存预热：加载用户与粉丝的最近消息
    private void loadUserLatestFanMessages() {
        log.info("Preloading user latest fan messages...");

        int count = 0;
        for (Long userId : hotUserIds) {
            try {
                Map<Long, Message> latestMessages = messageService.getBaseMapper().getLatestMessages(String.valueOf(userId));
                if (!latestMessages.isEmpty()) {
                    String cacheKey = getLatestFanMessageKey(userId);
                    RMap<Long, Message> rMap = redissonClient.getMap(cacheKey);
                    rMap.putAll(latestMessages);
                    rMap.expire(24, TimeUnit.HOURS);

                    // 加载到本地缓存
                    userLatestFanMessages.put(userId, latestMessages);
                    count++;
                }
            } catch (Exception e) {
                log.error("Failed to preload latest messages for user: {}", userId, e);
            }
        }

        log.info("Preloaded latest messages for {} users", count);
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

    // 生成用户与粉丝最近消息缓存键
    private String getLatestFanMessageKey(Long userId) {
        return "chat:latest_fan:" + userId;
    }

    // 获取缓存统计信息
    public String getCacheStats() {
        return "UserLatestFanMessages: " + userLatestFanMessages.stats() +
                "\nHotConversations: " + hotConversations.stats() +
                "\nHotUsers: " + hotUserIds.size() +
                "\nBloomFilter: " + userBloomFilter.count() + " elements";
    }
}