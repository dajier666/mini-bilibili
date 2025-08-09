package com.rfid.video.service.impl;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.rfid.video.Repository.VideoEsRepository;
import com.rfid.video.Repository.VideoMapper;
import com.rfid.video.entity.Video;
import com.rfid.video.entity.VideoDoc;
import com.rfid.video.entity.VideoUpdateMessage;
import com.rfid.video.exception.VideoException;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RList;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 视频元数据缓存服务实现类，使用Caffeine+Redis双层缓存
 */
@Slf4j
@Service
public class VideoCacheService {
    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private VideoMapper videoRepository;

    @Autowired
    private VideoEsRepository videoEsRepository;

    // Caffeine本地缓存：存储热门视频元数据
    private LoadingCache<Long, Video> hotVideoCache;

    // 缓存加载线程池
    private ExecutorService cacheLoaderExecutor;

    // 缓存加载超时时间（毫秒）
    private static final long CACHE_LOAD_TIMEOUT = 500;

    /**
     * 构造函数，初始化缓存和线程池
     */
    public VideoCacheService() {
        // 配置缓存加载线程池
        this.cacheLoaderExecutor = Executors.newFixedThreadPool(10, r -> {
            Thread thread = new Thread(r);
            thread.setName("video-cache-loader-" + new AtomicInteger(1).getAndIncrement());
            return thread;
        });

        // 配置Caffeine本地缓存
        this.hotVideoCache = Caffeine.newBuilder()
                .maximumSize(1000)  // 最大缓存1000个视频元数据
                .expireAfterAccess(2, TimeUnit.HOURS)  // 访问后2小时过期
                .executor(cacheLoaderExecutor)
                .recordStats()
                .build(this::loadVideoFromRedis);
    }

    /**
     * 初始化缓存预热
     */
    @PostConstruct
    public void initCache() {
        log.info("Starting video cache preheating...");
        // 缓存预热逻辑
        log.info("Video cache preheating completed");
    }

    /**
     * 从缓存获取视频元数据
     */
    public Video getVideoMetadata(Long videoId) {
        try {
            return hotVideoCache.get(videoId);
        } catch (Exception e) {
            log.error("Failed to get video metadata from cache: videoId={}", videoId, e);
            // 缓存获取失败时，直接从数据库获取
            return videoRepository.selectById(videoId);
        }
    }

    /**
     * 从Redis加载视频元数据
     */
    private Video loadVideoFromRedis(Long videoId) {
        String cacheKey = "video:metadata:" + videoId;
        RList<Video> videoList = redissonClient.getList(cacheKey);

        // 缓存穿透处理
        if (videoList.isEmpty()) {
            RLock lock = redissonClient.getLock("lock:" + cacheKey);
            try {
                if (lock.tryLock(CACHE_LOAD_TIMEOUT, TimeUnit.MILLISECONDS)) {
                    try {
                        if (videoList.isEmpty()) {
                            log.info("Loading video metadata from DB: videoId={}", videoId);
                            // 从数据库加载视频元数据
                            Video video = videoRepository.selectById(videoId);
                            if(video == null) {
                                return null;
                            }

                            // 缓存到Redis
                            videoList.add(video);
                            videoList.expire(7, TimeUnit.DAYS);
                            log.info("Cached video metadata: videoId={}", videoId);

                            return video;
                        }
                    } finally {
                        lock.unlock();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Interrupted while loading video cache: videoId={}", videoId, e);
                throw new VideoException("加载视频缓存失败");
            }
        }

        return videoList.isEmpty() ? null : videoList.get(0);
    }

    /**
     * 更新视频元数据缓存
     */
    public void updateVideoCache(Video video) {
        if (video == null || video.getId() == null) {
            return;
        }

        Long videoId = video.getId();
        String cacheKey = "video:metadata:" + videoId;

        // 更新Redis缓存
        RList<Video> videoList = redissonClient.getList(cacheKey);
        videoList.clear();
        videoList.add(video);
        videoList.expire(7, TimeUnit.DAYS);

        // 更新本地缓存
        hotVideoCache.put(videoId, video);

        // 更新ES
        updateVideoEs(video);

        log.info("Updated video cache and ES: videoId={}", videoId);
    }

    /**
     * 更新ES中的视频检索数据
     */
    private void updateVideoEs(Video video) {
        VideoDoc videoDoc = new VideoDoc();
        videoDoc.setId(video.getId());
        videoDoc.setTitle(video.getTitle());
        videoDoc.setDescription(video.getDescription());
        videoDoc.setTags(video.getTags() != null ? (String[]) video.getTags().toArray() : new String[0]);
        // 设置其他检索字段

        videoEsRepository.save(videoDoc);
    }

    /**
     * 清除视频元数据缓存
     */
    public void clearVideoCache(Long videoId) {
        if (videoId == null) {
            return;
        }

        String cacheKey = "video:metadata:" + videoId;

        // 清除Redis缓存
        redissonClient.getList(cacheKey).clear();

        // 清除本地缓存
        hotVideoCache.invalidate(videoId);

        // 清除ES数据
        videoEsRepository.deleteById(videoId);

        log.info("Cleared video cache and ES: videoId={}", videoId);
    }

    /**
     * 处理Kafka消息，更新缓存和ES
     */
    @KafkaListener(topics = "video_cache_topic", groupId = "video-cache-group")
    public void handleVideoUpdateMessage(VideoUpdateMessage updateMessage) {
        try {
            log.info("Received video update message: {}", updateMessage);

            if ("INSERT".equals(updateMessage.getEventType()) || "UPDATE".equals(updateMessage.getEventType())) {
                // 从数据库加载最新视频元数据
                Video video = videoRepository.selectById(updateMessage.getPrimaryKey());
                if (video != null) {
                    updateVideoCache(video);
                }
            } else if ("DELETE".equals(updateMessage.getEventType())) {
                clearVideoCache(updateMessage.getPrimaryKey());
            }
        } catch (Exception e) {
            log.error("处理视频更新消息失败", e);
        }
    }
}