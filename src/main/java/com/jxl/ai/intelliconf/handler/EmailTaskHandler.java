package com.jxl.ai.intelliconf.handler;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.jxl.ai.intelliconf.common.convention.exception.ServiceException;
import com.jxl.ai.intelliconf.dao.entity.ConfEmailContentDO;
import com.jxl.ai.intelliconf.dao.entity.SysTaskLogDO;
import com.jxl.ai.intelliconf.dao.mapper.SysTaskLogMapper;
import com.jxl.ai.intelliconf.enums.EmailType;
import com.jxl.ai.intelliconf.service.ConfEmailContentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Date;

/**
 * 邮件群发任务处理器
 * <p>
 * Bean 名称固定为 "emailTaskHandler"，与 conf_task_def.handler_bean 字段对应。
 * params JSON 格式示例：
 * <pre>
 * {
 *   "email_type": "CFP_REMINDER",
 *   "subject":    "【ICSE2026】投稿征文通知",
 *   "content":    "<p>尊敬的 ${name}，诚邀您投稿...</p>"
 * }
 * </pre>
 * 主执行日志（sys_task_log 状态 1→2/3）由 TaskDispatcher 统一管理，
 * 本 Handler 通过 TaskLogService.appendRemark 追加发送结果摘要。
 */
@Slf4j
@Component("emailTaskHandler")
@RequiredArgsConstructor
public class EmailTaskHandler implements TaskHandler {

    private final ConfEmailContentService confEmailContentService;
    private final SysTaskLogMapper sysTaskLogMapper;

    @Override
    public void execute(Long confId, Long taskLogId, String params) {
        // 任务到达时仅初始化邮件配置，真正发送由定时调度器执行。
        JSONObject config;
        try {
            config = JSON.parseObject(params);
            if (config == null) {
                config = new JSONObject();
            }
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            config = new JSONObject();
            log.warn("[EmailTaskHandler] params 解析失败，使用空配置初始化草稿, taskLogId={}, rawParams={}", taskLogId, params);
        }

        String emailTypeCode = resolveEmailTypeCode(config);
        String targetRole = resolveTargetRole(config, emailTypeCode);
        ConfEmailContentDO exists = confEmailContentService.getById(taskLogId);
        if (exists == null) {
            confEmailContentService.save(ConfEmailContentDO.builder()
                    .taskLogId(taskLogId)
                    .confId(confId)
                    .targetRole(targetRole)
                    .contentStatus(0)
                    .sendTime(new Date())
                    .build());
            markTaskWaitingForContent(taskLogId, "待配置邮件内容：请先填写邮件主题和正文");
            log.info("[EmailTaskHandler] 已初始化邮件配置草稿, taskLogId={}, confId={}, targetRole={}",
                    taskLogId, confId, targetRole);
            return;
        }

        exists.setTargetRole(targetRole);
        if (exists.getSendTime() == null) {
            exists.setSendTime(new Date());
        }
        confEmailContentService.updateById(exists);
        if (exists.getContentStatus() == null || exists.getContentStatus() == 0) {
            markTaskWaitingForContent(taskLogId, "待配置邮件内容：请先填写邮件主题和正文");
        }
        log.info("[EmailTaskHandler] 邮件配置已存在，更新目标角色完成, taskLogId={}, confId={}, targetRole={}",
                taskLogId, confId, targetRole);
    }

    private void markTaskWaitingForContent(Long taskLogId, String message) {
        sysTaskLogMapper.updateById(SysTaskLogDO.builder()
                .id(taskLogId)
                .executionStatus(1)
                .errorMsg(message)
                .build());
    }

    private String resolveEmailTypeCode(JSONObject config) {
        if (config == null) {
            return null;
        }
        JSONObject emailConfig = config.getJSONObject("email");
        String emailTypeCode = emailConfig == null ? null : emailConfig.getString("email_type");
        if (!StringUtils.hasText(emailTypeCode)) {
            emailTypeCode = config.getString("email_type");
        }
        if (!StringUtils.hasText(emailTypeCode)) {
            String legacyType = emailConfig == null ? null : emailConfig.getString("type");
            if (!StringUtils.hasText(legacyType)) {
                legacyType = config.getString("type");
            }
            emailTypeCode = mapLegacyType(legacyType);
        }
        return emailTypeCode;
    }

    private String resolveTargetRole(JSONObject config, String emailTypeCode) {
        if (config != null) {
            JSONObject emailConfig = config.getJSONObject("email");
            String explicit = emailConfig == null ? null : emailConfig.getString("targetRole");
            if (!StringUtils.hasText(explicit)) {
                explicit = config.getString("targetRole");
            }
            if (StringUtils.hasText(explicit)) {
                return explicit;
            }
        }
        return parseTargetRole(emailTypeCode);
    }

    private String mapLegacyType(String legacyType) {
        if (!StringUtils.hasText(legacyType)) {
            return null;
        }
        if ("CFP".equalsIgnoreCase(legacyType)) {
            return EmailType.CFP_REMINDER.name();
        }
        if ("SUBMISSION".equalsIgnoreCase(legacyType)) {
            return EmailType.SUBMISSION_CONFIRM.name();
        }
        if ("REVIEW".equalsIgnoreCase(legacyType)) {
            return EmailType.REVIEW_INVITE.name();
        }
        if ("RESULT".equalsIgnoreCase(legacyType)) {
            return EmailType.RESULT_ANNOUNCE.name();
        }
        return legacyType;
    }

    private String parseTargetRole(String emailTypeCode) {
        if (!StringUtils.hasText(emailTypeCode)) {
            return null;
        }
        try {
            return EmailType.valueOf(emailTypeCode).getDefaultRole();
        } catch (IllegalArgumentException ex) {
            log.warn("[EmailTaskHandler] 未识别的邮件类型，目标角色将由用户配置时补全, emailTypeCode={}", emailTypeCode);
            return null;
        }
    }
}

