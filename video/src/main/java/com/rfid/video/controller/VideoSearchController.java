package com.rfid.video.controller;

import com.rfid.video.entity.Result;
import com.rfid.video.entity.Video;
import com.rfid.video.entity.VideoDoc;
import com.rfid.video.service.impl.VideoCacheService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/video/search")
public class VideoSearchController {
    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    @Autowired
    private VideoCacheService videoCacheService;

    /**
     * 搜索视频
     */
    @GetMapping
    public Result searchVideos(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {

        Criteria criteria = new Criteria();

        if (keyword != null && !keyword.isEmpty()) {
            criteria.or(Criteria.where("title").contains(keyword))
                   .or(Criteria.where("description").contains(keyword));
        }

        if (tag != null && !tag.isEmpty()) {
            criteria.and(Criteria.where("tags").is(tag));
        }

        CriteriaQuery query = new CriteriaQuery(criteria);
        query.setPageable(org.springframework.data.domain.PageRequest.of(page - 1, size));

        SearchHits<VideoDoc> searchHits = elasticsearchOperations.search(query, VideoDoc.class);

        List<Video> videos = new ArrayList<>();
        for (SearchHit<VideoDoc> hit : searchHits) {
            VideoDoc videoDoc = hit.getContent();
            // 从缓存获取完整视频元数据
            Video video = videoCacheService.getVideoMetadata(videoDoc.getId());
            if (video != null) {
                videos.add(video);
            }
        }

        return Result.success(videos);
    }
}