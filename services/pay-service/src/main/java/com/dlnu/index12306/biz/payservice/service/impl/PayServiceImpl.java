package com.dlnu.index12306.biz.payservice.service.impl;

import com.alibaba.fastjson2.JSON;
import com.dlnu.index12306.biz.payservice.common.enums.TradeStatusEnum;
import com.dlnu.index12306.biz.payservice.dao.entity.PayDO;
import com.dlnu.index12306.biz.payservice.dao.mapper.PayMapper;
import com.dlnu.index12306.biz.payservice.dto.base.PayRequest;
import com.dlnu.index12306.biz.payservice.dto.base.PayResponse;
import com.dlnu.index12306.biz.payservice.dto.resp.PayRespDTO;
import com.dlnu.index12306.biz.payservice.handler.AliPayNativeHandler;
import com.dlnu.index12306.biz.payservice.mq.produce.PayResultCallbackOrderSendProduce;
import com.dlnu.index12306.biz.payservice.service.PayService;
import com.dlnu.index12306.biz.payservice.service.payid.PayIdGeneratorManager;
import com.dlnu.index12306.framework.starter.cache.DistributedCache;
import com.dlnu.index12306.framework.starter.common.toolkit.BeanUtil;
import com.dlnu.index12306.framework.starter.convention.exception.ServiceException;
import com.dlnu.index12306.framework.starter.designpattern.strategy.AbstractStrategyChoose;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

import static com.dlnu.index12306.biz.payservice.common.constant.RedisKeyConstant.ORDER_PAY_RESULT_INFO;

/**
 * 支付接口层实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayServiceImpl implements PayService {

    private final DistributedCache distributedCache;
    private final AbstractStrategyChoose abstractStrategyChoose;
    private final PayMapper payMapper;
    private final PayResultCallbackOrderSendProduce payResultCallbackOrderSendProduce;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public PayRespDTO commonPay(PayRequest requestParam) {
        PayRespDTO cacheResult = distributedCache.get(ORDER_PAY_RESULT_INFO + requestParam.getOrderSn(), PayRespDTO.class);
        if (cacheResult != null) {
            return cacheResult;
        }
        /**
         * {@link AliPayNativeHandler}
         */
        // 策略模式：通过策略模式封装支付渠道和支付场景，用户支付时动态选择对应的支付组件
        PayResponse result = abstractStrategyChoose.chooseAndExecuteResp(requestParam.buildMark(), requestParam);
        PayDO insertPay = BeanUtil.convert(requestParam, PayDO.class);
        String paySn = PayIdGeneratorManager.generateId(requestParam.getOrderSn());
        insertPay.setPaySn(paySn);
        insertPay.setStatus(TradeStatusEnum.WAIT_BUYER_PAY.tradeCode());
        insertPay.setTotalAmount(requestParam.getTotalAmount().multiply(new BigDecimal("100")).setScale(0, BigDecimal.ROUND_HALF_UP).intValue());
        int insert = payMapper.insert(insertPay);
        if (insert <= 0) {
            log.error("支付单创建失败，支付聚合根：{}", JSON.toJSONString(requestParam));
            throw new ServiceException("支付单创建失败");
        }
        distributedCache.put(ORDER_PAY_RESULT_INFO + requestParam.getOrderSn(), JSON.toJSONString(result), 10, TimeUnit.MINUTES);
        return BeanUtil.convert(result, PayRespDTO.class);
    }
}