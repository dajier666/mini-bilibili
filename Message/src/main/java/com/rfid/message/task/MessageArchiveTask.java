package com.rfid.message.task;

import com.rfid.message.entity.Message;
import com.rfid.message.service.MessageStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

@Component
public class MessageArchiveTask {
    @Autowired
    private MessageStorageService messageStorageService;

    @Scheduled(cron = "0 0 3 * * ?") // 每天凌晨 3 点执行
    public void archiveOldMessages() {
        LocalDateTime threeMonthsAgo =