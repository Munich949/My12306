package com.dlnu.index12306.biz.ticketservice.service.handler.ticket.select;

import com.dlnu.index12306.biz.ticketservice.common.enums.VehicleSeatTypeEnum;
import com.dlnu.index12306.biz.ticketservice.common.enums.VehicleTypeEnum;
import com.dlnu.index12306.biz.ticketservice.dto.domain.PurchaseTicketPassengerDetailDTO;
import com.dlnu.index12306.biz.ticketservice.dto.req.PurchaseTicketReqDTO;
import com.dlnu.index12306.biz.ticketservice.service.handler.ticket.dto.SelectSeatDTO;
import com.dlnu.index12306.biz.ticketservice.service.handler.ticket.dto.TrainPurchaseTicketRespDTO;
import com.dlnu.index12306.framework.starter.convention.exception.ServiceException;
import com.dlnu.index12306.framework.starter.designpattern.strategy.AbstractStrategyChoose;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class TrainSeatTypeSelector {

    private final AbstractStrategyChoose abstractStrategyChoose;
    private final ThreadPoolExecutor selectSeatThreadPoolExecutor;

    public List<TrainPurchaseTicketRespDTO> select(Integer trainType, PurchaseTicketReqDTO requestParam) {
        List<PurchaseTicketPassengerDetailDTO> passengers = requestParam.getPassengers();
        // 按照座位类型进行分组
        Map<Integer, List<PurchaseTicketPassengerDetailDTO>> seatTypeMap = passengers.stream()
                .collect(Collectors.groupingBy(PurchaseTicketPassengerDetailDTO::getSeatType));
        List<TrainPurchaseTicketRespDTO> actualResult = new CopyOnWriteArrayList<>();
        // 如果订单中的座位类型不止一种
        if (seatTypeMap.size() > 1) {
            List<Future<List<TrainPurchaseTicketRespDTO>>> futureResult = new ArrayList<>();
            seatTypeMap.forEach((seatType, passengerSeatDetails) -> {
                Future<List<TrainPurchaseTicketRespDTO>> completableFuture = selectSeatThreadPoolExecutor
                        .submit(() -> distributeSeats(trainType, seatType, requestParam, passengerSeatDetails));
                futureResult.add(completableFuture);
            });
        }
        return null;
    }

    /**
     * 分配座位
     */
    private List<TrainPurchaseTicketRespDTO> distributeSeats(Integer trainType, Integer seatType, PurchaseTicketReqDTO requestParam, List<PurchaseTicketPassengerDetailDTO> passengerSeatDetails) {
        String buildStrategyKey = VehicleTypeEnum.findNameByCode(trainType) + VehicleSeatTypeEnum.findNameByCode(seatType);
        SelectSeatDTO selectSeatDTO = SelectSeatDTO.builder()
                .seatType(seatType)
                .passengerSeatDetails(passengerSeatDetails)
                .purchaseTicketParam(requestParam)
                .build();
        try {
            // 策略模式 根据对应的列车类型进行选座
            return abstractStrategyChoose.chooseAndExecuteResp(buildStrategyKey, selectSeatDTO);
        } catch (ServiceException ex) {
            throw new ServiceException("当前车次列车类型暂未适配，请购买G35或G39车次");
        }
    }
}
