package com.rfid.user.entity;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class Message implements Serializable
{
    @Serial
    private static final long serialVersionUID = 1L;    // 序列化id
    private Long targetId;              // 接收者id
    private Long userId;                // 发送者id
    private Integer type;                   // 消息类型
    private String content;             // 发送时间
    private Boolean isRead;             // 是否已读
    private LocalDateTime createTime;   // 发送时间
}