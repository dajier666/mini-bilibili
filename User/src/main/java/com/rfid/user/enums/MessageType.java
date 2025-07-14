package com.rfid.user.enums;

import lombok.Getter;

@Getter
public enum MessageType {
    FOLLOW(1, "关注通知"),
    SYSTEM(2, "系统通知"),
    PROMOTION(3, "推广通知"),
    PRIVATE(3, "私信通知"),
    GROUP(4, "群发通知");


    private final int code;
    private final String description;

    MessageType(int code, String description) {
        this.code = code;
        this.description = description;
    }

}