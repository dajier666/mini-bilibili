package com.rfid.user.utils;

import com.rfid.user.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class SnowflakeIdGenerator {
    // 起始时间戳（2020-01-01）
    private final long startTimeStamp = 1577836800000L;

    // 各部分所占位数
    private final long workerIdBits = 5L;
    private final long dataCenterIdBits = 5L;
    private final long sequenceBits = 12L;

    // 最大值计算
    private final long maxWorkerId = -1L ^ (-1L << workerIdBits);
    private final long maxDataCenterId = -1L ^ (-1L << dataCenterIdBits);

    // 位移量
    private final long workerIdShift = sequenceBits;
    private final long dataCenterIdShift = sequenceBits + workerIdBits;
    private final long timestampShift = sequenceBits + workerIdBits + dataCenterIdBits;

    // 序列号掩码
    private final long sequenceMask = -1L ^ (-1L << sequenceBits);

    // 使用AtomicLong保存时间戳和序列号
    private final AtomicLong timestampAndSequence = new AtomicLong(0);

    private final long workerId;
    private final long dataCenterId;

    // 构造函数
    public SnowflakeIdGenerator(long workerId, long dataCenterId) {
        if (workerId > maxWorkerId || workerId < 0) {
            throw new IllegalArgumentException("Worker ID must be between 0 and " + maxWorkerId);
        }
        if (dataCenterId > maxDataCenterId || dataCenterId < 0) {
            throw new IllegalArgumentException("DataCenter ID must be between 0 and " + maxDataCenterId);
        }
        this.workerId = workerId;
        this.dataCenterId = dataCenterId;
    }

    // 生成下一个ID
    public long nextId() {
        long currentTimestamp = System.currentTimeMillis();
        long oldValue, newValue;

        long sequence;
        do {
            oldValue = timestampAndSequence.get();
            long lastTimestamp = oldValue >>> sequenceBits;
            sequence = oldValue & sequenceMask;

            // 处理时钟回拨
            if (currentTimestamp < lastTimestamp) {
//                logger.warn("Clock moved backwards.  Refusing to generate id for {} milliseconds", lastTimestamp - currentTimestamp);
                throw new BusinessException("服务繁忙，无法为您生成对应id");
            }

            if (currentTimestamp == lastTimestamp) {
                sequence = (sequence + 1) & sequenceMask;
                if (sequence == 0) {
                    // 当前毫秒内序列号用尽，等待下一毫秒
                    currentTimestamp = waitNextMillis(lastTimestamp);
                }
            } else {
                // 时间戳改变，重置序列号
                sequence = 0L;
            }

            // 组合时间戳和序列号
            newValue = (currentTimestamp << sequenceBits) | sequence;

            // CAS操作更新
        } while (!timestampAndSequence.compareAndSet(oldValue, newValue));

        // 按规则组合生成最终ID
        return ((currentTimestamp - startTimeStamp) << timestampShift) |
                (dataCenterId << dataCenterIdShift) |
                (workerId << workerIdShift) |
                sequence;
    }

    // 等待下一毫秒
    private long waitNextMillis(long lastTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }
}