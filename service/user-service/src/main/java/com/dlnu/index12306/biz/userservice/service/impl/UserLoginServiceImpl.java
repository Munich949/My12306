package com.dlnu.index12306.biz.userservice.service.impl;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dlnu.index12306.biz.userservice.common.enums.UserChainMarkEnum;
import com.dlnu.index12306.biz.userservice.dao.entity.UserDO;
import com.dlnu.index12306.biz.userservice.dao.entity.UserMailDO;
import com.dlnu.index12306.biz.userservice.dao.entity.UserPhoneDO;
import com.dlnu.index12306.biz.userservice.dao.entity.UserReuseDO;
import com.dlnu.index12306.biz.userservice.dao.mapper.UserMailMapper;
import com.dlnu.index12306.biz.userservice.dao.mapper.UserMapper;
import com.dlnu.index12306.biz.userservice.dao.mapper.UserPhoneMapper;
import com.dlnu.index12306.biz.userservice.dao.mapper.UserReuseMapper;
import com.dlnu.index12306.biz.userservice.dto.req.UserLoginReqDTO;
import com.dlnu.index12306.biz.userservice.dto.req.UserRegisterReqDTO;
import com.dlnu.index12306.biz.userservice.dto.resp.UserLoginRespDTO;
import com.dlnu.index12306.biz.userservice.dto.resp.UserRegisterRespDTO;
import com.dlnu.index12306.biz.userservice.service.UserLoginService;
import com.dlnu.index12306.framework.starter.cache.DistributedCache;
import com.dlnu.index12306.framework.starter.common.toolkit.BeanUtil;
import com.dlnu.index12306.framework.starter.convention.exception.ClientException;
import com.dlnu.index12306.framework.starter.convention.exception.ServiceException;
import com.dlnu.index12306.framework.starter.designpattern.chain.AbstractChainContext;
import com.dlnu.index12306.framework.starter.user.core.UserInfoDTO;
import com.dlnu.index12306.framework.starter.user.toolkit.JWTUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.dlnu.index12306.biz.userservice.common.constant.RedisKeyConstant.USER_REGISTER_REUSE_SHARDING;
import static com.dlnu.index12306.biz.userservice.common.enums.UserRegisterErrorCodeEnum.*;
import static com.dlnu.index12306.biz.userservice.toolkit.UserReuseUtil.hashShardingIdx;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserLoginServiceImpl implements UserLoginService {

    private final UserMapper userMapper;
    private final UserPhoneMapper userPhoneMapper;
    private final UserMailMapper userMailMapper;
    private final UserReuseMapper userReuseMapper;
    private final DistributedCache distributedCache;
    private final AbstractChainContext<UserRegisterReqDTO> abstractChainContext;
    private final RBloomFilter<String> userRegisterCachePenetrationBloomFilter;

    @Override
    public UserLoginRespDTO login(UserLoginReqDTO requestParam) {
        String usernameOrMailOrPhone = requestParam.getUsernameOrMailOrPhone();
        boolean mailFlag = false;
        // 判断是不是用邮箱登录的
        // 时间复杂度最佳 O(1)。indexOf or contains 时间复杂度为 O(n)
        for (char c : usernameOrMailOrPhone.toCharArray()) {
            if (c == '@') {
                mailFlag = true;
                break;
            }
        }
        String username;
        // 如果是邮箱，查询用户邮箱表
        if (mailFlag) {
            LambdaQueryWrapper<UserMailDO> queryWrapper = Wrappers.lambdaQuery(UserMailDO.class)
                    .eq(UserMailDO::getMail, usernameOrMailOrPhone);
            username = Optional.ofNullable(userMailMapper.selectOne(queryWrapper))
                    .map(UserMailDO::getUsername)
                    .orElseThrow(() -> new ClientException("用户名/手机号/邮箱不存在"));
        } else {
            // 查询用户手机号表
            LambdaQueryWrapper<UserPhoneDO> queryWrapper = Wrappers.lambdaQuery(UserPhoneDO.class)
                    .eq(UserPhoneDO::getPhone, usernameOrMailOrPhone);
            username = Optional.ofNullable(userPhoneMapper.selectOne(queryWrapper))
                    .map(UserPhoneDO::getUsername)
                    .orElse(null);
        }
        // 如果都不是，即用户输入的是用户名，查询用户表
        username = Optional.ofNullable(username).orElse(requestParam.getUsernameOrMailOrPhone());
        LambdaQueryWrapper<UserDO> queryWrapper = Wrappers.lambdaQuery(UserDO.class)
                .eq(UserDO::getUsername, username)
                .eq(UserDO::getPassword, requestParam.getPassword())
                .select(UserDO::getId, UserDO::getUsername, UserDO::getRealName);
        UserDO userDO = userMapper.selectOne(queryWrapper);
        if (userDO != null) {
            UserInfoDTO userInfoDTO = UserInfoDTO.builder()
                    .userId(String.valueOf(userDO.getId()))
                    .username(userDO.getUsername())
                    .realName(userDO.getRealName())
                    .build();
            String accessToken = JWTUtil.generateAccessToken(userInfoDTO);
            UserLoginRespDTO actual = new UserLoginRespDTO(userInfoDTO.getUserId(),
                    requestParam.getUsernameOrMailOrPhone(),
                    userDO.getRealName(),
                    accessToken);
            distributedCache.put(accessToken, JSON.toJSONString(actual), 30, TimeUnit.MINUTES);
            return actual;
        }
        throw new ServiceException("账号不存在获密码错误");
    }

    @Override
    public UserLoginRespDTO checkLogin(String accessToken) {
        return distributedCache.get(accessToken, UserLoginRespDTO.class);
    }

    @Override
    public void logout(String accessToken) {
        if (StrUtil.isNotBlank(accessToken)) {
            distributedCache.delete(accessToken);
        }
    }

    @Override
    public Boolean hasUsername(String username) {
        // 先在布隆过滤器中判断该用户名是否存在 如果布隆过滤器中不存在那一定不存在 直接返回true 表示可注册
        boolean hasUsername = userRegisterCachePenetrationBloomFilter.contains(username);
        if (hasUsername) {
            // 如果布隆过滤器中存在 在Redis中进一步判断用户名是否存在
            StringRedisTemplate instance = (StringRedisTemplate) distributedCache.getInstance();
            return instance.opsForSet().isMember(USER_REGISTER_REUSE_SHARDING + hashShardingIdx(username), username);
        }
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public UserRegisterRespDTO register(UserRegisterReqDTO requestParam) {
        abstractChainContext.handler(UserChainMarkEnum.USER_REGISTER_FILTER.name(), requestParam);
        try {
            // 插入用户信息表
            int inserted = userMapper.insert(BeanUtil.convert(requestParam, UserDO.class));
            if (inserted < 1) {
                throw new ServiceException(USER_REGISTER_FAIL);
            }
        } catch (DuplicateKeyException dke) {
            log.error("用户名 [{}] 重复注册", requestParam.getUsername());
            throw new ServiceException(HAS_USERNAME_NOTNULL);
        }
        UserPhoneDO userPhoneDO = UserPhoneDO.builder()
                .phone(requestParam.getPhone())
                .username(requestParam.getUsername())
                .build();
        try {
            // 插入用户手机信息表
            userPhoneMapper.insert(userPhoneDO);
        } catch (DuplicateKeyException dke) {
            log.error("用户 [{}] 注册手机号 [{}] 重复", requestParam.getUsername(), requestParam.getPhone());
            throw new ServiceException(PHONE_REGISTERED);
        }
        if (StrUtil.isNotBlank(requestParam.getMail())) {
            UserMailDO userMailDO = UserMailDO.builder()
                    .mail(requestParam.getMail())
                    .username(requestParam.getUsername())
                    .build();
            try {
                // 插入用户邮箱信息表
                userMailMapper.insert(userMailDO);
            } catch (DuplicateKeyException dke) {
                log.error("用户 [{}] 注册邮箱 [{}] 重复", requestParam.getUsername(), requestParam.getMail());
                throw new ServiceException(MAIL_REGISTERED);
            }
        }
        String username = requestParam.getUsername();
        // 删除复用表中的数据
        userReuseMapper.delete(Wrappers.update(new UserReuseDO(username)));
        StringRedisTemplate instance = (StringRedisTemplate) distributedCache.getInstance();
        // 删除Redis中的复用数据
        instance.opsForSet().remove(USER_REGISTER_REUSE_SHARDING + hasUsername(username), username);
        // 将已注册的用户名加入布隆过滤器
        userRegisterCachePenetrationBloomFilter.add(username);
        return BeanUtil.convert(requestParam, UserRegisterRespDTO.class);
    }
}
