package com.rfid.message.entity;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class GroupMessage implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private List<Long> groupIds;        // 群组 ID 列表
    private Long userId;              // 发送者 ID
    private String content;           // 消息内容
    private LocalDateTime createTime; // 发送时间
}