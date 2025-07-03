package com.rfid.user.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rfid.user.entity.User;


public interface UserMapper extends BaseMapper<User> {
    default User selectByPhoneOrEmail(String phone, String email) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("phone", phone).or().eq("email", email);
        return this.selectOne(queryWrapper);
    }
}
