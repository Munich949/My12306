package com.dlnu.index12306.biz.ticketservice.service.handler.ticket;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.lang.Pair;
import com.dlnu.index12306.biz.ticketservice.common.enums.VehicleSeatTypeEnum;
import com.dlnu.index12306.biz.ticketservice.common.enums.VehicleTypeEnum;
import com.dlnu.index12306.biz.ticketservice.dto.domain.PurchaseTicketPassengerDetailDTO;
import com.dlnu.index12306.biz.ticketservice.dto.domain.TrainSeatBaseDTO;
import com.dlnu.index12306.biz.ticketservice.service.SeatService;
import com.dlnu.index12306.biz.ticketservice.service.handler.ticket.base.AbstractTrainPurchaseTicketTemplate;
import com.dlnu.index12306.biz.ticketservice.service.handler.ticket.dto.SelectSeatDTO;
import com.dlnu.index12306.biz.ticketservice.service.handler.ticket.dto.TrainPurchaseTicketRespDTO;
import com.dlnu.index12306.biz.ticketservice.service.handler.ticket.select.SeatSelection;
import com.dlnu.index12306.biz.ticketservice.toolkit.SeatNumberUtil;
import com.dlnu.index12306.framework.starter.convention.exception.ServiceException;
import com.google.common.collect.Lists;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 高铁一等座购票组件
 */
@Component
@RequiredArgsConstructor
public class TrainFirstClassPurchaseTicketHandler extends AbstractTrainPurchaseTicketTemplate {

    private final SeatService seatService;

    private static final Map<Character, Integer> SEAT_Y_INT = Map.of('A', 0, 'C', 1, 'D', 2, 'F', 3);

    @Override
    public String mark() {
        return VehicleTypeEnum.HIGH_SPEED_RAIN.getName() + VehicleSeatTypeEnum.FIRST_CLASS.getName();
    }

    @Override
    protected List<TrainPurchaseTicketRespDTO> selectSeats(SelectSeatDTO requestParam) {
        String trainId = requestParam.getPurchaseTicketParam().getTrainId();
        String departure = requestParam.getPurchaseTicketParam().getDeparture();
        String arrival = requestParam.getPurchaseTicketParam().getArrival();
        Integer seatType = requestParam.getSeatType();
        List<PurchaseTicketPassengerDetailDTO> passengerSeatDetails = requestParam.getPassengerSeatDetails();
        List<String> trainCarriageList = seatService.listUsableCarriageNumber(trainId, seatType, departure, arrival);
        List<Integer> trainStationCarriageRemainingTicket = seatService.listSeatRemainingTicket(trainId, departure, arrival, trainCarriageList);
        int remainingTicketSum = trainStationCarriageRemainingTicket.stream().mapToInt(Integer::intValue).sum();
        if (remainingTicketSum < passengerSeatDetails.size()) {
            throw new ServiceException("站点余票不足，请尝试更换座位类型或选择其它站点");
        }
        // 一等座五人以下可以邻座 五人以上需要拆分座位
        if (passengerSeatDetails.size() < 5) {
            if (CollUtil.isNotEmpty(requestParam.getPurchaseTicketParam().getChooseSeats())) {
                return findMatchSeats(requestParam, trainCarriageList, trainStationCarriageRemainingTicket).getKey();
            }
            return selectSeats(requestParam, trainCarriageList, trainStationCarriageRemainingTicket);
        } else {
            if (CollUtil.isNotEmpty(requestParam.getPurchaseTicketParam().getChooseSeats())) {
                return findMatchSeats(requestParam, trainCarriageList, trainStationCarriageRemainingTicket).getKey();
            }
            return selectComplexSeats(requestParam, trainCarriageList, trainStationCarriageRemainingTicket);
        }
    }

