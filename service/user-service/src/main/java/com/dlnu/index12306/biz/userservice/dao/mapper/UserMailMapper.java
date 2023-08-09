package com.dlnu.index12306.biz.userservice.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dlnu.index12306.biz.userservice.dao.entity.UserMailDO;

/**
 * 用户邮箱表持久层
 */
public interface UserMailMapper extends BaseMapper<UserMailDO> {
    void deletionUser(UserMailDO userMailDO);
}
