package com.jxl.ai.intelliconf.toolkit;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

/**
 * 用户敏感信息加密工具类
 */
public class EncryptionUtil {

    // 手机号密钥 (建议从配置中心读取)
    private static final String PHONE_KEY = "PhoneKey12345678"; // 必须16/24/32位
    // 邮箱密钥 (建议不同字段使用不同密钥，增加安全性)
    private static final String EMAIL_KEY = "EmailKey12345678";

    /**
     * 加密
     */
    public static String encrypt(String data, String key) {
        if (data == null || data.isEmpty()) return data;
        try {
            SecretKeySpec skeySpec = new SecretKeySpec(key.getBytes(), "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, skeySpec);
            byte[] encrypted = cipher.doFinal(data.getBytes());
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    /**
     * 解密
     */
    public static String decrypt(String data, String key) {
        if (data == null || data.isEmpty()) return data;
        try {
            SecretKeySpec skeySpec = new SecretKeySpec(key.getBytes(), "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, skeySpec);
            byte[] decoded = Base64.getDecoder().decode(data);
            byte[] decrypted = cipher.doFinal(decoded);
            return new String(decrypted);
        } catch (Exception e) {
            // 容错处理：如果解密失败（可能是旧数据未加密），可返回原文或记录日志
            // 生产环境建议根据业务需求决定是抛异常还是返回原文
            return data;
        }
    }

    // 为了方便 Handler 调用，提供特定方法
    public static String encryptPhone(String data) { return encrypt(data, PHONE_KEY); }
    public static String decryptPhone(String data) { return decrypt(data, PHONE_KEY); }

    public static String encryptEmail(String data) { return encrypt(data, EMAIL_KEY); }
    public static String decryptEmail(String data) { return decrypt(data, EMAIL_KEY); }
}