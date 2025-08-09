package com.rfid.video.config;

import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.client.CanalConnectors;
import com.rfid.video.entity.VideoUpdateMessage;
import com.rfid.video.service.impl.CanalClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class CanalConfig {
    @Value("${canal.server.host}")
    private String canalHost;

    @Value("${canal.server.port}")
    private int canalPort;

    @Value("${canal.destination}")
    private String destination;

    @Value("${canal.username}")
    private String username;

    @Value("${canal.password}")
    private String password;

    @Value("${canal.filter}")
    private String filter;

    @Bean
    public CanalConnector canalConnector() {
        return CanalConnectors.newSingleConnector(
                new InetSocketAddress(canalHost, canalPort),
                destination,
                username,
                password
        );
    }

    @Bean
    public ExecutorService canalExecutor() {
        return Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r);
            thread.setName("canal-client");
            return thread;
        });
    }

    @Bean
    public CanalClient canalClient(CanalConnector connector,
                                  ExecutorService executor,
                                  KafkaTemplate<String, VideoUpdateMessage> kafkaTemplate) {
        return new CanalClient(connector, executor, kafkaTemplate);
    }
}