    /**
     * 计算当前余座情况是否满足用户选座情况
     *
     * @param actualSeats    实际余座情况
     * @param chooseSeatList 用户选座情况
     * @return 锁定的座位
     */
    private List<Pair<Integer, Integer>> calcChooseSeatLevelPairList(int[][] actualSeats, List<String> chooseSeatList) {
        String firstChooseSeat = CollUtil.getFirst(chooseSeatList);
        int firstSeatX = Integer.parseInt(firstChooseSeat.substring(1));
        int firstSeatY = SEAT_Y_INT.get(firstChooseSeat.charAt(0));
        List<Pair<Integer, Integer>> chooseSeatLevelPairList = new ArrayList<>();
        chooseSeatLevelPairList.add(new Pair<>(firstSeatX, firstSeatY));
        // 第一个选择的座位与其他座位X坐标最小差值
        int minLevelX = 0;
        for (int i = 1; i < chooseSeatList.size(); i++) {
            String chooseSeat = chooseSeatList.get(i);
            int chooseSeatX = Integer.parseInt(chooseSeat.substring(1));
            int chooseSeatY = SEAT_Y_INT.get(chooseSeat.charAt(0));
            // 更新X坐标的最小差值
            minLevelX = Math.min(minLevelX, chooseSeatX - firstSeatX);
            // 记录其他座位与第一个座位的行列差
            chooseSeatLevelPairList.add(new Pair<>(chooseSeatX - firstSeatX, chooseSeatY - firstSeatY));
        }
        // 从X坐标的最小差值开始遍历 目的是尽可能满足用户选择的座位偏好
        for (int i = Math.abs(minLevelX); i < 7; i++) {
            List<Pair<Integer, Integer>> sureSeatList = new ArrayList<>();
            // 遍历首选座位的X轴+最小偏移量(其实就是用户选择的离首选座位X轴的距离上最近的一个座位) 判断是否为可售座位
            if (actualSeats[i][firstSeatY] == 0) {
                sureSeatList.add(new Pair<>(i, firstSeatY));
                // 遍历其余选择的座位
                for (int j = 1; j < chooseSeatList.size(); j++) {
                    Pair<Integer, Integer> pair = chooseSeatLevelPairList.get(j);
                    int chooseSeatX = pair.getKey();
                    int chooseSeatY = pair.getValue();
                    int x = i + chooseSeatX;
                    // 如果偏移量已经大于一等座单节车厢的最大范围 则无法满足用户的偏好
                    if (x >= 7) {
                        return Collections.emptyList();
                    }
                    if (actualSeats[i + chooseSeatX][firstSeatY + chooseSeatY] == 0) {
                        sureSeatList.add(new Pair<>(i + chooseSeatX, firstSeatY + chooseSeatY));
                    } else {
                        break;
                    }
                }
            }
            // 满足用户偏好 返回要锁定座位
            if (sureSeatList.size() == chooseSeatList.size()) {
                return sureSeatList;
            }
        }
        return Collections.emptyList();
    }

