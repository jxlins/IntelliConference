package com.jxl.ai.intelliconf.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jxl.ai.intelliconf.common.biz.user.UserContext;
import com.jxl.ai.intelliconf.common.convention.errorcode.BaseErrorCode;
import com.jxl.ai.intelliconf.common.convention.exception.ClientException;
import com.jxl.ai.intelliconf.common.convention.exception.ServiceException;
import com.jxl.ai.intelliconf.dao.entity.UserDO;
import com.jxl.ai.intelliconf.dao.mapper.UserMapper;
import com.jxl.ai.intelliconf.dto.req.UserLoginReqDTO;
import com.jxl.ai.intelliconf.dto.req.UserRegisterReqDTO;
import com.jxl.ai.intelliconf.dto.req.UserUpdateReqDTO;
import com.jxl.ai.intelliconf.dto.resp.UserLoginRespDTO;
import com.jxl.ai.intelliconf.dto.resp.UserRespDTO;
import com.jxl.ai.intelliconf.service.UserService;
import com.jxl.ai.intelliconf.toolkit.PasswordUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.jxl.ai.intelliconf.common.constant.RedisCacheConstant.LOCK_USER_REGISTER_KEY;
import static com.jxl.ai.intelliconf.common.constant.RedisCacheConstant.USER_LOGIN_KEY;

/**
 * 用户接口实现曾
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, UserDO> implements UserService {

    private final RBloomFilter userRegisterCachePenetrationBloomField;
    private final RedissonClient redissonClient;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public UserRespDTO getUserByUsername(String username) {
        // 根据 用户名 和 删除标记 查询用户
        UserDO userDO = getUserByUseranme(username);

        // 2.1. 若用户不存在，报错
        if (userDO == null) {
            throw new ClientException(BaseErrorCode.USER_IS_NOT_EXIST_ERROR);
        }

        // 2.2. 用户存在则返回用户记录
        UserRespDTO result = new UserRespDTO();
        BeanUtil.copyProperties(userDO, result);
        return result;
    }

    @Override
    public Boolean hasUsername(String username) {
        UserDO userDO = getUserByUseranme(username);
        return userDO == null ? false : true;
    }

    @Override
    public void register(UserRegisterReqDTO requestParam) {
        String username = requestParam.getUsername();
        // 检查用户名是否存在
        Boolean hasUsername = hasUsername(username);
        if (hasUsername) {
            // 存在，抛出“用户名已存在”异常
            throw new ClientException(BaseErrorCode.USER_NAME_EXIST_ERROR);
        }
        // 不存在，插入数据
        // 分布式锁：intelli-conf:lock:user_register:{username}
        RLock lock = redissonClient.getLock(LOCK_USER_REGISTER_KEY + username);
        if (!lock.tryLock()) {
            // 获取不到锁，说明在尝试获取锁时该用户名已被其他用户注册
            throw new ClientException(BaseErrorCode.USER_NAME_EXIST_ERROR);
        }
        int inserted = 0;
        try {
            // 插入数据
            // 密码加密处理
            requestParam.setPassword(PasswordUtil.encrypt(requestParam.getPassword()));
            inserted = baseMapper.insert(BeanUtil.toBean(requestParam, UserDO.class));
            // 若插入数据失败，返回“注册失败”错误
            if (inserted < 1) {
                throw new ServiceException("注册失败");
            }
        } catch (DuplicateKeyException ex) {
            throw new ClientException("用户已存在");
        } finally {
            // 释放锁
            lock.unlock();
        }
    }

    @Override
    public void update(UserUpdateReqDTO requestParam) {
        if (!Objects.equals(requestParam.getUsername(), UserContext.getUsername())) {
            throw new ClientException("当前登录用户修改请求异常");
        }
        LambdaQueryWrapper<UserDO> queryWrapper = Wrappers.lambdaQuery(UserDO.class)
                .eq(UserDO::getUsername, requestParam.getUsername());
        baseMapper.update(BeanUtil.toBean(requestParam, UserDO.class), queryWrapper);
    }

    @Override
    public UserLoginRespDTO login(UserLoginReqDTO requestParam) {
        // 查询用户
        UserDO userDO = getUserByUseranme(requestParam.getUsername());
        if (userDO == null) {
            throw new ClientException(BaseErrorCode.USER_IS_NOT_EXIST_ERROR);   // 用户不存在
        }

        // 验证密码
        if (!PasswordUtil.matches(requestParam.getPassword(), userDO.getPassword())) {
            throw new ClientException("密码错误");
        }
        Map<Object, Object> hasLoginMap = stringRedisTemplate.opsForHash().entries(USER_LOGIN_KEY + requestParam.getUsername());
        // 检查 redis 中是否有登录信息，若有则续期并返回 token
        if (CollUtil.isNotEmpty(hasLoginMap)) {
            String key = USER_LOGIN_KEY + requestParam.getUsername();
            stringRedisTemplate.expire(key, 30L, TimeUnit.MINUTES);
            String token = hasLoginMap.keySet().stream()
                    .findFirst()
                    .map(Object::toString)
                    .orElseThrow(() -> new ClientException("用户登录错误"));
            return new UserLoginRespDTO(token);
        }
        /**
         * Hash
         * Key: short-link:login:用户名
         * Value:
         *  Key: token 标识
         *  Val: JSON 字符串（用户信息）
         */
        String key = USER_LOGIN_KEY + requestParam.getUsername();
        String uuid = UUID.randomUUID().toString();
        stringRedisTemplate.opsForHash().put(key, uuid, JSON.toJSONString(userDO));
        stringRedisTemplate.expire(key, 30L, TimeUnit.MINUTES);
        return new UserLoginRespDTO(uuid);
    }

    @Override
    public boolean checkLogin(String token, String username) {
        String key = USER_LOGIN_KEY + username;
        return stringRedisTemplate.opsForHash().get(key, token) != null;
    }

    @Override
    public void logout(String token, String username) {
        String key = USER_LOGIN_KEY + username;
        if (checkLogin(token, username)) {
            stringRedisTemplate.delete(key);
            return;
        }
        throw new ClientException("用户Token不存在或未登录");
    }

    private UserDO getUserByUseranme(String username) {
        LambdaQueryWrapper<UserDO> queryWrapper = Wrappers.lambdaQuery(UserDO.class)
                .eq(UserDO::getUsername, username)
                .eq(UserDO::getDelFlag, 0);
        UserDO userDO = baseMapper.selectOne(queryWrapper);
        return userDO;
    }
}
