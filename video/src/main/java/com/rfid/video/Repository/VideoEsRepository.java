package com.rfid.video.Repository;

import com.rfid.video.entity.VideoDoc;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VideoEsRepository extends ElasticsearchRepository<VideoDoc, Long> {
    // 可以添加自定义查询方法
}