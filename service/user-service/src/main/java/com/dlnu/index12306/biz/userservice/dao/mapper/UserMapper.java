package com.dlnu.index12306.biz.userservice.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dlnu.index12306.biz.userservice.dao.entity.UserDO;

/**
 * 用户信息持久层
 */
public interface UserMapper extends BaseMapper<UserDO> {
    void deletionUser(UserDO userDO);
}
