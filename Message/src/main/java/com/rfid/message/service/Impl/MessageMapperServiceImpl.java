package com.rfid.message.service.Impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.rfid.message.entity.Message;
import com.rfid.message.mapper.MessageMapper;
import com.rfid.message.service.MessageMapperService;
import org.springframework.stereotype.Service;

@Service
public class MessageMapperServiceImpl extends ServiceImpl<MessageMapper, Message> implements MessageMapperService {
}
