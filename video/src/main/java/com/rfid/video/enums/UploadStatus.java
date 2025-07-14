package com.rfid.video.enums;

import lombok.Getter;

// 上传状态枚举
@Getter
public enum UploadStatus {
    INIT(0, "初始化"),
    IN_PROGRESS(1, "进行中"),
    COMPLETED(2, "已完成"),
    FAILED(3, "已失败");

    private final int code;
    private final String description;

    UploadStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

}