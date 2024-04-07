package com.dlnu.index12306.biz.ticketservice.service.handler.ticket;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.lang.Pair;
import cn.hutool.core.util.ObjectUtil;
import com.dlnu.index12306.biz.ticketservice.common.enums.VehicleSeatTypeEnum;
import com.dlnu.index12306.biz.ticketservice.common.enums.VehicleTypeEnum;
import com.dlnu.index12306.biz.ticketservice.dto.domain.PurchaseTicketPassengerDetailDTO;
import com.dlnu.index12306.biz.ticketservice.dto.domain.TrainSeatBaseDTO;
import com.dlnu.index12306.biz.ticketservice.service.SeatService;
import com.dlnu.index12306.biz.ticketservice.service.handler.ticket.base.AbstractTrainPurchaseTicketTemplate;
import com.dlnu.index12306.biz.ticketservice.service.handler.ticket.base.BitMapCheckSeat;
import com.dlnu.index12306.biz.ticketservice.service.handler.ticket.base.BitMapCheckSeatStatusFactory;
import com.dlnu.index12306.biz.ticketservice.service.handler.ticket.dto.SelectSeatDTO;
import com.dlnu.index12306.biz.ticketservice.service.handler.ticket.dto.TrainPurchaseTicketRespDTO;
import com.dlnu.index12306.biz.ticketservice.service.handler.ticket.select.SeatSelection;
import com.dlnu.index12306.biz.ticketservice.toolkit.CarriageVacantSeatCalculateUtil;
import com.dlnu.index12306.biz.ticketservice.toolkit.SeatNumberUtil;
import com.dlnu.index12306.framework.starter.convention.exception.ServiceException;
import com.google.common.collect.Lists;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.dlnu.index12306.biz.ticketservice.service.handler.ticket.base.BitMapCheckSeatStatusFactory.TRAIN_BUSINESS;

/**
 * 高铁商务座购票组件
 */
@Component
@RequiredArgsConstructor
public class TrainBusinessClassPurchaseTicketHandler extends AbstractTrainPurchaseTicketTemplate {

    private final SeatService seatService;
    private static final Map<Character, Integer> SEAT_Y_INT = Map.of('A', 0, 'C', 1, 'F', 2);

    @Override
    public String mark() {
        return VehicleTypeEnum.HIGH_SPEED_RAIN.getName() + VehicleSeatTypeEnum.BUSINESS_CLASS.getName();
    }

    @Override
    protected List<TrainPurchaseTicketRespDTO> selectSeats(SelectSeatDTO requestParam) {
        String trainId = requestParam.getPurchaseTicketParam().getTrainId();
        String departure = requestParam.getPurchaseTicketParam().getDeparture();
        String arrival = requestParam.getPurchaseTicketParam().getArrival();
        Integer seatType = requestParam.getSeatType();
        List<PurchaseTicketPassengerDetailDTO> passengerSeatDetails = requestParam.getPassengerSeatDetails();
        // 检索出有余票的车厢
        List<String> trainCarriageList = seatService.listUsableCarriageNumber(trainId, seatType, departure, arrival);
        // 计算有余票的车厢剩余票量
        List<Integer> trainStationCarriageRemainingTicket = seatService.listSeatRemainingTicket(trainId, departure, arrival, trainCarriageList);
        int remainingTicketSum = trainStationCarriageRemainingTicket.stream().mapToInt(Integer::intValue).sum();
        if (remainingTicketSum < passengerSeatDetails.size()) {
            throw new ServiceException("站点余票不足，请尝试更换座位类型或选择其它站点");
        }
        // 三人以下可以邻座 三人以上需要拆分座位
        if (passengerSeatDetails.size() < 3) {
            // 用户选座了 按照用户选的去锁定座位
            if (CollUtil.isNotEmpty(requestParam.getPurchaseTicketParam().getChooseSeats())) {
                Pair<List<TrainPurchaseTicketRespDTO>, Boolean> actualSeatPair = findMatchSeats(requestParam, trainCarriageList, trainStationCarriageRemainingTicket);
                return actualSeatPair.getKey();
            }
            // 没选座 自动分配座位
            return selectSeats(requestParam, trainCarriageList, trainStationCarriageRemainingTicket);
        } else {
            if (CollUtil.isNotEmpty(requestParam.getPurchaseTicketParam().getChooseSeats())) {
                Pair<List<TrainPurchaseTicketRespDTO>, Boolean> actualSeatPair = findMatchSeats(requestParam, trainCarriageList, trainStationCarriageRemainingTicket);
                return actualSeatPair.getKey();
            }
            return selectComplexSeats(requestParam, trainCarriageList, trainStationCarriageRemainingTicket);
        }
    }

