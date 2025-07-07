package com.rfid.message.service.Impl;

import com.rfid.message.entity.Message;
import com.rfid.message.enums.MessageType;
import com.rfid.message.service.MessageHandler;
import com.rfid.message.service.MessageHandlerImpl.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class MessageHandlerFactory {
    private final Map<Integer, MessageHandler> handlerMap = new HashMap<>();

    @Autowired
    public MessageHandlerFactory(FollowMessageHandler followHandler,
                                 SystemMessageHandler systemHandler,
                                 PromotionMessageHandler promotionHandler,
                                 PrivateMessageHandler privateHandler,
                                 GroupMessageHandler groupHandler) {
        handlerMap.put(MessageType.FOLLOW.getCode(), followHandler);
        handlerMap.put(MessageType.SYSTEM.getCode(), systemHandler);
        handlerMap.put(MessageType.PROMOTION.getCode(), promotionHandler);
        handlerMap.put(MessageType.PRIVATE.getCode(), privateHandler);
        handlerMap.put(MessageType.GROUP.getCode(), groupHandler);
    }

    public MessageHandler getHandler(Message message) {
        return handlerMap.get(message.getType());
    }
}