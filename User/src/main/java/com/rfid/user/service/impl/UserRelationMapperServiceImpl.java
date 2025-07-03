package com.rfid.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.rfid.user.entity.UserRelation;
import com.rfid.user.mapper.UserRelationMapper;
import com.rfid.user.service.UserRelationMapperService;
import org.springframework.stereotype.Service;

@Service
public class UserRelationMapperServiceImpl extends ServiceImpl<UserRelationMapper, UserRelation> implements UserRelationMapperService {
}