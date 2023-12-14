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

package com.dlnu.index12306.framework.starter.idempotent.core.param;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.alibaba.fastjson2.JSON;
import com.dlnu.index12306.framework.starter.user.core.UserContext;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import com.dlnu.index12306.framework.starter.convention.exception.ClientException;
import com.dlnu.index12306.framework.starter.idempotent.core.AbstractIdempotentExecuteHandler;
import com.dlnu.index12306.framework.starter.idempotent.core.IdempotentContext;
import com.dlnu.index12306.framework.starter.idempotent.core.IdempotentParamWrapper;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 基于方法参数验证请求幂等性
 */
@RequiredArgsConstructor
public final class IdempotentParamExecuteHandler extends AbstractIdempotentExecuteHandler implements IdempotentParamService {

    private final RedissonClient redissonClient;

    private final static String LOCK = "lock:param:restAPI";

    /**
     * 构建幂等性参数包装类
     *
     * @param joinPoint 切点对象
     * @return 幂等性参数包装类
     */
    @Override
    protected IdempotentParamWrapper buildWrapper(ProceedingJoinPoint joinPoint) {
        String lockKey = String.format("idempotent:path:%s:currentUserId:%s:md5:%s", getServletPath(), getCurrentUserId(), calcArgsMD5(joinPoint));
        // 锁名称由 URL、用户 ID 和方法参数的 MD5 值组成
        return IdempotentParamWrapper.builder().lockKey(lockKey).joinPoint(joinPoint).build();
    }

    /**
     * 获取当前请求的 URL
     *
     * @return ServletPath
     */
    private String getServletPath() {
        ServletRequestAttributes sra = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return sra.getRequest().getServletPath();
    }

    /**
     * 获取当前操作的用户 ID
     *
     * @return 用户 ID
     */
    private String getCurrentUserId() {
        String userId = UserContext.getUserId();
        if (StrUtil.isBlank(userId)) {
            throw new ClientException("用户ID获取失败，请登录");
        }
        return userId;
    }

    /**
     * 计算方法参数的 MD5 值
     *
     * @param joinPoint 切点对象
     * @return 方法参数的 MD5 值
     */
    private String calcArgsMD5(ProceedingJoinPoint joinPoint) {
        String md5 = DigestUtil.md5Hex(JSON.toJSONBytes(joinPoint.getArgs()));
        // 计算参数的 MD5 值
        return md5;
    }

    /**
     * 幂等性处理逻辑
     *
     * @param wrapper 幂等性参数包装类
     */
    @Override
    public void handler(IdempotentParamWrapper wrapper) {
        String lockKey = wrapper.getLockKey();
        RLock lock = redissonClient.getLock(lockKey);
        if (!lock.tryLock()) {
            throw new ClientException(wrapper.getIdempotent().message());
        }
        // 将锁存入上下文中
        IdempotentContext.put(LOCK, lock);
    }

    /**
     * 后置处理逻辑，用于释放锁
     */
    @Override
    public void postProcessing() {
        RLock lock = null;
        try {
            lock = (RLock) IdempotentContext.getKey(LOCK);
        } finally {
            if (lock != null) {
                lock.unlock();
            }
        }
    }
}
