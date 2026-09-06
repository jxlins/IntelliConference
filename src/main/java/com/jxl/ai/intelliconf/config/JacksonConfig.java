package com.jxl.ai.intelliconf.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * Jackson 配置类
 * 解决前端 JavaScript 大整数精度丢失问题
 * 将所有 Long 类型序列化为字符串
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper jacksonObjectMapper(Jackson2ObjectMapperBuilder builder) {
        ObjectMapper objectMapper = builder.createXmlMapper(false).build();
        
        // 创建一个模块，将 Long 和 long 类型序列化为字符串
        SimpleModule simpleModule = new SimpleModule();
        simpleModule.addSerializer(Long.class, ToStringSerializer.instance);
        simpleModule.addSerializer(Long.TYPE, ToStringSerializer.instance);
        
        objectMapper.registerModule(simpleModule);
        
        return objectMapper;
    }
}
