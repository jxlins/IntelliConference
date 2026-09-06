package com.jxl.ai.intelliconf.mail.infrastructure;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSON;
import com.jxl.ai.intelliconf.config.DeepSeekProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * DeepSeek Chat Completion 客户端（兼容 OpenAI 接口格式）。
 *
 * <p>仅用于邮件草稿自动生成场景：在 TaskMailAutomationService.createPlanIfMissing
 * 首次创建草稿、且该会议该场景未配置数据库模板时调用一次，组织者审核前仍可修改。
 * 调用失败或未配置 Key 时返回 null，由上层降级到硬编码兜底文案。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeepSeekClient {

    private final DeepSeekProperties properties;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * 生成会议邮件主题与正文。
     *
     * @param scene     场景描述（如「评审通知」「注册缴费提醒」）
     * @param variables 会议变量（conferenceName / website / 各截止日期等）
     * @return [0]=主题, [1]=正文；调用不可用时返回 null
     */
    public String[] generateMailContent(String scene, Map<String, Object> variables) {
        if (!properties.isEnabled() || !StringUtils.hasText(properties.getApiKey())) {
            return null;
        }
        try {
            String userPrompt = buildUserPrompt(scene, variables);
            JSONObject payload = new JSONObject();
            payload.put("model", properties.getModel());
            payload.put("stream", false);
            payload.put("max_tokens", properties.getMaxTokens());
            payload.put("temperature", properties.getTemperature());

            JSONArray messages = new JSONArray();
            JSONObject systemMsg = new JSONObject();
            systemMsg.put("role", "system");
            systemMsg.put("content", "你是一位国际学术会议的会务邮件撰写助手。"
                    + "请根据给定的会议信息和邮件场景，撰写正式、得体、信息完整的中文邮件。"
                    + "邮件正文使用纯文本（用换行分段，不要使用 HTML 标签或 Markdown 符号），"
                    + "包含称呼、正文要点、会议关键信息、联系方式提示和落款。"
                    + "严格以 JSON 格式返回：{\"subject\":\"邮件主题\",\"body\":\"邮件正文\"}，不要输出任何额外说明。");
            messages.add(systemMsg);

            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", userPrompt);
            messages.add(userMsg);

            payload.put("messages", messages);

            HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getBaseUrl() + "/v1/chat/completions"))
                    .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("DeepSeek API non-200, status={}, body={}", response.statusCode(),
                        StringUtils.hasText(response.body()) ? response.body().substring(0, Math.min(300, response.body().length())) : "");
                return null;
            }
            return parseContent(response.body());
        } catch (Exception ex) {
            log.warn("DeepSeek generate mail content failed, scene={}, error={}", scene, ex.getMessage());
            return null;
        }
    }

    private String buildUserPrompt(String scene, Map<String, Object> variables) {
        StringBuilder sb = new StringBuilder();
        sb.append("邮件场景：").append(scene).append("\n\n");
        sb.append("会议信息：\n");
        putIfPresent(sb, "会议名称", variables.get("conferenceName"));
        putIfPresent(sb, "会议简称", variables.get("conferenceShortName"));
        putIfPresent(sb, "会议官网", variables.get("conferenceWebsite"));
        putIfPresent(sb, "投稿截止", variables.get("paperSubmissionDeadline"));
        putIfPresent(sb, "录用通知", variables.get("notificationOfAcceptance"));
        putIfPresent(sb, "终稿提交", variables.get("cameraReadySubmission"));
        putIfPresent(sb, "早鸟注册", variables.get("earlyBirdRegistration"));
        putIfPresent(sb, "会议开始", variables.get("conferenceStartDate"));
        putIfPresent(sb, "会议结束", variables.get("conferenceEndDate"));
        putIfPresent(sb, "收件人角色", variables.get("targetRole"));
        sb.append("\n请基于以上信息撰写该场景的邮件，确保关键日期与会议名称准确，措辞正式得体。");
        return sb.toString();
    }

    private void putIfPresent(StringBuilder sb, String label, Object value) {
        if (value != null && StringUtils.hasText(String.valueOf(value))) {
            sb.append("- ").append(label).append("：").append(value).append("\n");
        }
    }

    /** 从 chat completion 响应解析出主题与正文 */
    private String[] parseContent(String body) {
        if (!StringUtils.hasText(body)) return null;
        try {
            JSONObject root = JSON.parseObject(body);
            JSONArray choices = root.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) return null;
            String content = choices.getJSONObject(0).getJSONObject("message").getString("content");
            if (!StringUtils.hasText(content)) return null;
            // 模型可能输出 markdown 代码块包裹的 JSON，剔除 ```json 前缀
            String trimmed = content.trim();
            if (trimmed.startsWith("```")) {
                int firstNewline = trimmed.indexOf('\n');
                if (firstNewline > 0) trimmed = trimmed.substring(firstNewline + 1);
                if (trimmed.endsWith("```")) trimmed = trimmed.substring(0, trimmed.length() - 3);
                trimmed = trimmed.trim();
            }
            JSONObject parsed = JSON.parseObject(trimmed);
            String subject = parsed.getString("subject");
            String mailBody = parsed.getString("body");
            if (!StringUtils.hasText(subject) || !StringUtils.hasText(mailBody)) {
                log.warn("DeepSeek returned content missing subject/body, raw={}", trimmed.substring(0, Math.min(200, trimmed.length())));
                return null;
            }
            return new String[]{subject.trim(), mailBody.trim()};
        } catch (Exception ex) {
            log.warn("Parse DeepSeek response failed: {}", ex.getMessage());
            return null;
        }
    }
}
