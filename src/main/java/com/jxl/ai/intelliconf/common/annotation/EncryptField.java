package com.jxl.ai.intelliconf.common.annotation;

import java.lang.annotation.*;

/**
 * 标记敏感字段需要加密
 * 注意：实际加密逻辑由对应的 TypeHandler 执行，此注解主要起标识和文档作用
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface EncryptField {
    /**
     * 业务类型标识 (可选，用于日志或区分策略)
     */
    String type() default "COMMON";
}