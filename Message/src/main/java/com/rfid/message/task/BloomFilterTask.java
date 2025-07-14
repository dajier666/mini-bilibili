package com.rfid.message.task;

import com.rfid.message.service.Impl.MessageMapperServiceImpl;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
@Slf4j
@Component
public class BloomFilterTask {
    // 布隆过滤器：用于快速判断用户是否存在，防止缓存穿透
    @Getter
    private final RBloomFilter<Long> userBloomFilter;

    private final MessageMapperServiceImpl messageService;

    BloomFilterTask(RedissonClient redissonClient,
                    MessageMapperServiceImpl messageService) {
        this.messageService = messageService;
        // 初始化布隆过滤器（预计100万用户，误判率0.01%）
        this.userBloomFilter = redissonClient.getBloomFilter("userBloomFilter");
        userBloomFilter.tryInit(1000000, 0.0001);
    }

    @PostConstruct
    public void initCache() {
        log.info("Initializing user bloom filter...");

        // 从数据库加载所有用户ID到布隆过滤器
        List<Long> allUserIds = messageService.getBaseMapper().getAllUserIds();
        for (Long userId : allUserIds) {
            userBloomFilter.add(userId);
        }

        log.info("User bloom filter initialized with {} users", allUserIds.size());
    }

    // 定期更新布隆过滤器
    @Scheduled(fixedDelay = 20000)
    private void scheduledUserBloomFilter() {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(() -> {
            try {
                long currentCount = userBloomFilter.count();
                List<Long> newUserIds = messageService.getBaseMapper().getNewUserIds(currentCount);
                for (Long userId : newUserIds) {
                    userBloomFilter.add(userId);
                }
                log.info("Updated user bloom filter with {} new users", newUserIds.size());
            } catch (Exception e) {
                log.error("Failed to update user bloom filter", e);
            }
        }, 1, 1, TimeUnit.HOURS);
    }

}
