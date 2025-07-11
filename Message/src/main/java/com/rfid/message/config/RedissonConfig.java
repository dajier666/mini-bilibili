package com.rfid.message.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient() throws IOException {
        Config config = new Config();
        config.useSingleServer()
              .setAddress("redis://localhost:6379");
        return Redisson.create(config);
    }
}