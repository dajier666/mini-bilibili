package com.rfid.video.Repository;


import com.rfid.video.entity.Video;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

@Repository
public interface VideoRepository extends ElasticsearchRepository<Video, Long> {
    // 1. 基本查询
    List<Video> findByTitle(String title);
    List<Video> findByTitleContaining(String keyword);
    List<Video> findByDescriptionContaining(String keyword);

    // 2. 标签查询
    List<Video> findByTagsContaining(String tag);
    List<Video> findByTagsIn(List<String> tags);

    // 3. 状态查询
    List<Video> findByStatus(Integer status);
    List<Video> findByStatusNot(Integer status);

    // 4. 时间范围查询
    List<Video> findByUploadTimeBetween(Date startTime, Date endTime);
    List<Video> findByUpdateTimeAfter(Date updateTime);

    // 5. 组合条件查询
    List<Video> findByTitleContainingAndStatus(String titleKeyword, Integer status);
    List<Video> findByTagsContainingAndStatusIn(String tag, List<Integer> statuses);

    // 6. 分页查询
    Page<Video> findByStatus(Integer status, Pageable pageable);
    Page<Video> findByTitleContaining(String keyword, Pageable pageable);

    // 7. 排序查询
    List<Video> findByStatusOrderByUploadTimeDesc(Integer status);
}