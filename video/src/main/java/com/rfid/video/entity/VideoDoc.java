package com.rfid.video.entity;

import lombok.Data;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Data
@Document(indexName = "video_index")
public class VideoDoc {
    private Long id; // 与MySQL中的video_id对应

    @Field(type = FieldType.Text, analyzer = "ik_max_word")
    private String title; // 视频标题，用于检索

    @Field(type = FieldType.Text, analyzer = "ik_max_word")
    private String description; // 视频描述，用于检索


    @Field(type = FieldType.Keyword)
    private String[] tags; // 视频标签，用于检索

    // 其他需要检索的字段
}