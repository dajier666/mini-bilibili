package com.rfid.user.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rfid.user.entity.UserPoints;

public interface UserPointsMapper extends BaseMapper<UserPoints> {
    default UserPoints selectByUserId(Long userId) {
        QueryWrapper<UserPoints> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userId", userId);
        return this.selectOne(queryWrapper);
    }
}