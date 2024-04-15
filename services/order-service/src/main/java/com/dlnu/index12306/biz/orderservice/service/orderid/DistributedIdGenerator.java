package com.dlnu.index12306.biz.orderservice.service.orderid;

/**
 * 全局唯一订单号生成器
 */
public class DistributedIdGenerator {

    /**
     * 时间基点
     */
    private static final long EPOCH = 1609459200000L;

    /**
     * 节点分配位数
     */
    private static final int NODE_BITS = 5;

    /**
     * 序列号分配位数
     */
    private static final int SEQUENCE_BITS = 7;

    /**
     * 节点ID
     */
    private final long nodeID;

    /**
     * 上一次生成ID的时间
     */
    private long lastTimestamp = -1L;

    /**
     * 上一次生成ID的序列号
     */
    private long sequence = 0L;

    public DistributedIdGenerator(long nodeID) {
        this.nodeID = nodeID;
    }

    /**
     * 生成分布式ID
     */
    public synchronized long generateId() {
        // 计算当前时间与基点时间的差值
        long timestamp = System.currentTimeMillis() - EPOCH;
        // 如果当前时间小于上次记录的时间戳 抛出异常 因为时钟回拨会导致 ID 重复
        if (timestamp < lastTimestamp) {
            throw new RuntimeException("Clock moved backwards. Refusing to generate ID.");
        }
        // 如果当前时间与上次记录的时间是同一毫秒内 需要在序列号上做增加 以保证同一毫秒内生成的 ID 是唯一的
        if (timestamp == lastTimestamp) {
            // (sequence + 1) & 127 得到序列号能取得的最大值 如果超过 2 的 7 次方序列号会绕回 0
            sequence = (sequence + 1) & ((1 << SEQUENCE_BITS) - 1);
            // 如果绕回 则证明当前毫秒内的序列号已经用完 需要等待直到下一个毫秒
            if (sequence == 0) {
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            // 新的毫秒内 序列号重置
            sequence = 0L;
        }
        lastTimestamp = timestamp;
        // 1. timestamp << (NODE_BITS + SEQUENCE_BITS): 时间戳左移 5 + 7 位 留出空间放置节点 ID 和序列号
        // 2. nodeID << SEQUENCE_BITS: nodeID 左移 7 位
        // 3. 使用 ｜ 将 timestamp、nodeID 和 sequence 合并
        return (timestamp << (NODE_BITS + SEQUENCE_BITS)) | (nodeID << SEQUENCE_BITS) | sequence;
    }

    /**
     * 等待下一个毫秒值
     */
    private long tilNextMillis(long lastTimestamp) {
        long timestamp = System.currentTimeMillis() - EPOCH;
        while (timestamp <= lastTimestamp) {
            timestamp = System.currentTimeMillis() - EPOCH;
        }
        return timestamp;
    }
}