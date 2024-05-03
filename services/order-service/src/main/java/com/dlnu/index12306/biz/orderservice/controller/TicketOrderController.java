/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dlnu.index12306.biz.orderservice.controller;

import cn.crane4j.annotation.AutoOperate;
import com.dlnu.index12306.biz.orderservice.dto.req.CancelTicketOrderReqDTO;
import com.dlnu.index12306.biz.orderservice.dto.req.TicketOrderCreateReqDTO;
import com.dlnu.index12306.biz.orderservice.dto.req.TicketOrderPageQueryReqDTO;
import com.dlnu.index12306.biz.orderservice.dto.resp.TicketOrderDetailRespDTO;
import com.dlnu.index12306.biz.orderservice.service.OrderService;
import com.dlnu.index12306.framework.starter.convention.page.PageResponse;
import com.dlnu.index12306.framework.starter.convention.result.Result;
import com.dlnu.index12306.framework.starter.web.Results;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 车票订单接口控制层
 */
@RestController
@RequiredArgsConstructor
public class TicketOrderController {

    private final OrderService orderService;

    /**
     * 根据订单号查询车票订单
     */
    @GetMapping("/api/order-service/order/ticket/query")
    public Result<TicketOrderDetailRespDTO> queryTicketOrderByOrderSn(@RequestParam(value = "orderSn") String orderSn) {
        return Results.success(orderService.queryTicketOrderByOrderSn(orderSn));
    }

    /**
     * 分页查询车票订单
     */
    @AutoOperate(type = TicketOrderDetailRespDTO.class, on = "data.records")
    @GetMapping("/api/order-service/order/ticket/page")
    public Result<PageResponse<TicketOrderDetailRespDTO>> pageTicketOrder(TicketOrderPageQueryReqDTO requestParam) {
        return Results.success(orderService.pageTicketOrder(requestParam));
    }

    /**
     * 车票订单创建
     */
    @PostMapping("/api/order-service/order/ticket/create")
    public Result<String> createTicketOrder(@RequestBody TicketOrderCreateReqDTO requestParam) {
        return Results.success(orderService.createTicketOrder(requestParam));
    }

    /**
     * 车票订单取消
     */
    @PostMapping("/api/order-service/order/ticket/cancel")
    public Result<Boolean> cancelTickOrder(@RequestBody CancelTicketOrderReqDTO requestParam) {
        return Results.success(orderService.cancelTickOrder(requestParam));
    }

    /**
     * 车票订单关闭
     */
    @PostMapping("/api/order-service/order/ticket/close")
    public Result<Boolean> closeTickOrder(@RequestBody CancelTicketOrderReqDTO requestParam) {
        return Results.success(orderService.closeTickOrder(requestParam));
    }
}