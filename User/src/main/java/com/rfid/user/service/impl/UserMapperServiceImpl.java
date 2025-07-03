package com.rfid.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.rfid.user.entity.User;
import com.rfid.user.mapper.UserMapper;
import com.rfid.user.service.UserMapperService;
import org.springframework.stereotype.Service;

@Service
public class UserMapperServiceImpl extends ServiceImpl<UserMapper, User> implements UserMapperService {
}