package com.rfid.message.service.Impl;

import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.protocol.CanalEntry;
import com.alibaba.otter.canal.protocol.Message;
import com.rfid.message.entity.CacheUpdateMessage;
import jakarta.annotation.PostConstruct;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ExecutorService;

@Component
public class CanalClient {

    private final CanalConnector connector;
    private final ExecutorService executor;
    private final KafkaTemplate<String, CacheUpdateMessage> kafkaTemplate;
    private final String topic;

    public CanalClient(CanalConnector connector,
                       ExecutorService executor,
                       KafkaTemplate<String, CacheUpdateMessage> kafkaTemplate) {
        this.connector = connector;
        this.executor = executor;
        this.kafkaTemplate = kafkaTemplate;
        this.topic = "message_cache_topic";
    }

    @PostConstruct
    public void start() {
        executor.submit(this::consume);
    }

    private void consume() {
        try {
            connector.connect();
            connector.subscribe("message"); // 替换为实际表名

            while (true) {
                Message message = connector.get(100);
                List<CanalEntry.Entry> entries = message.getEntries();
                if (entries != null && !entries.isEmpty()) {
                    for (CanalEntry.Entry entry : entries) {
                        if (entry.getEntryType() == CanalEntry.EntryType.ROWDATA) {
                            processRowChange(entry);
                        }
                    }
                }
                Thread.sleep(100); // 避免CPU占用过高
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            connector.disconnect();
        }
    }

    private void processRowChange(CanalEntry.Entry entry) {
        try {
            CanalEntry.RowChange rowChange = CanalEntry.RowChange.parseFrom(entry.getStoreValue());
            CanalEntry.EventType eventType = rowChange.getEventType();

            // 处理消息表变更
            for (CanalEntry.RowData rowData : rowChange.getRowDatasList()) {
                if (eventType == CanalEntry.EventType.INSERT ||
                        eventType == CanalEntry.EventType.UPDATE ||
                        eventType == CanalEntry.EventType.DELETE) {

                    // 构建缓存更新消息
                    CacheUpdateMessage updateMessage = new CacheUpdateMessage();
                    updateMessage.setEventType(eventType.name());
                    updateMessage.setTableName(entry.getHeader().getTableName());

                    // 提取主键和关键字段
                    if (eventType == CanalEntry.EventType.DELETE) {
                        for (CanalEntry.Column column : rowData.getBeforeColumnsList()) {
                            if (column.getIsKey()) {
                                updateMessage.setPrimaryKey(Long.valueOf(column.getValue()));
                                break;
                            }
                        }
                    } else {
                        for (CanalEntry.Column column : rowData.getAfterColumnsList()) {
                            if (column.getIsKey()) {
                                updateMessage.setPrimaryKey(Long.valueOf(column.getValue()));
                            }
                            // 提取用户ID和目标ID等关键字段
                            if ("user_id".equals(column.getName())) {
                                updateMessage.setUserId(Long.valueOf(column.getValue()));
                            } else if ("target_id".equals(column.getName())) {
                                updateMessage.setTargetId(Long.valueOf(column.getValue()));
                            }
                        }
                    }

                    // 发送到Kafka
                    kafkaTemplate.send(topic, updateMessage);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}