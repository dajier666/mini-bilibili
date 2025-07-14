package com.rfid.video.entity;

import com.rfid.video.enums.UploadStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class UploadSession {
    private String uploadId;        // 上传会话ID
    private String videoId;         // 关联的视频ID(上传完成后生成)
    private String fileName;        // 原始文件名
    private long fileSize;          // 文件总大小
    private long chunkSize;         // 分片大小
    private int totalChunks;        // 总分片数
    private List<Integer> uploadedChunks; // 已上传的分片索引
    private int status;    // 上传状态(INIT, IN_PROGRESS, COMPLETED, FAILED)
    private LocalDateTime createTime; // 创建时间
    private LocalDateTime updateTime; // 更新时间
    private String filePath;        // 最终存储路径(上传完成后)
    private String errorMessage;    // 错误信息(如果失败)
}


