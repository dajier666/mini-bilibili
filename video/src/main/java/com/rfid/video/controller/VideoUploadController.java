package com.rfid.video.controller;

import com.rfid.video.entity.Result;
import com.rfid.video.entity.UploadRequest;
import com.rfid.video.entity.UploadResponse;
import com.rfid.video.entity.UploadSession;
import com.rfid.video.service.impl.VideoUploadServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/videos/upload")
public class VideoUploadController {

    @Autowired
    private VideoUploadServiceImpl videoUploadService;

    /**
     * 初始化上传会话
     */
    @PostMapping("/init")
    public Result initiateUpload(@RequestBody UploadRequest request) {
        UploadResponse response = videoUploadService.initiateUpload(request);
        return Result.success(response);
    }

    /**
     * 上传文件分片
     */
    @PostMapping("/chunk")
    public Result uploadChunk(
            @RequestParam String uploadId,
            @RequestParam int chunkIndex,
            @RequestParam MultipartFile file) {
        videoUploadService.uploadChunk(uploadId, chunkIndex, file);
        return Result.success();
    }

    /**
     * 查询上传状态
     */
    @GetMapping("/status/{uploadId}")
    public Result getUploadStatus(@PathVariable String uploadId) {
        UploadSession session = videoUploadService.getUploadSession(uploadId);
        return Result.success(session);
    }
}