package com.dlnu.index12306.biz.userservice.service;

import jakarta.validation.constraints.NotEmpty;

/**
 * 用户信息接口层
 */
public interface UserService {

    /**
     * 根据证件类型和证件号查询注销次数
     *
     * @param idType 证件类型
     * @param idCard 证件号
     * @return 注销次数
     */
    Integer queryUserDeletionNum(Integer idType, String idCard);
}