    private Pair<List<TrainPurchaseTicketRespDTO>, Boolean> findMatchSeats(SelectSeatDTO requestParam, List<String> trainCarriageList, List<Integer> trainStationCarriageRemainingTicket) {
        TrainSeatBaseDTO trainSeatBaseDTO = buildTrainSeatBaseDTO(requestParam);
        List<TrainPurchaseTicketRespDTO> actualResult = Lists.newArrayListWithCapacity(trainSeatBaseDTO.getPassengerSeatDetails().size());
        Map<String, List<cn.hutool.core.lang.Pair<Integer, Integer>>> carriagesSeatMap = new HashMap<>(8);
        int passengersCount = trainSeatBaseDTO.getPassengerSeatDetails().size();
        for (int i = 0; i < trainStationCarriageRemainingTicket.size(); i++) {
            String carriagesNumber = trainCarriageList.get(i);
            List<String> listAvailableSeat = seatService.listAvailableSeat(trainSeatBaseDTO.getTrainId(), carriagesNumber, requestParam.getSeatType(), trainSeatBaseDTO.getDeparture(), trainSeatBaseDTO.getArrival());
            // 一等座每个车厢7排4列
            int[][] actualSeats = new int[7][4];
            List<Pair<Integer, Integer>> carriagesVacantSeat = new ArrayList<>();
            for (int j = 1; j < 8; j++) {
                for (int k = 1; k < 5; k++) {
                    actualSeats[j - 1][k - 1] = listAvailableSeat.contains("0" + j + SeatNumberUtil.convert(1, k)) ? 0 : 1;
                    if (actualSeats[j - 1][k - 1] == 0) {
                        carriagesVacantSeat.add(new Pair<>(j - 1, k - 1));
                    }
                }
            }
            List<String> selectSeats = new ArrayList<>(passengersCount);
            List<Pair<Integer, Integer>> sureSeatList = calcChooseSeatLevelPairList(actualSeats, trainSeatBaseDTO.getChooseSeatList());
            // 按照选择的座位分配且座位数量充足
            if (CollUtil.isNotEmpty(selectSeats) && carriagesVacantSeat.size() > passengersCount) {
                List<Pair<Integer, Integer>> vacantSeatList = new ArrayList<>();
                if (sureSeatList.size() != passengersCount) {
                    for (Pair<Integer, Integer> pair : sureSeatList) {
                        actualSeats[pair.getKey()][pair.getValue()] = 1;
                    }
                    for (int i1 = 0; i1 < 7; i1++) {
                        for (int j = 0; j < 4; j++) {
                            if (actualSeats[i1][j] == 0) {
                                vacantSeatList.add(new Pair<>(i1, j));
                            }
                        }
                    }
                    int needSeatSize = passengersCount - sureSeatList.size();
                    sureSeatList.addAll(vacantSeatList.subList(0, needSeatSize));
                }
                for (Pair<Integer, Integer> each : sureSeatList) {
                    selectSeats.add("0" + (each.getKey() + 1) + SeatNumberUtil.convert(1, each.getValue() + 1));
                }
                AtomicInteger countNum = new AtomicInteger(0);
                for (String selectSeat : selectSeats) {
                    TrainPurchaseTicketRespDTO result = new TrainPurchaseTicketRespDTO();
                    PurchaseTicketPassengerDetailDTO currentTicketPassenger = trainSeatBaseDTO.getPassengerSeatDetails().get(countNum.getAndIncrement());
                    result.setSeatNumber(selectSeat);
                    result.setSeatType(currentTicketPassenger.getSeatType());
                    result.setCarriageNumber(carriagesNumber);
                    result.setPassengerId(currentTicketPassenger.getPassengerId());
                    actualResult.add(result);
                }
                return new Pair<>(actualResult, Boolean.TRUE);
            } else {
                if (CollUtil.isNotEmpty(carriagesVacantSeat)) {
                    carriagesSeatMap.put(carriagesNumber, carriagesVacantSeat);
                    if (i == trainStationCarriageRemainingTicket.size() - 1) {
                        Pair<String, List<Pair<Integer, Integer>>> findSureCarriageSeat = null;
                        for (Map.Entry<String, List<Pair<Integer, Integer>>> entry : carriagesSeatMap.entrySet()) {
                            if (entry.getValue().size() >= passengersCount) {
                                findSureCarriageSeat = new Pair<>(entry.getKey(), entry.getValue().subList(0, passengersCount));
                                break;
                            }
                        }
                        if (findSureCarriageSeat != null) {
                            for (Pair<Integer, Integer> each : findSureCarriageSeat.getValue()) {
                                selectSeats.add("0" + (each.getKey() + 1) + SeatNumberUtil.convert(1, (each.getValue() + 1)));
                            }
                            AtomicInteger countNum = new AtomicInteger(0);
                            for (String selectSeat : selectSeats) {
                                TrainPurchaseTicketRespDTO result = new TrainPurchaseTicketRespDTO();
                                PurchaseTicketPassengerDetailDTO currentTicketPassenger = trainSeatBaseDTO.getPassengerSeatDetails().get(countNum.getAndIncrement());
                                result.setSeatNumber(selectSeat);
                                result.setSeatType(currentTicketPassenger.getSeatType());
                                result.setCarriageNumber(findSureCarriageSeat.getKey());
                                result.setPassengerId(currentTicketPassenger.getPassengerId());
                                actualResult.add(result);
                            }
                            return new Pair<>(actualResult, Boolean.TRUE);
                        } else {
                            int sureSeatListSize = 0;
                            AtomicInteger countNum = new AtomicInteger(0);
                            for (Map.Entry<String, List<Pair<Integer, Integer>>> entry : carriagesSeatMap.entrySet()) {
                                if (sureSeatListSize < passengersCount) {
                                    if (sureSeatListSize + entry.getValue().size() < passengersCount) {
                                        sureSeatListSize = sureSeatListSize + entry.getValue().size();
                                        List<String> actualSelectSeats = new ArrayList<>();
                                        for (Pair<Integer, Integer> each : entry.getValue()) {
                                            actualSelectSeats.add("0" + (each.getKey() + 1) + SeatNumberUtil.convert(1, each.getValue() + 1));
                                        }
                                        for (String selectSeat : actualSelectSeats) {
                                            TrainPurchaseTicketRespDTO result = new TrainPurchaseTicketRespDTO();
                                            PurchaseTicketPassengerDetailDTO currentTicketPassenger = trainSeatBaseDTO.getPassengerSeatDetails().get(countNum.getAndIncrement());
                                            result.setSeatNumber(selectSeat);
                                            result.setSeatType(currentTicketPassenger.getSeatType());
                                            result.setCarriageNumber(entry.getKey());
                                            result.setPassengerId(currentTicketPassenger.getPassengerId());
                                            actualResult.add(result);
                                        }
                                    } else {
                                        int needSeatSize = entry.getValue().size() - (sureSeatListSize + entry.getValue().size() - passengersCount);
                                        sureSeatListSize = sureSeatListSize + needSeatSize;
                                        if (sureSeatListSize >= passengersCount) {
                                            List<String> actualSelectSeats = new ArrayList<>();
                                            for (Pair<Integer, Integer> each : entry.getValue().subList(0, needSeatSize)) {
                                                actualSelectSeats.add("0" + (each.getKey() + 1) + SeatNumberUtil.convert(1, each.getValue() + 1));
                                            }
                                            for (String selectSeat : actualSelectSeats) {
                                                TrainPurchaseTicketRespDTO result = new TrainPurchaseTicketRespDTO();
                                                PurchaseTicketPassengerDetailDTO currentTicketPassenger = trainSeatBaseDTO.getPassengerSeatDetails().get(countNum.getAndIncrement());
                                                result.setSeatNumber(selectSeat);
                                                result.setSeatType(currentTicketPassenger.getSeatType());
                                                result.setCarriageNumber(entry.getKey());
                                                result.setPassengerId(currentTicketPassenger.getPassengerId());
                                                actualResult.add(result);
                                            }
                                            break;
                                        }
                                    }
                                }
                            }
                            return new Pair<>(actualResult, Boolean.TRUE);
                        }
                    }
                }
            }
        }
        return new Pair<>(null, Boolean.FALSE);
    }

