package com.rfid.message.service.Impl;

import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rfid.message.entity.Message;

@Service
public class WebsocketHandler extends TextWebSocketHandler {
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private static final ObjectMapper objectMapper = new ObjectMapper();
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String userId = getUserIdFromSession(session);
        if (userId != null) {
            sessions.put(userId, session);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        String userId = getUserIdFromSession(session);
        if (userId != null) {
            // 处理消息逻辑
            //session.sendMessage(new TextMessage("用户 " + userId + "，收到消息: " + message.getPayload()));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String userId = getUserIdFromSession(session);
        if (userId != null) {
            sessions.remove(userId);
        }
    }

    private String getUserIdFromSession(WebSocketSession session) {
        Map<String, String> uriVars = (Map<String, String>) session.getAttributes().get("uriVars");
        return uriVars != null ? uriVars.get("userId") : null;
    }

    /**
     * 向指定用户发送消息
     */
    public void sendMessageToUser(Message message) {
        WebSocketSession session = sessions.get(message.getTargetId().toString());
        if (session != null && session.isOpen()) {
            try {
                String jsonMessage = objectMapper.writeValueAsString(message);
                session.sendMessage(new TextMessage(jsonMessage));
            } catch (JsonProcessingException e) {
                System.err.println("JSON 序列化失败: " + e.getMessage());
            } catch (IOException e) {
                System.err.println("消息发送失败: " + e.getMessage());
            }
        }
    }




}

