package com.jxl.ai.intelliconf.controller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jxl.ai.intelliconf.common.constant.TaskConstants;
import com.jxl.ai.intelliconf.common.convention.exception.ClientException;
import com.jxl.ai.intelliconf.common.convention.result.Result;
import com.jxl.ai.intelliconf.common.convention.result.Results;
import com.jxl.ai.intelliconf.dao.entity.ConfEmailContentDO;
import com.jxl.ai.intelliconf.dao.entity.ConfTaskDefDO;
import com.jxl.ai.intelliconf.dao.entity.SysTaskLogDO;
import com.jxl.ai.intelliconf.dao.mapper.ConfTaskDefMapper;
import com.jxl.ai.intelliconf.dao.mapper.SysTaskLogMapper;
import com.jxl.ai.intelliconf.dto.req.EmailContentSaveReqDTO;
import com.jxl.ai.intelliconf.dto.resp.EmailContentRespDTO;
import com.jxl.ai.intelliconf.enums.ConferenceRole;
import com.jxl.ai.intelliconf.enums.EmailType;
import com.jxl.ai.intelliconf.service.ConferencePermissionService;
import com.jxl.ai.intelliconf.service.ConfEmailContentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

import java.util.Date;

@RestController
@RequiredArgsConstructor
public class EmailContentController {

    private final SysTaskLogMapper sysTaskLogMapper;
    private final ConfTaskDefMapper confTaskDefMapper;
    private final ConferencePermissionService conferencePermissionService;
    private final ConfEmailContentService confEmailContentService;

    @GetMapping("/api/task/email-content/{taskLogId}")
    public Result<EmailContentRespDTO> getEmailContent(@PathVariable Long taskLogId) {
        SysTaskLogDO taskLog = sysTaskLogMapper.selectById(taskLogId);
        validateEmailTask(taskLog);
        conferencePermissionService.requireAtLeastRole(taskLog.getConfereId(), ConferenceRole.OPERATOR);

        ConfTaskDefDO taskDef = confTaskDefMapper.selectById(taskLog.getTaskDefId());
        String targetRole = parseTargetRole(taskDef == null ? null : taskDef.getParams());

        ConfEmailContentDO contentDO = confEmailContentService.getById(taskLogId);
        if (contentDO == null) {
            return Results.success(EmailContentRespDTO.builder()
                    .taskLogId(taskLogId)
                    .confId(taskLog.getConfereId())
                    .targetRole(targetRole)
                    .contentStatus(0)
                    .build());
        }

        return Results.success(EmailContentRespDTO.builder()
                .taskLogId(contentDO.getTaskLogId())
                .confId(contentDO.getConfId())
                .subject(contentDO.getSubject())
                .contentBody(contentDO.getContentBody())
                .targetRole(contentDO.getTargetRole())
                .sendTime(contentDO.getSendTime())
                .contentStatus(contentDO.getContentStatus())
                .build());
    }

    @PostMapping("/api/task/email-content/save")
    public Result<Void> saveEmailContent(@RequestBody EmailContentSaveReqDTO reqDTO) {
        if (reqDTO == null || reqDTO.getTaskLogId() == null) {
            throw new ClientException("taskLogId 不能为空");
        }
        if (!StringUtils.hasText(reqDTO.getSubject())) {
            throw new ClientException("邮件主题不能为空");
        }
        if (!StringUtils.hasText(reqDTO.getContentBody())) {
            throw new ClientException("邮件正文不能为空");
        }

        SysTaskLogDO taskLog = sysTaskLogMapper.selectById(reqDTO.getTaskLogId());
        validateEmailTask(taskLog);
        conferencePermissionService.requireAtLeastRole(taskLog.getConfereId(), ConferenceRole.OPERATOR);

        ConfTaskDefDO taskDef = confTaskDefMapper.selectById(taskLog.getTaskDefId());
        String targetRole = parseTargetRole(taskDef == null ? null : taskDef.getParams());

        ConfEmailContentDO contentDO = ConfEmailContentDO.builder()
                .taskLogId(taskLog.getId())
                .confId(taskLog.getConfereId())
                .subject(reqDTO.getSubject())
                .contentBody(reqDTO.getContentBody())
                .targetRole(targetRole)
                .sendTime(reqDTO.getSendTime() == null ? new Date() : reqDTO.getSendTime())
                .contentStatus(1)
                .build();

        confEmailContentService.saveOrUpdate(contentDO);
        return Results.success();
    }

    @PostMapping("/api/task/email-content/retry/{taskLogId}")
    public Result<Void> retryEmailTask(@PathVariable Long taskLogId) {
        SysTaskLogDO taskLog = sysTaskLogMapper.selectById(taskLogId);
        validateEmailTask(taskLog);
        conferencePermissionService.requireAtLeastRole(taskLog.getConfereId(), ConferenceRole.OPERATOR);

        ConfEmailContentDO contentDO = confEmailContentService.getById(taskLogId);
        if (contentDO == null) {
            throw new ClientException("未找到邮件配置，请先填写邮件主题和正文");
        }
        if (!StringUtils.hasText(contentDO.getSubject()) || !StringUtils.hasText(contentDO.getContentBody())) {
            throw new ClientException("邮件主题或正文为空，请完善后再重试");
        }

        if (!StringUtils.hasText(contentDO.getTargetRole())) {
            ConfTaskDefDO taskDef = confTaskDefMapper.selectById(taskLog.getTaskDefId());
            contentDO.setTargetRole(parseTargetRole(taskDef == null ? null : taskDef.getParams()));
        }
        if (contentDO.getSendTime() == null) {
            contentDO.setSendTime(new Date());
        }
        contentDO.setContentStatus(1);
        confEmailContentService.updateById(contentDO);

        Integer retryCount = taskLog.getRetryCount() == null ? 0 : taskLog.getRetryCount();
        sysTaskLogMapper.updateById(SysTaskLogDO.builder()
                .id(taskLogId)
                .executionStatus(1)
                .retryCount(retryCount + 1)
                .startTime(new Date())
                .errorMsg("邮件任务重试中")
                .build());

        return Results.success();
    }

    private void validateEmailTask(SysTaskLogDO taskLog) {
        if (taskLog == null) {
            throw new ClientException("任务日志不存在");
        }
        if (!TaskConstants.EMAIL_TASK_HANDLER.equals(taskLog.getHandlerBean())) {
            throw new ClientException("该任务不是邮件发送任务");
        }
    }

    private String parseTargetRole(String params) {
        if (!StringUtils.hasText(params)) {
            return null;
        }
        JSONObject config = JSON.parseObject(params);
        if (config == null) {
            return null;
        }
        JSONObject emailConfig = config.getJSONObject("email");

        String explicitRole = emailConfig == null ? null : emailConfig.getString("targetRole");
        if (!StringUtils.hasText(explicitRole)) {
            explicitRole = config.getString("targetRole");
        }
        if (StringUtils.hasText(explicitRole)) {
            return explicitRole;
        }

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
        if (!StringUtils.hasText(emailTypeCode)) {
            return null;
        }
        try {
            return EmailType.valueOf(emailTypeCode).getDefaultRole();
        } catch (IllegalArgumentException ex) {
            return null;
        }
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
}
