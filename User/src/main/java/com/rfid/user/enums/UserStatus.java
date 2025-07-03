package com.rfid.user.enums;

public enum UserStatus {
    NORMAL(1, "正常"),
    FROZEN(2, "冻结"),
    DELETED(3, "已删除");

    private final int code;
    private final String description;

    UserStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}