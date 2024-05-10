package com.dlnu.index12306.biz.payservice.service;

import com.dlnu.index12306.biz.payservice.dto.base.PayRequest;
import com.dlnu.index12306.biz.payservice.dto.resp.PayRespDTO;

/**
 * 支付接口层
 */
public interface PayService {

    /**
     * 创建支付单
     *
     * @param requestParam 创建支付单实体
     * @return 支付返回详情
     */
    PayRespDTO commonPay(PayRequest requestParam);
}