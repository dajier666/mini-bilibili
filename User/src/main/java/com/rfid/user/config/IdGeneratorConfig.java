package com.rfid.user.config;

import com.rfid.user.utils.RedisIdAllocator;
import com.rfid.user.utils.SnowflakeIdGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IdGeneratorConfig {

    @Value("${snowflake.worker-id}")
    private long workerId;

    @Value("${snowflake.datacenter-id}")
    private long datacenterId;

    @Value("${snowflake.enable-auto}")
    private boolean enableAuto;

    private final RedisIdAllocator redisIdAllocator;

    public IdGeneratorConfig(RedisIdAllocator redisIdAllocator) {
        this.redisIdAllocator = redisIdAllocator;
    }

    @Bean
    public SnowflakeIdGenerator snowflakeIdGenerator() {
        if (enableAuto) {
            // 方式1：通过Redis自动分配唯一ID
            return createIdGeneratorWithRedis();
        } else {
            // 方式2：从配置文件读取固定ID
            return new SnowflakeIdGenerator(workerId, datacenterId);
        }
    }

    private SnowflakeIdGenerator createIdGeneratorWithRedis() {
        try {
            long allocatedWorkerId = redisIdAllocator.allocateWorkerId();
            long allocatedDataCenterId = redisIdAllocator.allocateDataCenterId();

            return new SnowflakeIdGenerator(allocatedWorkerId, allocatedDataCenterId);
        } catch (Exception e) {
            // 异常处理：记录日志并回退到固定ID
            System.err.println("Failed to get ID from Redis, fallback to configured ID");
            return new SnowflakeIdGenerator(workerId, datacenterId);
        }
    }
}
