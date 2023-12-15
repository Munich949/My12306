package com.dlnu.index12306.biz.userservice.service.handler.filter.user;

import com.dlnu.index12306.biz.userservice.common.enums.UserRegisterErrorCodeEnum;
import com.dlnu.index12306.biz.userservice.dto.req.UserRegisterReqDTO;
import com.dlnu.index12306.biz.userservice.service.UserLoginService;
import com.dlnu.index12306.framework.starter.convention.exception.ClientException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 用户注册用户名唯一校验
 */
@Component
@RequiredArgsConstructor
public class UserRegisterHasUsernameChainFilterHandler implements UserRegisterCreateChainFilter<UserRegisterReqDTO> {

    private final UserLoginService userLoginService;

    @Override
    public void handler(UserRegisterReqDTO requestParam) {
        if (!userLoginService.hasUsername(requestParam.getUsername())) {
            throw new ClientException(UserRegisterErrorCodeEnum.HAS_USERNAME_NOTNULL);
        }
    }

    @Override
    public int getOrder() {
        return 1;
    }
}
