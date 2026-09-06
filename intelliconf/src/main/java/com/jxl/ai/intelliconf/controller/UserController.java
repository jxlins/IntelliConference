package com.jxl.ai.intelliconf.controller;

import com.jxl.ai.intelliconf.common.convention.result.Result;
import com.jxl.ai.intelliconf.common.convention.result.Results;
import com.jxl.ai.intelliconf.dto.req.UserLoginReqDTO;
import com.jxl.ai.intelliconf.dto.req.UserRegisterReqDTO;
import com.jxl.ai.intelliconf.dto.req.UserUpdateReqDTO;
import com.jxl.ai.intelliconf.dto.resp.UserLoginRespDTO;
import com.jxl.ai.intelliconf.dto.resp.UserRespDTO;
import com.jxl.ai.intelliconf.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户管理控制层
 */
@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 根据用户名查询用户
     */
    @GetMapping("/api/intelli-conf/v1/user/{username}")
    public Result<UserRespDTO> getUserByUsername(@PathVariable("username") String username) {
        UserRespDTO userRespDTO = userService.getUserByUsername(username);
        return Results.success(userRespDTO);
    }

    /**
     * 查询用户名是否存在
     */
    @GetMapping("/api/intelli-conf/v1/user/has-username")
    public Result<Boolean> hasUsername(@RequestParam("username") String username) {
        return Results.success(userService.hasUsername(username));
    }

    /**
     * 注册用户
     */
    @PostMapping("/api/intelli-conf/v1/user")
    public Result<Void> register(@RequestBody UserRegisterReqDTO requestParam) {
        userService.register(requestParam);
        return Results.success();
    }

    /**
     * 更新用户
     */
    @PutMapping("/api/intelli-conf/v1/user")
    public Result<Void> update(@RequestBody UserUpdateReqDTO userUpdateReqDTO) {
        userService.update(userUpdateReqDTO);
        return Results.success();
    }

    /**
     * 用户登录
     */
    @PostMapping("/api/intelli-conf/v1/user/login")
    public Result<UserLoginRespDTO> login(@RequestBody UserLoginReqDTO userLoginReqDTO) {
        UserLoginRespDTO result = userService.login(userLoginReqDTO);
        return Results.success(result);
    }

    /**
     * 检查用户是否登录
     */
    @GetMapping("/api/intelli-conf/v1/user/check-login")
    public Result<Boolean> checkLogin(@RequestParam("token") String token,
                                      @RequestParam("username") String username) {
        boolean result = userService.checkLogin(token, username);
        return Results.success(result);
    }

    /**
     * 用户退出登录
     */
    @DeleteMapping("/api/intelli-conf/v1/user/logout")
    public Result<Void> logout(@RequestParam("token") String token,
                               @RequestParam("username") String username) {
        userService.logout(token, username);
        return Results.success();
    }
}
