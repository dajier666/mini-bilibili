package com.rfid.message.controller;

import com.rfid.message.entity.Message;
import com.rfid.message.entity.Result;

import com.rfid.message.service.Impl.KafkaProducerService;
import com.rfid.message.service.Impl.MessageCacheServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    @Autowired
    private MessageCacheServiceImpl messageService;

    @Autowired
    private KafkaProducerService kafkaProducerService;

    /**
     * 获取单聊消息
     */
    @GetMapping("/{userId}/{targetId}/page/{page}/size/{size}")
    public Result getMessages(
            @RequestParam Long userId,
            @RequestParam Long targetId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<Message> messages = messageService.getMessagesBetweenUsers(userId, targetId, page, size);
        return Result.success(messages);
    }

    /**
     * 获取所有单聊消息
     */
    @GetMapping("/{userId}/{targetId}/all")
    public Result getAllMessages(
            @RequestParam Long userId,
            @RequestParam Long targetId) {
        List<Message> messages = messageService.getMessagesBetweenUsers(userId, targetId);
        return Result.success(messages);
    }

    /**
     * 发送私信
     */
    @PostMapping("/send")
    public Result sendMessage(@RequestBody Message message) {
        kafkaProducerService.sendPrivateMessage(message.getUserId(), message.getTargetId(), message.getContent());
        return Result.success();
    }


}