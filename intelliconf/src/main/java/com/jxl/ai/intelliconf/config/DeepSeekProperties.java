package com.jxl.ai.intelliconf.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * DeepSeek API 配置（用于自动邮件内容生成）。
 *
 * <p>接入逻辑：当某个 EMAIL 任务临近计划发送时间（7 天窗口）自动创建邮件草稿时，
 * 若该会议该场景未配置数据库模板，则调用 DeepSeek 生成主题与正文；未配置 Key 或调用失败时
 * 自动降级到内置硬编码文案，保证邮件生成链路不会因 AI 不可用而中断。
 */
@Data
@Component
@ConfigurationProperties(prefix = "deepseek")
public class DeepSeekProperties {

    /** 是否启用 DeepSeek 生成邮件内容；未配置 api-key 时自动降级 */
    private boolean enabled = true;

    /** DeepSeek API Key（在 https://platform.deepseek.com 申请） */
    private String apiKey = "";

    /** API 基础地址，默认官方地址；兼容 OpenAI 格式 */
    private String baseUrl = "https://api.deepseek.com";

    /** 模型名，默认 deepseek-chat（通用对话模型，性价比高） */
    private String model = "deepseek-chat";

    /** 请求超时（秒），DeepSeek 生成通常 5~15 秒 */
    private int timeoutSeconds = 30;

    /** 生成最大 token 数，邮件正文一般不超过 1024 */
    private int maxTokens = 1024;

    /** 采样温度，0~2；邮件场景偏稳定正式，建议 0.7 */
    private double temperature = 0.7;
}
