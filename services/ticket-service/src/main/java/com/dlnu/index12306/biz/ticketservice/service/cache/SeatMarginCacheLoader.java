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

package com.dlnu.index12306.biz.ticketservice.service.cache;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dlnu.index12306.biz.ticketservice.common.enums.SeatStatusEnum;
import com.dlnu.index12306.biz.ticketservice.common.enums.VehicleTypeEnum;
import com.dlnu.index12306.biz.ticketservice.dao.entity.SeatDO;
import com.dlnu.index12306.biz.ticketservice.dao.entity.TrainDO;
import com.dlnu.index12306.biz.ticketservice.dao.mapper.SeatMapper;
import com.dlnu.index12306.biz.ticketservice.dao.mapper.TrainMapper;
import com.dlnu.index12306.biz.ticketservice.dto.domain.RouteDTO;
import com.dlnu.index12306.biz.ticketservice.service.TrainStationService;
import com.dlnu.index12306.framework.starter.cache.DistributedCache;
import com.dlnu.index12306.framework.starter.cache.toolkit.CacheUtil;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.dlnu.index12306.biz.ticketservice.common.constant.Index12306Constant.ADVANCE_TICKET_DAY;
import static com.dlnu.index12306.biz.ticketservice.common.constant.RedisKeyConstant.*;
import static com.dlnu.index12306.biz.ticketservice.common.enums.VehicleSeatTypeEnum.*;

/**
 * 座位余量缓存加载
 */
@Component
@RequiredArgsConstructor
public class SeatMarginCacheLoader {

    private final TrainMapper trainMapper;
    private final SeatMapper seatMapper;
    private final DistributedCache distributedCache;
    private final RedissonClient redissonClient;
    private final TrainStationService trainStationService;