    private List<TrainPurchaseTicketRespDTO> selectSeats(SelectSeatDTO requestParam, List<String> trainCarriageList, List<Integer> trainStationCarriageRemainingTicket) {
        String trainId = requestParam.getPurchaseTicketParam().getTrainId();
        String departure = requestParam.getPurchaseTicketParam().getDeparture();
        String arrival = requestParam.getPurchaseTicketParam().getArrival();
        List<PurchaseTicketPassengerDetailDTO> passengerSeatDetails = requestParam.getPassengerSeatDetails();
        List<TrainPurchaseTicketRespDTO> actualResult = new ArrayList<>();
        Map<String, Integer> demotionStockNumMap = new LinkedHashMap<>();
        Map<String, int[][]> actualSeatsMap = new HashMap<>();
        Map<String, int[][]> carriagesNumberSeatsMap = new HashMap<>();
        String carriagesNumber;
        for (int i = 0; i < trainStationCarriageRemainingTicket.size(); i++) {
            carriagesNumber = trainCarriageList.get(i);
            List<String> listAvailableSeat = seatService.listAvailableSeat(trainId, carriagesNumber, requestParam.getSeatType(), departure, arrival);
            int[][] actualSeats = new int[7][4];
            for (int j = 1; j < 8; j++) {
                for (int k = 1; k < 5; k++) {
                    // 当前默认按照复兴号商务座排序，后续这里需要按照简单工厂对车类型进行获取 y 轴
                    actualSeats[j - 1][k - 1] = listAvailableSeat.contains("0" + j + SeatNumberUtil.convert(1, k)) ? 0 : 1;
                }
            }
            int[][] select = SeatSelection.adjacent(passengerSeatDetails.size(), actualSeats);
            if (select != null) {
                carriagesNumberSeatsMap.put(carriagesNumber, select);
                break;
            }
            int demotionStockNum = 0;
            for (int[] actualSeat : actualSeats) {
                for (int i1 : actualSeat) {
                    if (i1 == 0) {
                        demotionStockNum++;
                    }
                }
            }
            demotionStockNumMap.putIfAbsent(carriagesNumber, demotionStockNum);
            actualSeatsMap.putIfAbsent(carriagesNumber, actualSeats);
            if (i < trainStationCarriageRemainingTicket.size() - 1) {
                continue;
            }
            // 如果邻座算法无法匹配，尝试对用户进行降级分配：同车厢不邻座
            for (Map.Entry<String, Integer> entry : demotionStockNumMap.entrySet()) {
                String carriagesNumberBack = entry.getKey();
                int demotionStockNumBack = entry.getValue();
                if (demotionStockNumBack > passengerSeatDetails.size()) {
                    int[][] seats = actualSeatsMap.get(carriagesNumberBack);
                    int[][] nonAdjacentSeats = SeatSelection.nonAdjacent(passengerSeatDetails.size(), seats);
                    if (Objects.equals(nonAdjacentSeats.length, passengerSeatDetails.size())) {
                        select = nonAdjacentSeats;
                        carriagesNumberSeatsMap.put(carriagesNumberBack, select);
                        break;
                    }
                }
            }
            // 如果同车厢也已无法匹配，则对用户座位再次降级：不同车厢不邻座
            if (Objects.isNull(select)) {
                for (Map.Entry<String, Integer> entry : demotionStockNumMap.entrySet()) {
                    String carriagesNumberBack = entry.getKey();
                    int demotionStockNumBack = entry.getValue();
                    int[][] seats = actualSeatsMap.get(carriagesNumberBack);
                    int[][] nonAdjacentSeats = SeatSelection.nonAdjacent(demotionStockNumBack, seats);
                    carriagesNumberSeatsMap.put(entry.getKey(), nonAdjacentSeats);
                }
            }
        }
        // 乘车人员在单一车厢座位不满足，触发乘车人元分布在不同车厢
        int count = (int) carriagesNumberSeatsMap.values().stream()
                .flatMap(Arrays::stream)
                .count();
        if (CollUtil.isNotEmpty(carriagesNumberSeatsMap) && passengerSeatDetails.size() == count) {
            int countNum = 0;
            for (Map.Entry<String, int[][]> entry : carriagesNumberSeatsMap.entrySet()) {
                List<String> selectSeats = new ArrayList<>();
                for (int[] ints : entry.getValue()) {
                    selectSeats.add("0" + ints[0] + SeatNumberUtil.convert(1, ints[1]));
                }
                for (String selectSeat : selectSeats) {
                    TrainPurchaseTicketRespDTO result = new TrainPurchaseTicketRespDTO();
                    PurchaseTicketPassengerDetailDTO currentTicketPassenger = passengerSeatDetails.get(countNum++);
                    result.setSeatNumber(selectSeat);
                    result.setSeatType(currentTicketPassenger.getSeatType());
                    result.setCarriageNumber(entry.getKey());
                    result.setPassengerId(currentTicketPassenger.getPassengerId());
                    actualResult.add(result);
                }
            }
        }
        return actualResult;
    }

