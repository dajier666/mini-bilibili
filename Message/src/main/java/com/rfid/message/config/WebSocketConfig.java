package com.rfid.message.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import com.rfid.message.service.Impl.WebsocketHandler;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final WebsocketHandler websocketHandler;

    public WebSocketConfig(WebsocketHandler websocketHandler) {
        this.websocketHandler = websocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(websocketHandler, "/ws/message/{userId}")
                .setAllowedOrigins("*");
    }
}