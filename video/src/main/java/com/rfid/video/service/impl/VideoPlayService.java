package com.rfid.video.service.impl;


import com.rfid.video.Repository.VideoMapper;
import com.rfid.video.entity.Video;
import com.rfid.video.exception.VideoException;
import com.rfid.video.utils.SnowflakeIdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class VideoPlayService {
    @Autowired
    private MinIOService minIOService;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private VideoMapper videoRepository;

    @Autowired
    private VideoCacheService videoCacheService;

    /**
     * 获取视频播放预签名URL
     */
    public String getVideoPlayUrl(Long videoId) {
        // 从缓存获取视频元数据
        Video video = videoCacheService.getVideoMetadata(videoId);
        try {
            // 生成1小时有效的预签名URL
            return minIOService.getPresignedUrl(video.getOriginalFilePath(), 3600);
        } catch (Exception e) {
            log.error("生成视频播放URL失败: videoId={}", videoId, e);
            throw new VideoException("生成视频播放URL失败", e);
        }
    }

    /**
     * 删除视频（包括文件和元数据）
     */
    public void deleteVideo(Long videoId) {
        // 从缓存获取视频元数据
        Video video = videoCacheService.getVideoMetadata(videoId);

        try {
            // 删除原始视频文件
            minIOService.deleteFile(video.getOriginalFilePath());

            // 删除转码文件
            if (video.getTranscodeFilePaths() != null) {
                for (String path : video.getTranscodeFilePaths()) {
                    minIOService.deleteFile(path);
                }
            }

            // 删除封面图片
            if (video.getCoverImagePath() != null) {
                minIOService.deleteFile(video.getCoverImagePath());
            }

            // 删除数据库记录
            videoRepository.deleteById(videoId);

            // 清除缓存
            videoCacheService.clearVideoCache(videoId);

            log.info("视频删除成功: videoId={}", videoId);
        } catch (Exception e) {
            log.error("视频删除失败: videoId={}", videoId, e);
            throw new VideoException("视频删除失败", e);
        }
    }
}
