package com.rfid.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.rfid.user.entity.UserPoints;
import com.rfid.user.mapper.UserPointsMapper;
import com.rfid.user.service.UserPointsMapperService;
import org.springframework.stereotype.Service;

@Service
public class UserPointsMapperServiceImpl extends ServiceImpl<UserPointsMapper, UserPoints> implements UserPointsMapperService {
}