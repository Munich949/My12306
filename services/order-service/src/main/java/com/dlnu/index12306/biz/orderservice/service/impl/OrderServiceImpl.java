package com.dlnu.index12306.biz.orderservice.service.impl;

import cn.hutool.core.collection.ListUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dlnu.index12306.biz.orderservice.common.enums.OrderCancelErrorCodeEnum;
import com.dlnu.index12306.biz.orderservice.common.enums.OrderItemStatusEnum;
import com.dlnu.index12306.biz.orderservice.common.enums.OrderStatusEnum;
import com.dlnu.index12306.biz.orderservice.dao.entity.OrderDO;
import com.dlnu.index12306.biz.orderservice.dao.entity.OrderItemDO;
import com.dlnu.index12306.biz.orderservice.dao.entity.OrderItemPassengerDO;
import com.dlnu.index12306.biz.orderservice.dao.mapper.OrderItemMapper;
import com.dlnu.index12306.biz.orderservice.dao.mapper.OrderMapper;
import com.dlnu.index12306.biz.orderservice.dto.req.CancelTicketOrderReqDTO;
import com.dlnu.index12306.biz.orderservice.dto.req.TicketOrderCreateReqDTO;
import com.dlnu.index12306.biz.orderservice.dto.req.TicketOrderItemCreateReqDTO;
import com.dlnu.index12306.biz.orderservice.dto.req.TicketOrderPageQueryReqDTO;
import com.dlnu.index12306.biz.orderservice.dto.resp.TicketOrderDetailRespDTO;
import com.dlnu.index12306.biz.orderservice.dto.resp.TicketOrderPassengerDetailRespDTO;
import com.dlnu.index12306.biz.orderservice.mq.event.DelayCloseOrderEvent;
import com.dlnu.index12306.biz.orderservice.mq.produce.DelayCloseOrderSendProduce;
import com.dlnu.index12306.biz.orderservice.service.OrderItemService;
import com.dlnu.index12306.biz.orderservice.service.OrderPassengerRelationService;
import com.dlnu.index12306.biz.orderservice.service.OrderService;
import com.dlnu.index12306.biz.orderservice.service.orderid.OrderIDGeneratorManager;
import com.dlnu.index12306.framework.starter.common.toolkit.BeanUtil;
import com.dlnu.index12306.framework.starter.convention.exception.ClientException;
import com.dlnu.index12306.framework.starter.convention.exception.ServiceException;
import com.dlnu.index12306.framework.starter.convention.page.PageResponse;
import com.dlnu.index12306.framework.starter.database.toolkit.PageUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.dlnu.index12306.biz.orderservice.common.constant.RedisKeyConstant.LOCK_CANCEL_ORDER;

