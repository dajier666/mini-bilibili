package com.rfid.message.service.MessageHandlerImpl;

import com.rfid.message.entity.Message;
import com.rfid.message.service.MessageHandler;
import com.rfid.message.service.Impl.WebsocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PromotionMessageHandler implements MessageHandler {
    @Autowired
    private WebsocketHandler websocketHandler;

    @Override
    public void handle(Message message) {
        // 推广通知逻辑
        message.setContent("(推广) "+message.getContent());
        websocketHandler.sendMessageToUser( message);
    }
}