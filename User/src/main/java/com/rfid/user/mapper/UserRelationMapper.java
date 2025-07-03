package com.rfid.user.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rfid.user.entity.UserRelation;

public interface UserRelationMapper extends BaseMapper<UserRelation> {
    default UserRelation selectByUserIdAndTargetId(Long userId,Long targetId) {
        QueryWrapper<UserRelation> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userId", userId).or().eq("targetId", targetId);
        return this.selectOne(queryWrapper);
    }
}