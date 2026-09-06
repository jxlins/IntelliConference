package com.jxl.ai.intelliconf.toolkit;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

public class PasswordUtil {

    // 单例模式，避免重复创建对象
    private static final PasswordEncoder ENCODER = new BCryptPasswordEncoder();

    /**
     * 加密密码 (注册/修改密码时调用)
     *
     * @param rawPassword 明文密码
     * @return 密文 (以 $2a$10$ 开头)
     */
    public static String encrypt(String rawPassword) {
        if (rawPassword == null) return null;
        return ENCODER.encode(rawPassword);
    }

    /**
     * 验证密码 (登录时调用)
     *
     * @param rawPassword 用户输入的明文密码
     * @param encodedPassword 数据库中存储的密文
     * @return true: 匹配成功, false: 失败
     */
    public static boolean matches(String rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            return false;
        }
        return ENCODER.matches(rawPassword, encodedPassword);
    }
}