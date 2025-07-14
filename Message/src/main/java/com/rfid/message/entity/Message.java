package com.rfid.message.entity;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

@Data
public class Message implements Serializable
{   @TableField(exist = false)
    @Serial
    private static final long serialVersionUID = 1L;    // 序列化id
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;                    // 消息id
    private Long targetId;              // 接收者id
    private Long userId;                // 发送者id
    private Integer type;               // 消息类型
    private String content;             // 消息内容
    private Boolean isRead;             // 是否已读
    private LocalDateTime createTime;   // 发送时间
}