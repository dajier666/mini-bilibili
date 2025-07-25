package com.rfid.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
<<<<<<<< HEAD:gateway/src/main/java/com/rfid/gateway/GatewayApplication.java
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
========
@EnableDiscoveryClient
public class AIChatApplication {
    public static void main(String[] args) {
        SpringApplication.run(AIChatApplication.class, args);
>>>>>>>> origin/master:AIChat/src/main/java/com/rfid/aichat/AiChatApplication.java
    }
}
