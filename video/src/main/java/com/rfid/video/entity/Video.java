package com.rfid.video.entity;

import lombok.Data;
import org.springframework.data.elasticsearch.annotations.Document;

import java.util.Date;
import java.util.List;


@Data
public class Video {
    private Long id;                // 视频ID
    private String title;           // 视频标题
    private String description;     // 视频描述
    private List<String> tags;      // 视频标签列表
    private String originalFilePath;// 原始视频文件路径
    private List<String> transcodeFilePaths; // 转码后的视频文件路径列表
    private String coverImagePath;  // 封面图片路径
    private Date uploadTime;        // 上传时间
    private Long uploaderId;        // 上传者ID
    private Date updateTime;        // 更新时间
    private Integer status;         // 视频状态(0 待审核，1 已发布，2 已下架)
}