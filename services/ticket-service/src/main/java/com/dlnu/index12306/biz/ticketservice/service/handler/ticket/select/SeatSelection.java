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

package com.dlnu.index12306.biz.ticketservice.service.handler.ticket.select;

import cn.hutool.core.collection.CollUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * 座位选择器
 */
public class SeatSelection {

    /**
     * 为一组乘客找到相邻的座位
     *
     * @param numSeats   座位数量
     * @param seatLayout 当前座位状态布局 0为可售 1为已售
     * @return 表示相邻座位的数组
     */
    public static int[][] adjacent(int numSeats, int[][] seatLayout) {
        int numRows = seatLayout.length;
        int numCols = seatLayout[0].length;
        List<int[]> selectedSeats = new ArrayList<>();

        for (int i = 0; i < numRows; i++) {
            for (int j = 0; j < numCols; j++) {
                // 如果当前座位是空的
                if (seatLayout[i][j] == 0) {
                    // 用来统计连续空座位的数量
                    int consecutiveSeats = 0;
                    for (int k = j; k < numCols; k++) {
                        if (seatLayout[i][k] == 0) {
                            consecutiveSeats++;
                            // 如果找到了足够的连续空座
                            if (consecutiveSeats == numSeats) {
                                for (int l = k - numSeats + 1; l <= k; l++) {
                                    // 把连续空座的位置添加到列表中
                                    selectedSeats.add(new int[]{i, l});
                                }
                                break;
                            }
                        } else {
                            // 遇到非空座位，连续空座计数器重置为0
                            consecutiveSeats = 0;
                        }
                    }
                    if (!selectedSeats.isEmpty()) {
                        break;
                    }
                }
            }
            if (!selectedSeats.isEmpty()) {
                break;
            }
        }
        if (CollUtil.isEmpty(selectedSeats)) {
            return null;
        }
        int[][] actualSeat = new int[numSeats][2];
        int i = 0;
        // 将列表中的座位信息转换为数组格式
        for (int[] seat : selectedSeats) {
            int row = seat[0] + 1;
            int col = seat[1] + 1;
            actualSeat[i][0] = row;
            actualSeat[i][1] = col;
            i++;
        }
        return actualSeat;
    }

    /**
     * 当无法为乘客分配相邻座位时，选择非相邻座位
     *
     * @param numSeats   座位数量
     * @param seatLayout 当前座位状态布局 0为可售 1为已售
     * @return 表示相邻座位的数组
     */
    public static int[][] nonAdjacent(int numSeats, int[][] seatLayout) {
        int numRows = seatLayout.length;
        int numCols = seatLayout[0].length;
        List<int[]> selectedSeats = new ArrayList<>();

        for (int i = 0; i < numRows; i++) {
            for (int j = 0; j < numCols; j++) {
                if (seatLayout[i][j] == 0) {
                    selectedSeats.add(new int[]{i, j});
                    // 如果已选择的座位数量达到需求量
                    if (selectedSeats.size() == numSeats) {
                        break;
                    }
                }
            }
            if (selectedSeats.size() == numSeats) {
                break;
            }
        }
        // 把列表中的坐标转换为数组并返回
        return convertToActualSeat(selectedSeats);
    }

    /**
     * 将选定列表中的座位坐标转换为数组
     *
     * @param selectedSeats 选中的座位坐标
     * @return 表示座位信息的数组
     */
    private static int[][] convertToActualSeat(List<int[]> selectedSeats) {
        // 此方法
        int[][] actualSeat = new int[selectedSeats.size()][2];
        for (int i = 0; i < selectedSeats.size(); i++) {
            int[] seat = selectedSeats.get(i);
            int row = seat[0] + 1;
            int col = seat[1] + 1;
            actualSeat[i][0] = row;
            actualSeat[i][1] = col;
        }
        return actualSeat;
    }

    public static void main(String[] args) {
        int[][] seatLayout = {
                {1, 1, 1, 1},
                {1, 1, 1, 0},
                {1, 1, 1, 0},
                {0, 0, 0, 0}
        };
        int[][] select = adjacent(2, seatLayout);
        System.out.println("成功预订相邻座位，座位位置为：");
        assert select != null;
        for (int[] ints : select) {
            System.out.printf("第 %d 排，第 %d 列%n", ints[0], ints[1]);
        }

        int[][] seatLayoutTwo = {
                {1, 0, 1, 1},
                {1, 1, 0, 0},
                {1, 1, 1, 0},
                {0, 0, 0, 0}
        };
        int[][] selectTwo = nonAdjacent(3, seatLayoutTwo);
        System.out.println("成功预订不相邻座位，座位位置为：");
        for (int[] ints : selectTwo) {
            System.out.printf("第 %d 排，第 %d 列%n", ints[0], ints[1]);
        }
    }
}
