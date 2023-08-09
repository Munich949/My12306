package com.dlnu.index12306.biz.userservice.controller;

import com.dlnu.index12306.biz.userservice.dto.req.UserDeletionReqDTO;
import com.dlnu.index12306.biz.userservice.dto.req.UserRegisterReqDTO;
import com.dlnu.index12306.biz.userservice.dto.resp.UserRegisterRespDTO;
import com.dlnu.index12306.biz.userservice.service.UserLoginService;
import com.dlnu.index12306.biz.userservice.service.UserService;
import com.dlnu.index12306.framework.starter.convention.result.Result;
import com.dlnu.index12306.framework.starter.web.Results;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class UserInfoController {

    private final UserLoginService userLoginService;
    private final UserService userService;

    /**
     * 检查用户名是否已存在
     */
    @GetMapping("/api/user-service/has-username")
    public Result<Boolean> hasUsername(@RequestParam("username") @NotEmpty String username) {
        return Results.success(userLoginService.hasUsername(username));
    }

    /**
     * 注册用户
     */
    @PostMapping("/api/user-service/register")
    public Result<UserRegisterRespDTO> register(@RequestBody @Valid UserRegisterReqDTO requestParam) {
        return Results.success(userLoginService.register(requestParam));
    }

    /**
     * 注销用户
     */
    @PostMapping("/api/user-service/deletion")
    public Result<Void> deletion(@RequestBody @Valid UserDeletionReqDTO requestParam) {
        userLoginService.deletion(requestParam);
        return Results.success();
    }
}
