package com.rfid.video.service.impl;


import com.rfid.video.Repository.VideoMapper;
import com.rfid.video.entity.UploadRequest;
import com.rfid.video.entity.UploadResponse;
import com.rfid.video.entity.UploadSession;
import com.rfid.video.entity.Video;
import com.rfid.video.entity.VideoProcessingMessage;
import com.rfid.video.enums.UploadStatus;
import com.rfid.video.exception.VideoException;
import com.rfid.video.utils.SnowflakeIdGenerator;
import io.minio.ComposeObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.Result;
import io.minio.ComposeSource;
import io.minio.messages.Item;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.StreamSupport;


// 视频上传服务核心代码
@Slf4j
@Service
public class VideoUploadServiceImpl {

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

    private static final String UPLOAD_SESSION_KEY = "upload-session";
    private static final String VIDEO_CHUNKS_BUCKET = "video-chunks";
    private static final String VIDEO_BUCKET = "videos";

    /**
     * 初始化上传会话
     */
    public UploadResponse initiateUpload(UploadRequest request) {
        // 验证请求参数
        validateUploadRequest(request);

        // 创建上传会话
        UploadSession session = createUploadSession(request);
        // 保存上传会话信息
        saveUploadSession(session);

        log.info("创建上传会话成功: uploadId={}, fileName={}", session.getUploadId(), session.getFileName());

        // 返回上传ID和分片信息
        return UploadResponse.builder()
                .uploadId(session.getUploadId())
                .totalChunks(session.getTotalChunks())
                .chunkSize(session.getChunkSize())
                .build();
    }

    /**
     * 上传文件分片
     */
    public void uploadChunk(String uploadId, int chunkIndex, MultipartFile file) {
        if (file.isEmpty()) {
            throw new VideoException("分片文件不能为空");
        }

        // 获取上传会话
        UploadSession session = getUploadSession(uploadId);
        if (session == null) {
            throw new VideoException("上传会话不存在");
        }

        if (session.getStatus() != UploadStatus.IN_PROGRESS.getCode()) {
            throw new VideoException("上传会话状态异常: " + session.getStatus());
        }

        // 检查分片索引是否有效
        if (chunkIndex < 0 || chunkIndex >= session.getTotalChunks()) {
            throw new VideoException("无效的分片索引: " + chunkIndex);
        }

        // 检查分片是否已上传
        if (session.getUploadedChunks() != null && session.getUploadedChunks().contains(chunkIndex)) {
            log.warn("分片已上传: uploadId={}, chunkIndex={}", uploadId, chunkIndex);
            return;
        }

        try {
            // 保存分片到临时存储
            String chunkPath = generateChunkPath(uploadId, chunkIndex);
            minIOService.uploadFile(chunkPath, file);

            // 更新已上传分片信息
            updateUploadedChunks(session, chunkIndex);
            log.info("分片上传成功: uploadId={}, chunkIndex={}, size={}KB",
                    uploadId, chunkIndex, file.getSize() / 1024);

            // 检查是否所有分片都已上传完成
            if (isAllChunksUploaded(session)) {
                // 合并分片
                mergeChunks(session);

                // 更新会话状态
                session.setStatus(UploadStatus.COMPLETED.getCode());
                session.setUpdateTime(LocalDateTime.now());
                updateUploadSession(session);

                // 保存视频信息到数据库
                saveVideoMetadata(session);

                // 发送视频处理消息
                kafkaTemplate.send("video-processing-topic",
                        new VideoProcessingMessage(session.getVideoId(), session.getFilePath()));

                log.info("所有分片上传完成，视频合并成功: videoId={}, filePath={}",
                        session.getVideoId(), session.getFilePath());
            }
        } catch (Exception e) {
            log.error("上传分片失败: uploadId={}, chunkIndex={}", uploadId, chunkIndex, e);
            session.setStatus(UploadStatus.FAILED.getCode());
            session.setErrorMessage(e.getMessage());
            session.setUpdateTime(LocalDateTime.now());
            updateUploadSession(session);
            throw new VideoException("上传分片失败: " + e.getMessage());
        }
    }

    /**
     * 更新上传会话
     */
    private void updateUploadSession(UploadSession session) {
        RMap<String, UploadSession> uploadSessionMap = redissonClient.getMap(UPLOAD_SESSION_KEY);
        uploadSessionMap.put(session.getUploadId(), session);
        // 设置会话过期时间(7天)
        uploadSessionMap.expire(7, java.util.concurrent.TimeUnit.DAYS);
    }

    /**
     * 获取上传会话
     */
    public UploadSession getUploadSession(String uploadId) {
        RMap<String, UploadSession> uploadSessionMap = redissonClient.getMap(UPLOAD_SESSION_KEY);
        return uploadSessionMap.get(uploadId);
    }

    /**
     * 保存上传会话
     */
    private void saveUploadSession(UploadSession session) {
        RMap<String, UploadSession> uploadSessionMap = redissonClient.getMap(UPLOAD_SESSION_KEY);
        uploadSessionMap.put(session.getUploadId(), session);
        // 设置会话过期时间(7天)
        uploadSessionMap.expire(7, java.util.concurrent.TimeUnit.DAYS);
    }

    /**
     * 计算总分片数
     */
    private int calculateTotalChunks(long fileSize, long chunkSize) {
        if (chunkSize <= 0) {
            throw new VideoException("分片大小必须大于0");
        }
        return (int) (fileSize / chunkSize) + (fileSize % chunkSize == 0 ? 0 : 1);
    }

