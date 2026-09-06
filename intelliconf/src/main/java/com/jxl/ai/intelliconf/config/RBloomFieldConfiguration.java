package com.jxl.ai.intelliconf.config;

import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.jxl.ai.intelliconf.common.constant.RedisCacheConstant.USER_REGISTER_BLOOM_FIELD;

/**
 * 布隆过滤器配置
 */
@Configuration
public class RBloomFieldConfiguration {

    @Bean
    public RBloomFilter<String> userRegisterCachePenetrationBloomField(RedissonClient redissonClient) {
        RBloomFilter<String> cachePenetrationBloomField = redissonClient.getBloomFilter(USER_REGISTER_BLOOM_FIELD);
        cachePenetrationBloomField.tryInit(1000000L, 0);    // 初始化布隆过滤器
        return cachePenetrationBloomField;
    }
}
