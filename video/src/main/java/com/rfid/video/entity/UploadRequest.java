package com.rfid.video.entity;

import lombok.Data;

import java.util.Map;

@Data
public class UploadRequest {
    private String fileName;        // 原始文件名
    private long fileSize;          // 文件总大小(字节)
    private long chunkSize;         // 分片大小(字节)，默认5MB
    private String contentType;     // 文件类型(MIME)
    private String categoryId;      // 视频分类ID
    private String[] tags;          // 视频标签
    private String title;           // 视频标题(可选)
    private String description;     // 视频描述(可选)
    private String uploadToken;     // 上传令牌(用于身份验证)
    private Map<String, String> extraParams; // 额外参数
}
