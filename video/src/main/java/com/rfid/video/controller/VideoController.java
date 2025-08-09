package com.rfid.video.controller;

import com.rfid.video.Repository.VideoMapper;
import com.rfid.video.entity.Result;
import com.rfid.video.entity.Video;
import com.rfid.video.service.impl.VideoPlayService;
import com.rfid.video.service.impl.VideoUploadServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
@Slf4j
@RestController
@RequestMapping("/api/videos")
public class VideoController {

    @Autowired
    private VideoUploadServiceImpl videoUploadService;

    @Autowired
    private VideoPlayService videoPlayService;

    @Autowired
    private VideoMapper videoRepository;

    /**
     * 获取视频播放URL
     */
    @GetMapping("/{videoId}/play")
    public Result getVideoPlayUrl(@PathVariable Long videoId) {
        String url = videoPlayService.getVideoPlayUrl(videoId);
        return Result.success(url);
    }

    /**
     * 删除视频
     */
    @DeleteMapping("/{videoId}")
    public Result deleteVideo(@PathVariable Long videoId) {
        videoPlayService.deleteVideo(videoId);
        return Result.success();
    }

    /**
     * 获取视频详情
     */
    @GetMapping("/{videoId}")
    public Result getVideoDetail(@PathVariable Long videoId) {
        Optional<Video> video = Optional.ofNullable(videoRepository.selectById(videoId));
        if (video.isPresent()) {
            Video foundVideo = video.get();
            return Result.success(foundVideo);
        } else {
            log.warn("视频不存在{}", videoId);
            return Result.error("视频不存在");
        }

    }

    /**
     * 更新视频元数据
     */
    @PutMapping("/{videoId}")
    public Result updateVideoMetadata(@PathVariable Long videoId, @RequestBody Video video) {
        video.setId(videoId);
        videoRepository.insert(video);
        return Result.success();
    }

}