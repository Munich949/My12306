package com.dlnu.index12306.biz.orderservice.common.constant;

/**
 * Redis Key 定义常量类
 */
public class RedisKeyConstant {

    /**
     * 取消车票订单分布式锁，KeyPrefix + orderSn订单号
     */
    public static final String LOCK_CANCEL_ORDER = "index12306-order-service:lock:cancel_order";

    /**
     * 车票订单状态反转分布式锁，KeyPrefix + orderSn订单号
     */
    public static final String LOCK_STATUS_REVERSAL = "index12306-order-service:lock:status-reversal:";
}