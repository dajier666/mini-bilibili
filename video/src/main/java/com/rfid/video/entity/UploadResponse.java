package com.rfid.video.entity;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class UploadResponse {
    private String uploadId;        // 唯一上传会话ID
    private int totalChunks;        // 总分片数
    private long chunkSize;         // 每片大小(字节)
    private String uploadUrl;       // 分片上传URL
    private Map<String, String> extraParams; // 额外参数(如签名信息)
}