/**
 * 订单服务接口层实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderItemService orderItemService;
    private final OrderPassengerRelationService orderPassengerRelationService;
    private final DelayCloseOrderSendProduce delayCloseOrderSendProduce;
    private final RedissonClient redissonClient;


    @Override
    public TicketOrderDetailRespDTO queryTicketOrderByOrderSn(String orderSn) {
        LambdaQueryWrapper<OrderDO> queryWrapper = Wrappers.lambdaQuery(OrderDO.class)
                .eq(OrderDO::getOrderSn, orderSn);
        OrderDO orderDO = orderMapper.selectOne(queryWrapper);
        TicketOrderDetailRespDTO result = BeanUtil.convert(orderDO, TicketOrderDetailRespDTO.class);
        LambdaQueryWrapper<OrderItemDO> orderItemQueryWrapper = Wrappers.lambdaQuery(OrderItemDO.class)
                .eq(OrderItemDO::getOrderSn, orderSn);
        List<OrderItemDO> orderItemList = orderItemMapper.selectList(orderItemQueryWrapper);
        result.setPassengerDetails(BeanUtil.convert(orderItemList, TicketOrderPassengerDetailRespDTO.class));
        return result;
    }

    @Override
    public PageResponse<TicketOrderDetailRespDTO> pageTicketOrder(TicketOrderPageQueryReqDTO requestParam) {
        LambdaQueryWrapper<OrderDO> queryWrapper = Wrappers.lambdaQuery(OrderDO.class)
                .eq(OrderDO::getUserId, requestParam.getUserId())
                .in(OrderDO::getStatus, buildOrderStatusList(requestParam))
                .orderByDesc(OrderDO::getOrderTime);
        IPage<OrderDO> orderPage = orderMapper.selectPage(PageUtil.convert(requestParam), queryWrapper);
        return PageUtil.convert(orderPage, each -> {
            TicketOrderDetailRespDTO result = BeanUtil.convert(each, TicketOrderDetailRespDTO.class);
            LambdaQueryWrapper<OrderItemDO> orderItemQueryWrapper = Wrappers.lambdaQuery(OrderItemDO.class)
                    .eq(OrderItemDO::getOrderSn, each.getOrderSn());
            List<OrderItemDO> orderItemDOList = orderItemMapper.selectList(orderItemQueryWrapper);
            result.setPassengerDetails(BeanUtil.convert(orderItemDOList, TicketOrderPassengerDetailRespDTO.class));
            return result;
        });
    }

    @Override
    public String createTicketOrder(TicketOrderCreateReqDTO requestParam) {
        // 通过基因法将用户 ID 融入到订单号中
        String orderSn = OrderIDGeneratorManager.generateId(requestParam.getUserId());
        OrderDO orderDO = OrderDO.builder()
                .orderSn(orderSn)
                .orderTime(requestParam.getOrderTime())
                .departure(requestParam.getDeparture())
                .departureTime(requestParam.getDepartureTime())
                .ridingDate(requestParam.getRidingDate())
                .arrivalTime(requestParam.getArrivalTime())
                .trainNumber(requestParam.getTrainNumber())
                .arrival(requestParam.getArrival())
                .trainId(requestParam.getTrainId())
                .source(requestParam.getSource())
                .status(OrderStatusEnum.PENDING_PAYMENT.getStatus())
                .username(requestParam.getUsername())
                .userId(String.valueOf(requestParam.getUserId()))
                .build();
        orderMapper.insert(orderDO);
        List<TicketOrderItemCreateReqDTO> ticketOrderItems = requestParam.getTicketOrderItems();
        List<OrderItemDO> orderItemDOList = new ArrayList<>();
        List<OrderItemPassengerDO> orderPassengerRelationDOList = new ArrayList<>();
        ticketOrderItems.forEach(each -> {
            OrderItemDO orderItemDO = OrderItemDO.builder()
                    .trainId(requestParam.getTrainId())
                    .seatNumber(each.getSeatNumber())
                    .carriageNumber(each.getCarriageNumber())
                    .realName(each.getRealName())
                    .orderSn(orderSn)
                    .phone(each.getPhone())
                    .seatType(each.getSeatType())
                    .username(requestParam.getUsername())
                    .amount(each.getAmount())
                    .carriageNumber(each.getCarriageNumber())
                    .idCard(each.getIdCard())
                    .ticketType(each.getTicketType())
                    .idType(each.getIdType())
                    .userId(String.valueOf(requestParam.getUserId()))
                    .status(OrderStatusEnum.PENDING_PAYMENT.getStatus())
                    .build();
            orderItemDOList.add(orderItemDO);
            OrderItemPassengerDO orderPassengerRelationDO = OrderItemPassengerDO.builder()
                    .idType(each.getIdType())
                    .idCard(each.getIdCard())
                    .orderSn(orderSn)
                    .build();
            orderPassengerRelationDOList.add(orderPassengerRelationDO);
        });
        orderItemService.saveBatch(orderItemDOList);
        orderPassengerRelationService.saveBatch(orderPassengerRelationDOList);
        try {
            // 发送 RocketMQ 延时消息，指定时间后取消订单
            DelayCloseOrderEvent delayCloseOrderEvent = DelayCloseOrderEvent.builder()
                    .trainId(String.valueOf(requestParam.getTrainId()))
                    .departure(requestParam.getDeparture())
                    .arrival(requestParam.getArrival())
                    .orderSn(orderSn)
                    .trainPurchaseTicketResults(requestParam.getTicketOrderItems())
                    .build();
            SendResult sendResult = delayCloseOrderSendProduce.sendMessage(delayCloseOrderEvent);
            if (!Objects.equals(sendResult.getSendStatus(), SendStatus.SEND_OK)) {
                throw new ServiceException("投递延迟关闭订单消息队列失败");
            }
        } catch (Throwable ex) {
            log.error("延迟关闭订单消息队列发送错误，请求参数：{}", JSON.toJSONString(requestParam), ex);
            throw ex;
        }
        return orderSn;
    }

    @Override
    public boolean cancelTickOrder(CancelTicketOrderReqDTO requestParam) {
        String orderSn = requestParam.getOrderSn();
        LambdaQueryWrapper<OrderDO> queryWrapper = Wrappers.lambdaQuery(OrderDO.class)
                .eq(OrderDO::getOrderSn, orderSn);
        OrderDO orderDO = orderMapper.selectOne(queryWrapper);
        if (orderDO == null) {
            throw new ServiceException(OrderCancelErrorCodeEnum.ORDER_CANAL_UNKNOWN_ERROR);
        } else if (orderDO.getStatus() != OrderStatusEnum.PENDING_PAYMENT.getStatus()) {
            throw new ServiceException(OrderCancelErrorCodeEnum.ORDER_CANAL_STATUS_ERROR);
        }
        RLock lock = redissonClient.getLock(LOCK_CANCEL_ORDER + orderSn);
        if (!lock.tryLock()) {
            throw new ClientException(OrderCancelErrorCodeEnum.ORDER_CANAL_REPETITION_ERROR);
        }
        try {
            OrderDO updateOrderDO = new OrderDO();
            updateOrderDO.setStatus(OrderStatusEnum.CLOSED.getStatus());
            LambdaUpdateWrapper<OrderDO> updateWrapper = Wrappers.lambdaUpdate(OrderDO.class)
                    .eq(OrderDO::getOrderSn, orderSn);
            int updateResult = orderMapper.update(updateOrderDO, updateWrapper);
            if (updateResult <= 0) {
                throw new ServiceException(OrderCancelErrorCodeEnum.ORDER_CANAL_ERROR);
            }
            OrderItemDO updateOrderItemDO = new OrderItemDO();
            updateOrderItemDO.setStatus(OrderItemStatusEnum.CLOSED.getStatus());
            LambdaUpdateWrapper<OrderItemDO> updateItemWrapper = Wrappers.lambdaUpdate(OrderItemDO.class)
                    .eq(OrderItemDO::getOrderSn, orderSn);
            int updateItemResult = orderItemMapper.update(updateOrderItemDO, updateItemWrapper);
            if (updateItemResult <= 0) {
                throw new ServiceException(OrderCancelErrorCodeEnum.ORDER_CANAL_ERROR);
            }
        } finally {
            lock.unlock();
        }
        return true;
    }


    private List<Integer> buildOrderStatusList(TicketOrderPageQueryReqDTO requestParam) {
        List<Integer> result = new ArrayList<>();
        switch (requestParam.getStatusType()) {
            case 0 -> result = ListUtil.of(
                    OrderStatusEnum.PENDING_PAYMENT.getStatus()
            );
            case 1 -> result = ListUtil.of(
                    OrderStatusEnum.ALREADY_PAID.getStatus(),
                    OrderStatusEnum.PARTIAL_REFUND.getStatus(),
                    OrderStatusEnum.FULL_REFUND.getStatus()
            );
            case 2 -> result = ListUtil.of(
                    OrderStatusEnum.COMPLETED.getStatus()
            );
            default -> {
            }
        }
        return result;
    }
}