    private List<TrainPurchaseTicketRespDTO> selectComplexSeats(SelectSeatDTO requestParam, List<String> trainCarriageList, List<Integer> trainStationCarriageRemainingTicket) {
        String trainId = requestParam.getPurchaseTicketParam().getTrainId();
        String departure = requestParam.getPurchaseTicketParam().getDeparture();
        String arrival = requestParam.getPurchaseTicketParam().getArrival();
        List<PurchaseTicketPassengerDetailDTO> passengerSeatDetails = requestParam.getPassengerSeatDetails();
        List<TrainPurchaseTicketRespDTO> actualResult = new ArrayList<>();
        Map<String, Integer> demotionStockNumMap = new LinkedHashMap<>();
        Map<String, int[][]> actualSeatsMap = new HashMap<>();
        Map<String, int[][]> carriagesNumberSeatsMap = new HashMap<>();
        String carriagesNumber;
        // 多人分配同一车厢邻座
        for (int i = 0; i < trainStationCarriageRemainingTicket.size(); i++) {
            carriagesNumber = trainCarriageList.get(i);
            List<String> listAvailableSeat = seatService.listAvailableSeat(trainId, carriagesNumber, requestParam.getSeatType(), departure, arrival);
            int[][] actualSeats = new int[7][4];
            for (int j = 1; j < 8; j++) {
                for (int k = 1; k < 5; k++) {
                    actualSeats[j - 1][k - 1] = listAvailableSeat.contains("0" + j + SeatNumberUtil.convert(1, k)) ? 0 : 1;
                }
            }
            int[][] actualSeatsTranscript = deepCopy(actualSeats);
            List<int[][]> actualSelects = new ArrayList<>();
            List<List<PurchaseTicketPassengerDetailDTO>> splitPassengerSeatDetails = ListUtil.split(passengerSeatDetails, 2);
            for (List<PurchaseTicketPassengerDetailDTO> each : splitPassengerSeatDetails) {
                int[][] select = SeatSelection.adjacent(each.size(), actualSeatsTranscript);
                if (select != null) {
                    for (int[] ints : select) {
                        actualSeatsTranscript[ints[0] - 1][ints[1] - 1] = 1;
                    }
                    actualSelects.add(select);
                }
            }
            if (actualSelects.size() == splitPassengerSeatDetails.size()) {
                int[][] actualSelect = null;
                for (int j = 0; j < actualSelects.size(); j++) {
                    if (j == 0) {
                        actualSelect = mergeArrays(actualSelects.get(j), actualSelects.get(j + 1));
                    }
                    if (j != 0 && actualSelects.size() > 2) {
                        actualSelect = mergeArrays(actualSelect, actualSelects.get(j + 1));
                    }
                }
                carriagesNumberSeatsMap.put(carriagesNumber, actualSelect);
                break;
            }
            int demotionStockNum = 0;
            for (int[] actualSeat : actualSeats) {
                for (int i1 : actualSeat) {
                    if (i1 == 0) {
                        demotionStockNum++;
                    }
                }
            }
            demotionStockNumMap.putIfAbsent(carriagesNumber, demotionStockNum);
            actualSeatsMap.putIfAbsent(carriagesNumber, actualSeats);
        }
        // 如果邻座算法无法匹配，尝试对用户进行降级分配：同车厢不邻座
        if (CollUtil.isEmpty(carriagesNumberSeatsMap)) {
            for (Map.Entry<String, Integer> entry : demotionStockNumMap.entrySet()) {
                String carriagesNumberBack = entry.getKey();
                int demotionStockNumBack = entry.getValue();
                if (demotionStockNumBack > passengerSeatDetails.size()) {
                    int[][] seats = actualSeatsMap.get(carriagesNumberBack);
                    int[][] nonAdjacentSeats = SeatSelection.nonAdjacent(passengerSeatDetails.size(), seats);
                    if (Objects.equals(nonAdjacentSeats.length, passengerSeatDetails.size())) {
                        carriagesNumberSeatsMap.put(carriagesNumberBack, nonAdjacentSeats);
                        break;
                    }
                }
            }
        }
        // 如果同车厢也已无法匹配，则对用户座位再次降级：不同车厢不邻座
        if (CollUtil.isEmpty(carriagesNumberSeatsMap)) {
            int undistributedPassengerSize = passengerSeatDetails.size();
            for (Map.Entry<String, Integer> entry : demotionStockNumMap.entrySet()) {
                String carriagesNumberBack = entry.getKey();
                int demotionStockNumBack = entry.getValue();
                int[][] seats = actualSeatsMap.get(carriagesNumberBack);
                int[][] nonAdjacentSeats = SeatSelection.nonAdjacent(Math.min(undistributedPassengerSize, demotionStockNumBack), seats);
                undistributedPassengerSize = undistributedPassengerSize - demotionStockNumBack;
                carriagesNumberSeatsMap.put(entry.getKey(), nonAdjacentSeats);
            }
        }
        // 乘车人员在单一车厢座位不满足，触发乘车人元分布在不同车厢
        int count = (int) carriagesNumberSeatsMap.values().stream()
                .flatMap(Arrays::stream)
                .count();
        if (CollUtil.isNotEmpty(carriagesNumberSeatsMap) && passengerSeatDetails.size() == count) {
            int countNum = 0;
            for (Map.Entry<String, int[][]> entry : carriagesNumberSeatsMap.entrySet()) {
                List<String> selectSeats = new ArrayList<>();
                for (int[] ints : entry.getValue()) {
                    selectSeats.add("0" + ints[0] + SeatNumberUtil.convert(1, ints[1]));
                }
                for (String selectSeat : selectSeats) {
                    TrainPurchaseTicketRespDTO result = new TrainPurchaseTicketRespDTO();
                    PurchaseTicketPassengerDetailDTO currentTicketPassenger = passengerSeatDetails.get(countNum++);
                    result.setSeatNumber(selectSeat);
                    result.setSeatType(currentTicketPassenger.getSeatType());
                    result.setCarriageNumber(entry.getKey());
                    result.setPassengerId(currentTicketPassenger.getPassengerId());
                    actualResult.add(result);
                }
            }
        }
        return actualResult;
    }

    public static int[][] mergeArrays(int[][] array1, int[][] array2) {
        List<int[]> list = new ArrayList<>(Arrays.asList(array1));
        list.addAll(Arrays.asList(array2));
        return list.toArray(new int[0][]);
    }

    public static int[][] deepCopy(int[][] originalArray) {
        int[][] copy = new int[originalArray.length][originalArray[0].length];
        for (int i = 0; i < originalArray.length; i++) {
            System.arraycopy(originalArray[i], 0, copy[i], 0, originalArray[i].length);
        }
        return copy;
    }
}