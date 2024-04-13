package com.dlnu.index12306.biz.ticketservice.service.handler.ticket;

import com.dlnu.index12306.biz.ticketservice.common.enums.VehicleSeatTypeEnum;
import com.dlnu.index12306.biz.ticketservice.common.enums.VehicleTypeEnum;
import com.dlnu.index12306.biz.ticketservice.service.SeatService;
import com.dlnu.index12306.biz.ticketservice.service.handler.ticket.base.AbstractTrainPurchaseTicketTemplate;
import com.dlnu.index12306.biz.ticketservice.service.handler.ticket.dto.SelectSeatDTO;
import com.dlnu.index12306.biz.ticketservice.service.handler.ticket.dto.TrainPurchaseTicketRespDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 高铁二等座购票组件
 */
@Component
@RequiredArgsConstructor
public class TrainSecondClassPurchaseTicketHandler extends AbstractTrainPurchaseTicketTemplate {


    private final SeatService seatService;

    private static final Map<Character, Integer> SEAT_Y_INT = Map.of('A', 0, 'B', 1, 'C', 2, 'D', 3, 'F', 4);

    @Override
    public String mark() {
        return VehicleTypeEnum.HIGH_SPEED_RAIN.getName() + VehicleSeatTypeEnum.SECOND_CLASS.getName();
    }

    @Override
    protected List<TrainPurchaseTicketRespDTO> selectSeats(SelectSeatDTO requestParam) {
        return List.of();
    }
}