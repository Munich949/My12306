package com.dlnu.index12306.biz.userservice.service.handler.filter.user;

import com.dlnu.index12306.biz.userservice.common.enums.UserChainMarkEnum;
import com.dlnu.index12306.biz.userservice.dto.req.UserRegisterReqDTO;
import com.dlnu.index12306.framework.starter.designpattern.chain.AbstractChainHandler;

public interface UserRegisterCreateChainFilter extends AbstractChainHandler<UserRegisterReqDTO> {
    @Override
    default String mark() {
        return UserChainMarkEnum.USER_REGISTER_FILTER.name();
    }
}
