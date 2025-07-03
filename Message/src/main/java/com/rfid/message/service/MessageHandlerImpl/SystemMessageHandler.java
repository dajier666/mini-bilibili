package com.rfid.message.service.MessageHandlerImpl;

import com.rfid.message.entity.Message;
import com.rfid.message.service.MessageHandler;
import com.rfid.message.service.WebsocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SystemMessageHandler implements MessageHandler {
    @Autowired
    private WebsocketHandler websocketHandler;

    @Override
    public void handle(Message message) {
        // 系统通知逻辑
        message.setContent("(系统通知)"+message.getContent());
        websocketHandler.sendMessageToUser( message);
    }
}