    private Pair<List<TrainPurchaseTicketRespDTO>, Boolean> findMatchSeats(SelectSeatDTO requestParam, List<String> trainCarriageList, List<Integer> trainStationCarriageRemainingTicket) {
        TrainSeatBaseDTO trainSeatBaseDTO = buildTrainSeatBaseDTO(requestParam);
        // 选择的座位数量
        int chooseSeatSize = trainSeatBaseDTO.getChooseSeatList().size();
        // 乘客数量
        int passengerCount = trainSeatBaseDTO.getPassengerSeatDetails().size();
        List<TrainPurchaseTicketRespDTO> actualResult = Lists.newArrayListWithCapacity(passengerCount);
        // 构造高铁商务座座位选择器
        BitMapCheckSeat instance = BitMapCheckSeatStatusFactory.getInstance(TRAIN_BUSINESS);
        Map<String, List<Pair<Integer, Integer>>> carriagesSeatMap = new HashMap<>(4);
        for (int i = 0; i < trainStationCarriageRemainingTicket.size(); i++) {
            String carriagesNumber = trainCarriageList.get(i);
            // 获取列车车厢中可售的座位集合
            List<String> listAvailableSeat = seatService.listAvailableSeat(trainSeatBaseDTO.getTrainId(), carriagesNumber, requestParam.getSeatType(), trainSeatBaseDTO.getDeparture(), trainSeatBaseDTO.getArrival());
            // 商务座只有5个座位 两排三列 第一排第二列座位不可售
            int[][] actualSeats = new int[2][3];
            for (int j = 1; j < 3; j++) {
                for (int k = 1; k < 4; k++) {
                    // 如果可售的座位集合中包含（例如01A），则将表示座位的数组置为“1”（可售）
                    actualSeats[j - 1][k - 1] = listAvailableSeat.contains("0" + j + SeatNumberUtil.convert(0, k)) ? 0 : 1;
                }
            }
            List<Pair<Integer, Integer>> vacantSeatList = CarriageVacantSeatCalculateUtil.buildCarriageVacantSeatList2(actualSeats, 2, 3);
            boolean isExist = instance.checkChooseSeat(trainSeatBaseDTO.getChooseSeatList(), actualSeats, SEAT_Y_INT);
            int vacantSeatCount = vacantSeatList.size();
            // 初步确定要确定抢占的座位
            List<Pair<Integer, Integer>> sureSeatList = new ArrayList<>();
            // 最终选择到的座位
            List<String> selectSeats = Lists.newArrayListWithCapacity(passengerCount);
            boolean flag = false;
            if (isExist && vacantSeatCount >= passengerCount) {
                Iterator<Pair<Integer, Integer>> pairIterator = vacantSeatList.iterator();
                for (int i1 = 0; i1 < chooseSeatSize; i1++) {
                    // 选择的座位只有一个时
                    if (chooseSeatSize == 1) {
                        String chooseSeat = trainSeatBaseDTO.getChooseSeatList().get(i1);
                        int seatX = Integer.parseInt(chooseSeat.substring(1));
                        int seatY = SEAT_Y_INT.get(chooseSeat.charAt(0));
                        // 如果选择的座位为可售状态
                        if (actualSeats[seatX][seatY] == 0) {
                            sureSeatList.add(new Pair<>(seatX, seatY));
                            while (pairIterator.hasNext()) {
                                Pair<Integer, Integer> pair = pairIterator.next();
                                if (pair.getKey() == seatX && pair.getValue() == seatY) {
                                    // 从可售座位集合中移除
                                    pairIterator.remove();
                                    break;
                                }
                            }
                        } else {
                            // 如果选择的座位为不可售 但同行靠过道的座位可售 也可以锁定该座位
                            if (actualSeats[1][seatY] == 0) {
                                sureSeatList.add(new Pair<>(1, seatY));
                                while (pairIterator.hasNext()) {
                                    Pair<Integer, Integer> pair = pairIterator.next();
                                    if (pair.getKey() == 1 && pair.getValue() == seatY) {
                                        pairIterator.remove();
                                        break;
                                    }
                                }
                            } else {
                                // 未锁定到座位
                                flag = true;
                            }
                        }
                    } else {
                        String chooseSeat = trainSeatBaseDTO.getChooseSeatList().get(i1);
                        int seatX = Integer.parseInt(chooseSeat.substring(1));
                        int seatY = SEAT_Y_INT.get(chooseSeat.charAt(0));
                        if (actualSeats[seatX][seatY] == 0) {
                            sureSeatList.add(new Pair<>(seatX, seatY));
                            while (pairIterator.hasNext()) {
                                Pair<Integer, Integer> pair = pairIterator.next();
                                if (pair.getKey() == seatX && pair.getValue() == seatY) {
                                    pairIterator.remove();
                                    break;
                                }
                            }
                        }
                    }
                }
                // 如果没有锁定到座位 但有余票的车厢还没遍历完
                if (flag && i < trainStationCarriageRemainingTicket.size() - 1) {
                    continue;
                }
                // 如果遍历完有余票的车厢 还没有锁定足够购票数量的票
                if (sureSeatList.size() != passengerCount) {
                    // 从同车厢剩余的空座位中补足
                    int needSeatCount = passengerCount - sureSeatList.size();
                    sureSeatList.addAll(vacantSeatList.subList(0, needSeatCount));
                }
                for (Pair<Integer, Integer> each : sureSeatList) {
                    // 拼接好座位号 如01A
                    selectSeats.add("0" + (each.getKey() + 1) + SeatNumberUtil.convert(0, (each.getValue() + 1)));
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
                // 如果还没有遍历完所有车厢 则继续遍历
                if (i < trainStationCarriageRemainingTicket.size()) {
                    // 如果可售状态的座位数量大于0 就加入可选车厢的Map中 后续用于遍历
                    if (vacantSeatCount > 0) {
                        carriagesSeatMap.put(carriagesNumber, vacantSeatList);
                    }
                    // 如果已经遍历到了最后一个车厢了 则从遍历可选车厢的Map 从中找符合条件的座位
                    if (i == trainStationCarriageRemainingTicket.size() - 1) {
                        Pair<String, List<Pair<Integer, Integer>>> findSureCarriage = null;
                        // 如果有车厢可以满足用户购票的数量 则直接在此车厢分配相应数量的座位
                        for (Map.Entry<String, List<Pair<Integer, Integer>>> entry : carriagesSeatMap.entrySet()) {
                            if (entry.getValue().size() >= passengerCount) {
                                findSureCarriage = new Pair<>(entry.getKey(), entry.getValue().subList(0, passengerCount));
                                break;
                            }
                        }
                        if (findSureCarriage != null) {
                            sureSeatList = findSureCarriage.getValue().subList(0, passengerCount);
                            for (Pair<Integer, Integer> each : sureSeatList) {
                                selectSeats.add("0" + (each.getKey() + 1) + SeatNumberUtil.convert(0, each.getValue() + 1));
                            }
                            AtomicInteger countNum = new AtomicInteger(0);
                            for (String selectSeat : selectSeats) {
                                TrainPurchaseTicketRespDTO result = new TrainPurchaseTicketRespDTO();
                                PurchaseTicketPassengerDetailDTO currentTicketPassenger = trainSeatBaseDTO.getPassengerSeatDetails().get(countNum.getAndIncrement());
                                result.setSeatNumber(selectSeat);
                                result.setSeatType(currentTicketPassenger.getSeatType());
                                result.setCarriageNumber(findSureCarriage.getKey());
                                result.setPassengerId(currentTicketPassenger.getPassengerId());
                                actualResult.add(result);
                            }
                        } else {
                            // 如果没有任何一个车厢可以满足用户购票的数量 则从所有车厢中依次找可售座位 直至最终满足购票数量
                            int sureSeatListSize = 0;
                            AtomicInteger countNum = new AtomicInteger(0);
                            for (Map.Entry<String, List<Pair<Integer, Integer>>> entry : carriagesSeatMap.entrySet()) {
                                if (sureSeatListSize < passengerCount) {
                                    // 当前车厢的所有可售座位数量的和仍不满足用户的购票数量 直接将当前车厢的所有可售座位都锁定给该用户
                                    if (sureSeatListSize + entry.getValue().size() < passengerCount) {
                                        sureSeatListSize += entry.getValue().size();
                                        List<String> actualSelectSeats = new ArrayList<>();
                                        for (Pair<Integer, Integer> each : entry.getValue()) {
                                            actualSelectSeats.add("0" + (each.getKey() + 1) + SeatNumberUtil.convert(0, each.getValue() + 1));
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
                                        int needSeatSize = passengerCount - sureSeatListSize;
                                        sureSeatListSize += needSeatSize;
                                        if (sureSeatListSize >= passengerCount) {
                                            List<String> actualSelectSeats = new ArrayList<>();
                                            for (Pair<Integer, Integer> each : entry.getValue().subList(0, needSeatSize)) {
                                                actualSelectSeats.add("0" + (each.getKey() + 1) + SeatNumberUtil.convert(0, each.getValue() + 1));
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
                        }
                        return new Pair<>(actualResult, Boolean.TRUE);
                    }
                }
            }
        }
        return new Pair<>(null, Boolean.FALSE);
    }

    /**
     * 三人以下自动分配座位
     *
     * @param requestParam                        选座请求参数
     * @param trainCarriageList                   车厢号集合
     * @param trainStationCarriageRemainingTicket 车厢余票集合
     * @return 乘车人座位
     */
    private List<TrainPurchaseTicketRespDTO> selectSeats(SelectSeatDTO requestParam, List<String> trainCarriageList, List<Integer> trainStationCarriageRemainingTicket) {
        String trainId = requestParam.getPurchaseTicketParam().getTrainId();
        String departure = requestParam.getPurchaseTicketParam().getDeparture();
        String arrival = requestParam.getPurchaseTicketParam().getArrival();
        Integer seatType = requestParam.getSeatType();
        List<PurchaseTicketPassengerDetailDTO> passengerSeatDetails = requestParam.getPassengerSeatDetails();
        // 存储最终结果的列表
        List<TrainPurchaseTicketRespDTO> actualResult = new ArrayList<>();
        // 存储可座位降级的库存数量
        Map<String, Integer> demotionStockNumMap = new LinkedHashMap<>();
        // 存储实际的座位图
        Map<String, int[][]> actualSeatsMap = new HashMap<>();
        // 存储每个车厢的座位选择结果
        Map<String, int[][]> carriagesNumberSeatsMap = new HashMap<>();
        String carriagesNumber;
        for (int i = 0; i < trainStationCarriageRemainingTicket.size(); i++) {
            carriagesNumber = trainCarriageList.get(i);
            // 获取当前车厢所有可售状态的座位
            List<String> listAvailableSeat = seatService.listAvailableSeat(trainId, carriagesNumber, seatType, departure, arrival);
            int[][] actualSeats = new int[2][3];
            for (int j = 1; j < 3; j++) {
                for (int k = 1; k < 4; k++) {
                    actualSeats[j - 1][k - 1] = listAvailableSeat.contains("0" + j + SeatNumberUtil.convert(0, k)) ? 0 : 1;
                }
            }
            // 尝试获取相邻的座位
            int[][] select = SeatSelection.adjacent(passengerSeatDetails.size(), actualSeats);
            // 获取到了相邻座位 保存
            if (select != null) {
                carriagesNumberSeatsMap.put(carriagesNumber, select);
                break;
            }
            // 没找到相邻座位 计算此车厢的可座位降级的库存数量
            int demotionStockNum = 0;
            for (int[] actualSeat : actualSeats) {
                for (int i1 : actualSeat) {
                    // 可售状态的座位
                    if (i1 == 0) {
                        demotionStockNum++;
                    }
                }
            }
            // 记录每个车厢的可座位降级的库存数量
            demotionStockNumMap.putIfAbsent(carriagesNumber, demotionStockNum);
            actualSeatsMap.putIfAbsent(carriagesNumber, actualSeats);
            if (i < trainStationCarriageRemainingTicket.size() - 1) {
                continue;
            }
            // 对于邻座算法无法分配座位的情况，尝试降级分配策略：在同一车厢内分配不相邻座位
            for (Map.Entry<String, Integer> entry : demotionStockNumMap.entrySet()) {
                String carriagesNumberBack = entry.getKey();
                Integer demotionStockNumBack = entry.getValue();
                if (demotionStockNumBack > passengerSeatDetails.size()) {
                    int[][] seats = actualSeatsMap.get(carriagesNumberBack);
                    // 在当前车厢内执行非相邻座位选择
                    int[][] nonAdjacentSeats = SeatSelection.nonAdjacent(passengerSeatDetails.size(), seats);
                    // 分配成功 更新结果 退出降级分配策略
                    if (ObjectUtil.equals(nonAdjacentSeats.length, passengerSeatDetails.size())) {
                        select = nonAdjacentSeats;
                        carriagesNumberSeatsMap.put(carriagesNumberBack, select);
                        break;
                    }
                }
            }
            // 如果同车厢不相邻座位分配仍不满足条件，则进行进一步降级：在不同车厢内分配非相邻座位
            if (select == null) {
                for (Map.Entry<String, Integer> entry : demotionStockNumMap.entrySet()) {
                    String carriagesNumberBack = entry.getKey();
                    int demotionStockNumBack = entry.getValue();
                    int[][] seats = actualSeatsMap.get(carriagesNumberBack);
                    int[][] nonAdjacentSeats = SeatSelection.nonAdjacent(demotionStockNumBack, seats);
                    carriagesNumberSeatsMap.put(entry.getKey(), nonAdjacentSeats);
                }
            }
        }
        int count = (int) carriagesNumberSeatsMap.values().stream()
                .flatMap(Arrays::stream)
                .count();
        // 如果所有乘客的座位均已成功分配且每个乘客都有座位 则构建最终的购票相应信息
        if (CollUtil.isNotEmpty(carriagesNumberSeatsMap) && passengerSeatDetails.size() == count) {
            int countNum = 0;
            for (Map.Entry<String, int[][]> entry : carriagesNumberSeatsMap.entrySet()) {
                List<String> selectSeats = new ArrayList<>();
                for (int[] seats : entry.getValue()) {
                    selectSeats.add("0" + seats[0] + SeatNumberUtil.convert(0, seats[1]));
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

    /**
     * 三人及以上自动分配座位
     *
     * @param requestParam                        选座请求参数
     * @param trainCarriageList                   车厢号集合
     * @param trainStationCarriageRemainingTicket 车厢余票集合
     * @return 乘车人座位
     */
    private List<TrainPurchaseTicketRespDTO> selectComplexSeats(SelectSeatDTO requestParam, List<String> trainCarriageList, List<Integer> trainStationCarriageRemainingTicket) {
        String trainId = requestParam.getPurchaseTicketParam().getTrainId();
        String departure = requestParam.getPurchaseTicketParam().getDeparture();
        String arrival = requestParam.getPurchaseTicketParam().getArrival();
        Integer seatType = requestParam.getSeatType();
        List<PurchaseTicketPassengerDetailDTO> passengerSeatDetails = requestParam.getPassengerSeatDetails();
        List<TrainPurchaseTicketRespDTO> actualResult = new ArrayList<>();
        Map<String, Integer> demotionStockNumMap = new LinkedHashMap<>();
        Map<String, int[][]> actualSeatsMap = new HashMap<>();
        Map<String, int[][]> carriagesNumberSeatsMap = new HashMap<>();
        String carriagesNumber;
        for (int i = 0; i < trainCarriageList.size(); i++) {
            carriagesNumber = trainCarriageList.get(i);
            List<String> listAvailableSeat = seatService.listAvailableSeat(trainId, carriagesNumber, seatType, departure, arrival);
            int[][] actualSeats = new int[2][3];
            for (int j = 1; j < 3; j++) {
                for (int k = 1; k < 4; k++) {
                    actualSeats[j - 1][k - 1] = listAvailableSeat.contains("0" + j + SeatNumberUtil.convert(0, k)) ? 0 : 1;
                }
            }
            // 对座位矩阵进行深拷贝 备用
            int[][] actualSeatsTranscript = deepCopy(actualSeats);
            List<int[][]> actualSelects = new ArrayList<>();
            // 将乘客划分为每两个一组
            List<List<PurchaseTicketPassengerDetailDTO>> splitPassengerSeatDetails = ListUtil.split(passengerSeatDetails, 2);
            for (List<PurchaseTicketPassengerDetailDTO> each : splitPassengerSeatDetails) {
                int[][] select = SeatSelection.adjacent(each.size(), actualSeatsTranscript);
                // 如果找到了相邻的座位 将这些座位设置为锁定状态
                if (select != null) {
                    for (int[] seat : select) {
                        actualSeatsTranscript[seat[0] - 1][seat[1] - 1] = 1;
                    }
                    actualSelects.add(select);
                }
            }
            // 如果每组乘客都成功分配了座位 则中断分配过程
            if (actualSelects.size() == splitPassengerSeatDetails.size()) {
                // 合并所有锁定的座位
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
        int count = (int) carriagesNumberSeatsMap.values().stream()
                .flatMap(Arrays::stream)
                .count();
        if (CollUtil.isNotEmpty(carriagesNumberSeatsMap) && passengerSeatDetails.size() == count) {
            int countNum = 0;
            for (Map.Entry<String, int[][]> entry : carriagesNumberSeatsMap.entrySet()) {
                List<String> selectSeats = new ArrayList<>();
                for (int[] ints : entry.getValue()) {
                    selectSeats.add("0" + ints[0] + SeatNumberUtil.convert(0, ints[1]));
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
