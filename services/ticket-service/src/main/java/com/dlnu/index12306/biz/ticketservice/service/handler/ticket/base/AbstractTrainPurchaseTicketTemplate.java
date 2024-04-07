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

package com.dlnu.index12306.biz.ticketservice.service.handler.ticket.base;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.dlnu.index12306.biz.ticketservice.dto.domain.RouteDTO;
import com.dlnu.index12306.biz.ticketservice.dto.domain.TrainSeatBaseDTO;
import com.dlnu.index12306.biz.ticketservice.service.TrainStationService;
import com.dlnu.index12306.biz.ticketservice.service.handler.ticket.dto.SelectSeatDTO;
import com.dlnu.index12306.biz.ticketservice.service.handler.ticket.dto.TrainPurchaseTicketRespDTO;
import com.dlnu.index12306.framework.starter.bases.ApplicationContextHolder;
import com.dlnu.index12306.framework.starter.cache.DistributedCache;
import com.dlnu.index12306.framework.starter.designpattern.strategy.AbstractExecuteStrategy;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static com.dlnu.index12306.biz.ticketservice.common.constant.RedisKeyConstant.TRAIN_STATION_REMAINING_TICKET;

/**
 * 抽象高铁购票模板基础服务
 */
public abstract class AbstractTrainPurchaseTicketTemplate implements IPurchaseTicket, CommandLineRunner, AbstractExecuteStrategy<SelectSeatDTO, List<TrainPurchaseTicketRespDTO>> {

    private DistributedCache distributedCache;
    private String ticketAvailabilityCacheUpdateType;
    private TrainStationService trainStationService;

    /**
     * 选择座位
     *
     * @param requestParam 购票请求入参
     * @return 乘车人座位
     */
    protected abstract List<TrainPurchaseTicketRespDTO> selectSeats(SelectSeatDTO requestParam);

    /**
     * 构建选座请求参数
     */
    protected TrainSeatBaseDTO buildTrainSeatBaseDTO(SelectSeatDTO requestParam) {
        return TrainSeatBaseDTO.builder()
                .trainId(requestParam.getPurchaseTicketParam().getTrainId())
                .departure(requestParam.getPurchaseTicketParam().getDeparture())
                .arrival(requestParam.getPurchaseTicketParam().getArrival())
                .chooseSeatList(requestParam.getPurchaseTicketParam().getChooseSeats())
                .passengerSeatDetails(requestParam.getPassengerSeatDetails())
                .build();
    }

    @Override
    public List<TrainPurchaseTicketRespDTO> executeResp(SelectSeatDTO requestParam) {
        // 模板设计模式，根据子类重写的选座方法进行选座
        List<TrainPurchaseTicketRespDTO> actualResult = selectSeats(requestParam);
        // 扣减车厢余票缓存，扣减站点余票缓存
        if (CollUtil.isNotEmpty(actualResult) && !StrUtil.equals(ticketAvailabilityCacheUpdateType, "binlog")) {
            String trainId = requestParam.getPurchaseTicketParam().getTrainId();
            String departure = requestParam.getPurchaseTicketParam().getDeparture();
            String arrival = requestParam.getPurchaseTicketParam().getArrival();
            StringRedisTemplate stringRedisTemplate = (StringRedisTemplate) distributedCache.getInstance();
            // 计算需要扣减的站点
            List<RouteDTO> routeDTOList = trainStationService.listTakeoutTrainStationRoute(trainId, departure, arrival);
            // 扣减余票
            routeDTOList.forEach(each -> {
                String keySuffix = StrUtil.join("_", trainId, each.getStartStation(), each.getEndStation());
                stringRedisTemplate.opsForHash().increment(TRAIN_STATION_REMAINING_TICKET + keySuffix, String.valueOf(requestParam.getSeatType()), -actualResult.size());
            });
        }
        return actualResult;
    }

    @Override
    public void run(String... args) throws Exception {
        distributedCache = ApplicationContextHolder.getBean(DistributedCache.class);
        trainStationService = ApplicationContextHolder.getBean(TrainStationService.class);
        ConfigurableEnvironment configurableEnvironment = ApplicationContextHolder.getBean(ConfigurableEnvironment.class);
        ticketAvailabilityCacheUpdateType = configurableEnvironment.getProperty("ticket.availability.cache-update.type", "");
    }
}
