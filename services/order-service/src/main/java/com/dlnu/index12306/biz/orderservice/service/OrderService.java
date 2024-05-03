package com.dlnu.index12306.biz.orderservice.service;

import com.dlnu.index12306.biz.orderservice.dto.req.CancelTicketOrderReqDTO;
import com.dlnu.index12306.biz.orderservice.dto.req.TicketOrderCreateReqDTO;
import com.dlnu.index12306.biz.orderservice.dto.req.TicketOrderPageQueryReqDTO;
import com.dlnu.index12306.biz.orderservice.dto.resp.TicketOrderDetailRespDTO;
import com.dlnu.index12306.framework.starter.convention.page.PageResponse;

/**
 * 订单接口层
 */
public interface OrderService {

    /**
     * 根据订单号查询车票订单
     *
     * @param orderSn 订单号
     * @return 订单详情
     */
    TicketOrderDetailRespDTO queryTicketOrderByOrderSn(String orderSn);

    /**
     * 跟据用户名分页查询车票订单
     *
     * @param requestParam 跟据用户 ID 分页查询对象
     * @return 订单分页详情
     */
    PageResponse<TicketOrderDetailRespDTO> pageTicketOrder(TicketOrderPageQueryReqDTO requestParam);

    /**
     * 创建火车票订单
     *
     * @param requestParam 商品订单入参
     * @return 订单号
     */
    String createTicketOrder(TicketOrderCreateReqDTO requestParam);

    /**
     * 取消车票订单
     *
     * @param requestParam 取消车票订单入参
     */
    boolean cancelTickOrder(CancelTicketOrderReqDTO requestParam);

    /**
     * 关闭火车票订单
     *
     * @param requestParam 关闭火车票订单入参
     */
    boolean closeTickOrder(CancelTicketOrderReqDTO requestParam);
}