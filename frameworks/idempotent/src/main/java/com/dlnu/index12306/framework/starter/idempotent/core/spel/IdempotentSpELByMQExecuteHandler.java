package com.dlnu.index12306.framework.starter.idempotent.core.spel;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import com.dlnu.index12306.framework.starter.cache.DistributedCache;
import com.dlnu.index12306.framework.starter.idempotent.annotation.Idempotent;
import com.dlnu.index12306.framework.starter.idempotent.core.*;
import com.dlnu.index12306.framework.starter.idempotent.enums.IdempotentMQConsumeStatusEnum;
import com.dlnu.index12306.framework.starter.idempotent.toolkit.LogUtil;
import com.dlnu.index12306.framework.starter.idempotent.toolkit.SpELUtil;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * 基于 SpEL 方法验证请求幂等性，适用于 MQ 场景
 */
@RequiredArgsConstructor
public final class IdempotentSpELByMQExecuteHandler extends AbstractIdempotentExecuteHandler implements IdempotentSpELService {

    private final DistributedCache distributedCache;
    private final static int TIMEOUT = 600;
    private final static String WRAPPER = "wrapper:spEL:MQ";

    /**
     * 构建幂等性参数包装类
     *
     * @param joinPoint AOP 方法处理
     * @return
     */
    @SneakyThrows
    @Override
    protected IdempotentParamWrapper buildWrapper(ProceedingJoinPoint joinPoint) {
        Idempotent idempotent = IdempotentAspect.getIdempotent(joinPoint);
        String key = (String) SpELUtil.parseKey(idempotent.key(), ((MethodSignature) joinPoint.getSignature()).getMethod(), joinPoint.getArgs());
        return IdempotentParamWrapper.builder().lockKey(key).joinPoint(joinPoint).build();
    }

    /**
     * 幂等性处理逻辑
     *
     * @param wrapper 幂等参数包装器
     */
    @Override
    public void handler(IdempotentParamWrapper wrapper) {
        // 构造唯一键
        String uniqueKey = wrapper.getIdempotent().uniqueKeyPrefix() + wrapper.getLockKey();
        Boolean setIfAbsent = ((StringRedisTemplate) distributedCache.getInstance())
                .opsForValue()
                .setIfAbsent(uniqueKey, IdempotentMQConsumeStatusEnum.CONSUMING.getCode(), TIMEOUT, TimeUnit.SECONDS);
        // 如果设置失败，则表示该请求已被处理过
        if (setIfAbsent != null && !setIfAbsent) {
            // 获取处理状态
            String consumeStatus = distributedCache.get(uniqueKey, String.class);
            // 判断是否为错误状态
            boolean error = IdempotentMQConsumeStatusEnum.isError(consumeStatus);
            LogUtil.getLog(wrapper.getJoinPoint()).warn("[{}] MQ repeated consumption, {}.", uniqueKey, error ? "Wait for the client to delay consumption" : "Status is completed");
            throw new RepeatConsumptionException(error);
        }
        IdempotentContext.put(WRAPPER, wrapper);
    }

    @Override
    public void exceptionProcessing() {
        IdempotentParamWrapper wrapper = (IdempotentParamWrapper) IdempotentContext.getKey(WRAPPER);
        if (wrapper != null) {
            Idempotent idempotent = wrapper.getIdempotent();
            String uniqueKey = idempotent.uniqueKeyPrefix() + wrapper.getLockKey();
            try {
                distributedCache.delete(uniqueKey);
            } catch (Throwable ex) {
                LogUtil.getLog(wrapper.getJoinPoint()).error("[{}] Failed to del MQ anti-heavy token.", uniqueKey);
            }
        }
    }

    @Override
    public void postProcessing() {
        IdempotentParamWrapper wrapper = (IdempotentParamWrapper) IdempotentContext.getKey(WRAPPER);
        if (wrapper != null) {
            Idempotent idempotent = wrapper.getIdempotent();
            String uniqueKey = idempotent.uniqueKeyPrefix() + wrapper.getLockKey();
            try {
                distributedCache.put(uniqueKey, IdempotentMQConsumeStatusEnum.CONSUMED.getCode(), idempotent.keyTimeout(), TimeUnit.SECONDS);
            } catch (Throwable ex) {
                LogUtil.getLog(wrapper.getJoinPoint()).error("[{}] Failed to set MQ anti-heavy token.", uniqueKey);
            }
        }
    }
}
