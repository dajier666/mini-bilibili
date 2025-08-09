package com.rfid.video.Repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rfid.video.entity.Video;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface VideoMapper extends BaseMapper<Video> {
}
