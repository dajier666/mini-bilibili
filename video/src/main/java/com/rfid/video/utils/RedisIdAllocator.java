package com.rfid.video.utils;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
@Slf4j
@Component
public class RedisIdAllocator {
    private static final String WORKER_ID_KEY = "snowflake:worker_id";
    private static final String DC_ID_KEY = "snowflake:datacenter_id";
    private static final String LOCK_KEY = "snowflake:id_lock";
    private static final long MAX_WORKER_ID = 31; // 5位最大值
    private static final long MAX_DC_ID = 31;     // 5位最大值

    private final RedissonClient redissonClient;

    public RedisIdAllocator(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * 获取唯一的机器ID
     */
    public long allocateWorkerId() {
        RLock lock = redissonClient.getLock(LOCK_KEY);
        try {
            // 尝试获取锁，最多等待10秒，锁持有时间30秒
            if (lock.tryLock(10, 30, TimeUnit.SECONDS)) {
                // 获取当前最大ID
                Long currentMaxId = redissonClient.getAtomicLong(WORKER_ID_KEY).get();

                // 循环分配，确保不超过最大值
                long nextId = (currentMaxId + 1) % (MAX_WORKER_ID + 1);
                redissonClient.getAtomicLong(WORKER_ID_KEY).set(nextId);

                log.info("Allocated worker ID: {}", nextId);
                return nextId;
            } else {
                log.error("Failed to acquire lock for worker ID allocation");
                throw new RuntimeException("Failed to allocate worker ID");
            }
        } catch (Exception e) {
            log.error("Error allocating worker ID", e);
            throw new RuntimeException("Error allocating worker ID", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 获取唯一的数据中心ID
     */
    public long allocateDataCenterId() {
        RLock lock = redissonClient.getLock(LOCK_KEY);
        try {
            if (lock.tryLock(10, 30, TimeUnit.SECONDS)) {
                Long currentMaxId = redissonClient.getAtomicLong(DC_ID_KEY).get();

                // 循环分配，确保不超过最大值
                long nextId = (currentMaxId + 1) % (MAX_DC_ID + 1);
                redissonClient.getAtomicLong(DC_ID_KEY).set(nextId);

                log.info("Allocated datacenter ID: {}", nextId);
                return nextId;
            } else {
                log.error("Failed to acquire lock for datacenter ID allocation");
                throw new RuntimeException("Failed to allocate datacenter ID");
            }
        } catch (Exception e) {
            log.error("Error allocating datacenter ID", e);
            throw new RuntimeException("Error allocating datacenter ID", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