    public Map<String, String> load(String trainId, String seatType, String departure, String arrival) {
        Map<String, Map<String, String>> trainStationRemainingTicketMaps = new LinkedHashMap<>();
        String keySuffix = CacheUtil.buildKey(trainId, departure, arrival);
        RLock lock = redissonClient.getLock(String.format(LOCK_SAFE_LOAD_SEAT_MARGIN_GET, keySuffix));
        lock.lock();
        try {
            StringRedisTemplate stringRedisTemplate = (StringRedisTemplate) distributedCache.getInstance();
            Object quantityObj = stringRedisTemplate.opsForHash().get(TRAIN_STATION_REMAINING_TICKET + keySuffix, seatType);
            if (CacheUtil.isNullOrBlank(quantityObj)) {
                // 获取列车基本信息
                TrainDO trainDO = distributedCache.safeGet(
                        TRAIN_INFO + trainId,
                        TrainDO.class,
                        () -> trainMapper.selectById(trainId),
                        ADVANCE_TICKET_DAY,
                        TimeUnit.DAYS
                );
                // 获取开始站点和目的站点及中间站点信息
                List<RouteDTO> routeDTOList = trainStationService.listTrainStationRoute(trainId, trainDO.getStartStation(), trainDO.getEndStation());
                if (CollUtil.isNotEmpty(routeDTOList)) {
                    // 根据列车类型获取列车类型对应席别的数量
                    switch (trainDO.getTrainType()) {
                        // 高铁
                        case 0 -> {
                            for (RouteDTO each : routeDTOList) {
                                Map<String, String> trainStationRemainingTicket = new LinkedHashMap<>();
                                trainStationRemainingTicket.put(String.valueOf(BUSINESS_CLASS.getCode()), selectSeatMargin(trainId, BUSINESS_CLASS.getCode(), each.getStartStation(), each.getEndStation()));
                                trainStationRemainingTicket.put(String.valueOf(FIRST_CLASS.getCode()), selectSeatMargin(trainId, FIRST_CLASS.getCode(), each.getStartStation(), each.getEndStation()));
                                trainStationRemainingTicket.put(String.valueOf(SECOND_CLASS.getCode()), selectSeatMargin(trainId, SECOND_CLASS.getCode(), each.getStartStation(), each.getEndStation()));
                                String actualKeySuffix = CacheUtil.buildKey(trainId, each.getStartStation(), each.getEndStation());
                                trainStationRemainingTicketMaps.put(TRAIN_STATION_REMAINING_TICKET + actualKeySuffix, trainStationRemainingTicket);
                            }
                        }
                        // 动车
                        case 1 -> {
                            for (RouteDTO each : routeDTOList) {
                                Map<String, String> trainStationRemainingTicket = new LinkedHashMap<>();
                                trainStationRemainingTicket.put(String.valueOf(SECOND_CLASS_CABIN_SEAT.getCode()), selectSeatMargin(trainId, SECOND_CLASS_CABIN_SEAT.getCode(), each.getStartStation(), each.getEndStation()));
                                trainStationRemainingTicket.put(String.valueOf(FIRST_SLEEPER.getCode()), selectSeatMargin(trainId, FIRST_SLEEPER.getCode(), each.getStartStation(), each.getEndStation()));
                                trainStationRemainingTicket.put(String.valueOf(SECOND_SLEEPER.getCode()), selectSeatMargin(trainId, SECOND_SLEEPER.getCode(), each.getStartStation(), each.getEndStation()));
                                trainStationRemainingTicket.put(String.valueOf(NO_SEAT_SLEEPER.getCode()), selectSeatMargin(trainId, NO_SEAT_SLEEPER.getCode(), each.getStartStation(), each.getEndStation()));
                                String actualKeySuffix = CacheUtil.buildKey(trainId, each.getStartStation(), each.getEndStation());
                                trainStationRemainingTicketMaps.put(TRAIN_STATION_REMAINING_TICKET + actualKeySuffix, trainStationRemainingTicket);
                            }
                        }
                        // 普通火车
                        case 2 -> {
                            for (RouteDTO each : routeDTOList) {
                                Map<String, String> trainStationRemainingTicket = new LinkedHashMap<>();
                                trainStationRemainingTicket.put(String.valueOf(SOFT_SLEEPER.getCode()), selectSeatMargin(trainId, SOFT_SLEEPER.getCode(), each.getStartStation(), each.getEndStation()));
                                trainStationRemainingTicket.put(String.valueOf(HARD_SLEEPER.getCode()), selectSeatMargin(trainId, HARD_SLEEPER.getCode(), each.getStartStation(), each.getEndStation()));
                                trainStationRemainingTicket.put(String.valueOf(HARD_SEAT.getCode()), selectSeatMargin(trainId, HARD_SEAT.getCode(), each.getStartStation(), each.getEndStation()));
                                trainStationRemainingTicket.put(String.valueOf(NO_SEAT_SLEEPER.getCode()), selectSeatMargin(trainId, NO_SEAT_SLEEPER.getCode(), each.getStartStation(), each.getEndStation()));
                                String actualKeySuffix = CacheUtil.buildKey(trainId, each.getStartStation(), each.getEndStation());
                                trainStationRemainingTicketMaps.put(TRAIN_STATION_REMAINING_TICKET + actualKeySuffix, trainStationRemainingTicket);
                            }
                        }
                    }
                } else {
                    Map<String, String> trainStationRemainingTicket = new LinkedHashMap<>();
                    VehicleTypeEnum.findSeatTypesByCode(trainDO.getTrainType())
                            .forEach(each -> trainStationRemainingTicket.put(String.valueOf(each), "0"));
                    trainStationRemainingTicketMaps.put(TRAIN_STATION_REMAINING_TICKET + keySuffix, trainStationRemainingTicket);
                }
                // TODO LUA 脚本执行
                trainStationRemainingTicketMaps.forEach((cacheKey, cacheMap) -> stringRedisTemplate.opsForHash().putAll(cacheKey, cacheMap));
            }
        } finally {
            lock.unlock();
        }
        return Optional.ofNullable(trainStationRemainingTicketMaps.get(TRAIN_STATION_REMAINING_TICKET + keySuffix))
                .orElse(new LinkedHashMap<>());
    }

    /**
     * 查询数据库中的余票数量
     *
     * @param trainId   车次ID
     * @param type      列车类型
     * @param departure 出发地
     * @param arrival   目的地
     * @return 余票数量
     */
    private String selectSeatMargin(String trainId, Integer type, String departure, String arrival) {
        LambdaQueryWrapper<SeatDO> queryWrapper = Wrappers.lambdaQuery(SeatDO.class)
                .eq(SeatDO::getTrainId, trainId)
                .eq(SeatDO::getSeatType, type)
                .eq(SeatDO::getSeatStatus, SeatStatusEnum.AVAILABLE.getCode())
                .eq(SeatDO::getStartStation, departure)
                .eq(SeatDO::getEndStation, arrival);
        return Optional.ofNullable(seatMapper.selectCount(queryWrapper))
                .map(String::valueOf)
                .orElse("0");
    }
}
