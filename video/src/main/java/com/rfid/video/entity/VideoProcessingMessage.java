package com.rfid.video.entity;

import lombok.Data;

@Data
public class VideoProcessingMessage {
    private String videoId;
    private String filePath;

    public VideoProcessingMessage(String videoId, String filePath) {
        this.videoId = videoId;
        this.filePath = filePath;
    }
}