    /**
     * 创建上传会话
     */
    private UploadSession createUploadSession(UploadRequest request) {
        // 生成唯一上传ID
        String uploadId = String.valueOf(snowflakeIdGenerator.nextId());

        // 创建上传会话
        UploadSession session = new UploadSession();
        session.setUploadId(uploadId);
        session.setFileName(request.getFileName());
        session.setFileSize(request.getFileSize());
        session.setChunkSize(request.getChunkSize() > 0 ? request.getChunkSize() : 5 * 1024 * 1024); // 默认5MB
        session.setTotalChunks(calculateTotalChunks(request.getFileSize(), session.getChunkSize()));
        session.setStatus(UploadStatus.IN_PROGRESS.getCode());
        session.setCreateTime(LocalDateTime.now());
        session.setUpdateTime(LocalDateTime.now());

        return session;
    }

    /**
     * 生成分片路径
     */
    private String generateChunkPath(String uploadId, int chunkIndex) {
        return String.format("%s/%d", uploadId, chunkIndex);
    }

    /**
     * 更新已上传分片信息
     */
    private void updateUploadedChunks(UploadSession session, int chunkIndex) {
        if (session.getUploadedChunks() == null) {
            session.setUploadedChunks(new ArrayList<>());
        }
        session.getUploadedChunks().add(chunkIndex);
        session.setUpdateTime(LocalDateTime.now());
        saveUploadSession(session);
    }

    /**
     * 检查是否所有分片都已上传完成
     */
    private boolean isAllChunksUploaded(UploadSession session) {
        return session.getUploadedChunks() != null && 
               session.getUploadedChunks().size() == session.getTotalChunks();
    }

    /**
     * 合并分片文件
     */
    private void mergeChunks(UploadSession session) throws Exception {
        String uploadId = session.getUploadId();
        String lockKey = "merge-chunks-lock:" + uploadId;
        RLock lock = redissonClient.getLock(lockKey);
    
        try {
            if (lock.tryLock(5000, 60000, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                // 列出所有分片文件
                Iterable<Result<Item>> results = minIOService.listObjects(
                        ListObjectsArgs.builder()
                                .bucket(VIDEO_CHUNKS_BUCKET)
                                .prefix(uploadId + "/")
                                .recursive(true)
                                .build());
                
                // 提取分片对象名称并排序
                List<String> chunkObjectNames = StreamSupport.stream(results.spliterator(), false)
                        .map(result -> {
                            try {
                                return result.get().objectName();
                            } catch (Exception e) {
                                throw new VideoException("获取分片信息失败", e);
                            }
                        })
                        .sorted(Comparator.comparingInt(name -> 
                            Integer.parseInt(name.substring(name.lastIndexOf("/") + 1))
                        ))
                        .toList();
    
                // 生成视频ID和路径
                String videoId = String.valueOf(snowflakeIdGenerator.nextId());
                String videoPath = String.format("%s/%s", videoId, session.getFileName());
    
                // 创建合并源列表（MinIO 8.5.17兼容方式）
                List<ComposeSource> sources = new ArrayList<>();
                for (String chunkObjectName : chunkObjectNames) {
                    sources.add(ComposeSource.builder()
                            .bucket(VIDEO_CHUNKS_BUCKET)
                            .object(chunkObjectName)
                            .build());
                }
    
                // 执行合并操作
                minIOService.composeObject(
                    ComposeObjectArgs.builder()
                        .bucket(VIDEO_BUCKET)
                        .object(videoPath)
                        .sources(sources)  // 使用sources()方法设置合并源
                        .build()
                );
    
                // 更新会话信息
                session.setVideoId(videoId);
                session.setFilePath(videoPath);
                session.setUpdateTime(LocalDateTime.now());
                updateUploadSession(session);
    
                // 删除临时分片文件
                deleteChunks(uploadId);
            } else {
                throw new VideoException("获取合并锁失败，请稍后重试");
            }
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 删除临时分片文件
     */
    private void deleteChunks(String uploadId) {
        try {
            Iterable<Result<Item>> results = minIOService.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(VIDEO_CHUNKS_BUCKET)
                            .prefix(uploadId + "/")
                            .recursive(true)
                            .build());

            for (Result<Item> result : results) {
                Item item = result.get();
                minIOService.deleteFile(item.objectName());
            }
            log.info("临时分片文件删除成功: uploadId={}", uploadId);
        } catch (Exception e) {
            log.error("删除临时分片文件失败: uploadId={}", uploadId, e);
            // 不抛出异常，避免影响主流程
        }
    }

    /**
     * 验证上传请求
     */
    private void validateUploadRequest(UploadRequest request) {
        if (request.getFileName() == null || request.getFileName().trim().isEmpty()) {
            throw new VideoException("文件名不能为空");
        }
        if (request.getFileSize() <= 0) {
            throw new VideoException("文件大小必须大于0");
        }
        // 可以添加更多验证逻辑，如文件类型、大小限制等
    }

    /**
     * 保存视频元数据到数据库
     */
    private void saveVideoMetadata(UploadSession session) {
        Video video = new Video();
        video.setId(Long.valueOf(session.getVideoId()));
        video.setTitle(session.getFileName()); // 实际应用中可能需要从UploadRequest获取
        video.setOriginalFilePath(session.getFilePath());
        video.setUploadTime(new Date());
        video.setStatus(0); // 0表示待审核
        // 可以从UploadRequest中获取更多元数据

        videoRepository.insert(video);
        log.info("视频元数据保存成功: videoId={}", session.getVideoId());
    }



}