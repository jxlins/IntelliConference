package com.jxl.ai.intelliconf.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jxl.ai.intelliconf.dao.entity.UserDO;
import com.jxl.ai.intelliconf.dto.req.UserLoginReqDTO;
import com.jxl.ai.intelliconf.dto.req.UserRegisterReqDTO;
import com.jxl.ai.intelliconf.dto.req.UserUpdateReqDTO;
import com.jxl.ai.intelliconf.dto.resp.UserLoginRespDTO;
import com.jxl.ai.intelliconf.dto.resp.UserRespDTO;

/**
 * 用户接口层
 */
public interface UserService extends IService<UserDO> {

    /**
     * 根据用户名查询用户
     *
     * @param username 用户名
     * @return 用户
     */
    UserRespDTO getUserByUsername(String username);

    /**
     * 查询用户名是否存在
     *
     * @param username 用户名
     * @return 是否存在
     */
    Boolean hasUsername(String username);


    /**
     * 用户注册
     *
     * @param requestParam 注册请求参数
     */
    void register(UserRegisterReqDTO requestParam);

    /**
     * 更新用户
     *
     * @param requestParam 更新请求参数
     */
    void update(UserUpdateReqDTO requestParam);

    /**
     * 用户登录
     *
     * @param requestParam 登录请求参数
     * @return
     */
    UserLoginRespDTO login(UserLoginReqDTO requestParam);

    /**
     * 检查用户是否登录
     *
     * @param token
     * @param username
     * @return
     */
    boolean checkLogin(String token, String username);

    /**
     * 用户退出登录
     *
     * @param token
     * @param username
     */
    void logout(String token, String username);
}
