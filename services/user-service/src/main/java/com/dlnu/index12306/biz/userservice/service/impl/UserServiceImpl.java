package com.dlnu.index12306.biz.userservice.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dlnu.index12306.biz.userservice.dao.entity.UserDO;
import com.dlnu.index12306.biz.userservice.dao.entity.UserDeletionDO;
import com.dlnu.index12306.biz.userservice.dao.entity.UserMailDO;
import com.dlnu.index12306.biz.userservice.dao.mapper.UserDeletionMapper;
import com.dlnu.index12306.biz.userservice.dao.mapper.UserMailMapper;
import com.dlnu.index12306.biz.userservice.dao.mapper.UserMapper;
import com.dlnu.index12306.biz.userservice.dto.req.UserUpdateReqDTO;
import com.dlnu.index12306.biz.userservice.dto.resp.UserQueryActualRespDTO;
import com.dlnu.index12306.biz.userservice.dto.resp.UserQueryRespDTO;
import com.dlnu.index12306.biz.userservice.service.UserService;
import com.dlnu.index12306.framework.starter.cache.DistributedCache;
import com.dlnu.index12306.framework.starter.common.toolkit.BeanUtil;
import com.dlnu.index12306.framework.starter.convention.exception.ClientException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

import static com.dlnu.index12306.biz.userservice.common.constant.RedisKeyConstant.USER_DELETION_LIST;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserDeletionMapper userDeletionMapper;
    private final UserMapper userMapper;
    private final UserMailMapper userMailMapper;
    private final DistributedCache distributedCache;

    @Override
    public Integer queryUserDeletionNum(Integer idType, String idCard) {
        StringRedisTemplate instance = (StringRedisTemplate) distributedCache.getInstance();
        Long deletionCount = instance.opsForList()
                .size(String.format(USER_DELETION_LIST, idType, idCard));
        LambdaQueryWrapper<UserDeletionDO> queryWrapper = Wrappers.lambdaQuery(UserDeletionDO.class)
                .eq(UserDeletionDO::getIdType, idType)
                .eq(UserDeletionDO::getIdCard, idCard);
        deletionCount = Optional.ofNullable(deletionCount)
                .orElse(userDeletionMapper.selectCount(queryWrapper));
        return Optional.ofNullable(deletionCount).map(Long::intValue).orElse(0);
    }

    @Override
    public UserQueryRespDTO queryUserByUserId(String userId) {
        LambdaQueryWrapper<UserDO> queryWrapper = Wrappers.lambdaQuery(UserDO.class)
                .eq(UserDO::getIdType, userId);
        UserDO userDO = userMapper.selectOne(queryWrapper);
        if (userDO == null) {
            throw new ClientException("用户不存在，请检查用户ID是否正确");
        }
        return BeanUtil.convert(userDO, UserQueryRespDTO.class);
    }

    @Override
    public UserQueryRespDTO queryUserByUsername(String username) {
        LambdaQueryWrapper<UserDO> queryWrapper = Wrappers.lambdaQuery(UserDO.class)
                .eq(UserDO::getUsername, username);
        UserDO userDO = userMapper.selectOne(queryWrapper);
        if (userDO == null) {
            throw new ClientException("用户不存在，请检查用户名是否正确");
        }
        return BeanUtil.convert(userDO, UserQueryRespDTO.class);
    }

    @Override
    public UserQueryActualRespDTO queryActualUserByUsername(String username) {
        return BeanUtil.convert(queryUserByUsername(username), UserQueryActualRespDTO.class);
    }

    @Override
    public void update(UserUpdateReqDTO requestParam) {
        UserQueryRespDTO userQueryRespDTO = queryUserByUsername(requestParam.getUsername());
        UserDO userDO = BeanUtil.convert(requestParam, UserDO.class);
        LambdaUpdateWrapper<UserDO> userUpdateWrapper = Wrappers.lambdaUpdate(UserDO.class)
                .eq(UserDO::getUsername, requestParam.getUsername());
        userMapper.update(userDO, userUpdateWrapper);
        if (StrUtil.isNotBlank(requestParam.getMail()) && !Objects.equals(requestParam.getMail(), userQueryRespDTO.getMail())) {
            LambdaUpdateWrapper<UserMailDO> userMailUpdateWrapper = Wrappers.lambdaUpdate(UserMailDO.class)
                    .eq(UserMailDO::getMail, userQueryRespDTO.getMail());
            userMailMapper.delete(userMailUpdateWrapper);
            UserMailDO userMailDO = UserMailDO.builder()
                    .mail(requestParam.getMail())
                    .username(requestParam.getUsername())
                    .build();
            userMailMapper.insert(userMailDO);
        }
    }
}
