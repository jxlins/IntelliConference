package com.jxl.ai.intelliconf.common.constant;

/**
 * Redis 缓存常量类
 */
public class RedisCacheConstant {

    /**
     * 用户注册布隆过滤器标识
     */
    public static final String USER_REGISTER_BLOOM_FIELD = "user_register_bloom_field";

    /**
     * 用户注册分布式锁
     */
    public static final String LOCK_USER_REGISTER_KEY = "intelli-conf:lock:user_register:";

    /**
     * 用户登录缓存前缀
     */
    public static final String USER_LOGIN_KEY = "intelli-conf:login:";

    /**
     * Conference stage instance list cache.
     */
    public static final String CONFERENCE_STAGE_LIST_KEY = "intelli-conf:conference:stage:list:";

    /**
     * Conference generated task list cache.
     */
    public static final String CONFERENCE_TASK_LIST_KEY = "intelli-conf:conference:task:list:";
}
