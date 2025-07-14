package com.rfid.user.enums;

import lombok.Getter;

@Getter
public enum RelationType {
    FOLLOW(1, "关注"),
    FAN(2, "粉丝"),
    BLACKLIST(3, "黑名单");

    private final int code;
    private final String description;

    RelationType(int code, String description) {
        this.code = code;
        this.description = description;
    }

}