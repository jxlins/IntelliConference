package com.jxl.ai.intelliconf.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jxl.ai.intelliconf.common.constant.TaskConstants;
import com.jxl.ai.intelliconf.dao.entity.ConfEmailContentDO;
import com.jxl.ai.intelliconf.dao.entity.ConfTaskDefDO;
import com.jxl.ai.intelliconf.dao.entity.SysMailLogDO;
import com.jxl.ai.intelliconf.dao.entity.SysTaskBatchStatDO;
import com.jxl.ai.intelliconf.dao.entity.SysTaskLogDO;
import com.jxl.ai.intelliconf.dao.mapper.ConfEmailContentMapper;
import com.jxl.ai.intelliconf.dao.mapper.ConfTaskDefMapper;
import com.jxl.ai.intelliconf.dao.mapper.SysMailLogMapper;
import com.jxl.ai.intelliconf.dao.mapper.SysTaskBatchStatMapper;
import com.jxl.ai.intelliconf.dao.mapper.SysTaskLogMapper;
import com.jxl.ai.intelliconf.dto.resp.BasicMailSendRespDTO;
import com.jxl.ai.intelliconf.dto.resp.ContactDTO;
import com.jxl.ai.intelliconf.enums.EmailType;
import com.jxl.ai.intelliconf.service.BasicMailSendService;
import com.jxl.ai.intelliconf.service.EmailTaskDispatchService;
import com.jxl.ai.intelliconf.service.MemberService;
import com.jxl.ai.intelliconf.service.MilestoneCompletionChecker;
import com.jxl.ai.intelliconf.service.SysTaskBatchStatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailTaskDispatchServiceImpl implements EmailTaskDispatchService {

    private final SysTaskLogMapper sysTaskLogMapper;
    private final ConfTaskDefMapper confTaskDefMapper;
    private final ConfEmailContentMapper confEmailContentMapper;
    private final MemberService memberService;
    private final BasicMailSendService basicMailSendService;
    private final SysTaskBatchStatService sysTaskBatchStatService;
    private final SysTaskBatchStatMapper taskBatchStatMapper;
    private final SysMailLogMapper sysMailLogMapper;
    private final MilestoneCompletionChecker milestoneCompletionChecker;

    @Override
    public void dispatchDueEmailTasks() {
        List<SysTaskLogDO> emailRunningTasks = sysTaskLogMapper.selectList(
                Wrappers.lambdaQuery(SysTaskLogDO.class)
                        .eq(SysTaskLogDO::getHandlerBean, TaskConstants.EMAIL_TASK_HANDLER)
                        .eq(SysTaskLogDO::getExecutionStatus, 1)
        );

        for (SysTaskLogDO taskLog : emailRunningTasks) {
            try {
                dispatchOneTask(taskLog);
            } catch (Exception ex) {
                log.error("[EmailTaskDispatch] 处理邮件任务失败, taskLogId={}", taskLog.getId(), ex);
            }
        }
    }

    private void dispatchOneTask(SysTaskLogDO taskLog) {
        ConfEmailContentDO content = confEmailContentMapper.selectById(taskLog.getId());
        if (content == null) {
            createDraftIfMissing(taskLog);
            markTaskWaitingForContent(taskLog.getId());
            return;
        }

        if (content.getContentStatus() == null || content.getContentStatus() != 1) {
            if (Integer.valueOf(2).equals(content.getContentStatus())) {
                reconcileTaskStatusIfStuck(taskLog);
            } else if (Integer.valueOf(3).equals(content.getContentStatus())) {
                recoverStuckProcessingTask(taskLog);
            }
            return;
        }

        int lock = confEmailContentMapper.markProcessingIfDue(taskLog.getId());
        if (lock == 0) {
            return;
        }

        try {
            executeLockedEmailTask(taskLog, content);
        } catch (Exception ex) {
            confEmailContentMapper.markDraft(taskLog.getId());
            finishTask(taskLog, 3, "邮件发送异常中断，已回退为草稿可重试：" + buildBusinessErrorMessage(ex));
            log.error("[EmailTaskDispatch] 邮件任务异常中断并已回退, taskLogId={}", taskLog.getId(), ex);
        }
    }

    private void executeLockedEmailTask(SysTaskLogDO taskLog, ConfEmailContentDO content) {

        ConfTaskDefDO taskDef = confTaskDefMapper.selectById(taskLog.getTaskDefId());
        if (taskDef == null) {
            finishTask(taskLog, 3, "邮件任务定义不存在");
            confEmailContentMapper.markSent(taskLog.getId());
            return;
        }

        JSONObject params = JSON.parseObject(taskDef.getParams());
        String emailTypeCode = resolveEmailTypeCode(params);
        String targetRole = StringUtils.hasText(content.getTargetRole()) ? content.getTargetRole() : resolveTargetRole(params, emailTypeCode);
        targetRole = normalizeTargetRole(targetRole);
        String subject = content.getSubject();
        String contentBody = content.getContentBody();

        if (!StringUtils.hasText(targetRole) || !StringUtils.hasText(subject) || !StringUtils.hasText(contentBody)) {
            confEmailContentMapper.markDraft(taskLog.getId());
            markTaskWaitingForContent(taskLog.getId());
            return;
        }

        List<ContactDTO> recipients = memberService.getParticipantsByRole(taskLog.getConfereId(), targetRole);
        if (recipients == null || recipients.isEmpty()) {
            finishTask(taskLog, 3, "邮件发送失败：未找到可发送的收件人，请检查参会者角色是否为 " + targetRole);
            confEmailContentMapper.markDraft(taskLog.getId());
            return;
        }
        initBatchStat(taskLog.getId(), recipients.size());

        BasicMailSendRespDTO sendResult = basicMailSendService.sendBatch(
            taskLog.getConfereId(),
                taskLog.getId(),
                subject,
                contentBody,
                recipients,
                true
        );

        int successCount = sendResult.getSuccessCount() == null ? 0 : sendResult.getSuccessCount();
        int failCount = sendResult.getFailCount() == null ? 0 : sendResult.getFailCount();
        int totalCount = sendResult.getTotalCount() == null ? recipients.size() : sendResult.getTotalCount();

        finalizeBatchStat(taskLog.getId(), totalCount, successCount, failCount);

        // 整体任务状态判定：只要存在至少一封发送成功，则任务视为成功；失败个体保留在明细日志中。
        int finalStatus = successCount > 0 ? 2 : 3;
        String summary = buildTaskSummary(
                successCount,
                failCount,
                totalCount,
                sendResult.getSuccessEmails(),
                sendResult.getFailedDetails()
        );
        finishTask(taskLog, finalStatus, summary);
        confEmailContentMapper.markSent(taskLog.getId());
        if (taskLog.getMilestoneId() != null) {
            milestoneCompletionChecker.checkAndComplete(taskLog.getMilestoneId());
        }
    }

    private void initBatchStat(Long taskLogId, int totalCount) {
        sysTaskBatchStatService.saveOrUpdate(
                SysTaskBatchStatDO.builder()
                        .taskLogId(taskLogId)
                        .totalCount(totalCount)
                        .successCount(0)
                        .failCount(0)
                        .processStatus(totalCount == 0 ? 2 : 1)
                        .build(),
                Wrappers.lambdaUpdate(SysTaskBatchStatDO.class)
                        .eq(SysTaskBatchStatDO::getTaskLogId, taskLogId)
                        .set(SysTaskBatchStatDO::getTotalCount, totalCount)
                        .set(SysTaskBatchStatDO::getSuccessCount, 0)
                        .set(SysTaskBatchStatDO::getFailCount, 0)
                        .set(SysTaskBatchStatDO::getProcessStatus, totalCount == 0 ? 2 : 1)
        );
    }

    private void finalizeBatchStat(Long taskLogId, int totalCount, int successCount, int failCount) {
        sysTaskBatchStatService.saveOrUpdate(
                SysTaskBatchStatDO.builder()
                        .taskLogId(taskLogId)
                        .totalCount(totalCount)
                        .successCount(successCount)
                        .failCount(failCount)
                        .processStatus(2)
                        .build(),
                Wrappers.lambdaUpdate(SysTaskBatchStatDO.class)
                        .eq(SysTaskBatchStatDO::getTaskLogId, taskLogId)
                        .set(SysTaskBatchStatDO::getTotalCount, totalCount)
                        .set(SysTaskBatchStatDO::getSuccessCount, successCount)
                        .set(SysTaskBatchStatDO::getFailCount, failCount)
                        .set(SysTaskBatchStatDO::getProcessStatus, 2)
        );
    }

    private void finishTask(SysTaskLogDO taskLog, int status, String remark) {
        taskLog.setExecutionStatus(status);
        taskLog.setEndTime(new Date());
        taskLog.setErrorMsg(remark);
        sysTaskLogMapper.updateById(taskLog);
    }

    private String parseTargetRole(String emailTypeCode) {
        if (!StringUtils.hasText(emailTypeCode)) {
            return null;
        }
        try {
            return EmailType.valueOf(emailTypeCode).getDefaultRole();
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String normalizeTargetRole(String role) {
        if (!StringUtils.hasText(role)) {
            return role;
        }
        if ("POTENTIAL_AUTHORS".equalsIgnoreCase(role)) {
            return "PROSPECT";
        }
        if ("CURRENT_AUTHOR".equalsIgnoreCase(role) || "ALL_AUTHORS".equalsIgnoreCase(role)) {
            return "AUTHOR";
        }
        if ("REVIEWER_POOL".equalsIgnoreCase(role)) {
            return "REVIEWER";
        }
        return role;
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
            String explicitRole = emailConfig == null ? null : emailConfig.getString("targetRole");
            if (!StringUtils.hasText(explicitRole)) {
                explicitRole = config.getString("targetRole");
            }
            if (StringUtils.hasText(explicitRole)) {
                return explicitRole;
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

    private void createDraftIfMissing(SysTaskLogDO taskLog) {
        if (confEmailContentMapper.selectById(taskLog.getId()) != null) {
            return;
        }
        ConfTaskDefDO taskDef = confTaskDefMapper.selectById(taskLog.getTaskDefId());
        JSONObject params = taskDef == null ? null : JSON.parseObject(taskDef.getParams());
        String emailTypeCode = resolveEmailTypeCode(params);
        String targetRole = resolveTargetRole(params, emailTypeCode);

        try {
            confEmailContentMapper.insert(ConfEmailContentDO.builder()
                    .taskLogId(taskLog.getId())
                    .confId(taskLog.getConfereId())
                    .targetRole(targetRole)
                    .contentStatus(0)
                    .sendTime(new Date())
                    .build());
        } catch (Exception ignore) {
            // 并发下可能由其他线程先创建，忽略重复创建异常。
        }
    }

    private void markTaskWaitingForContent(Long taskLogId) {
        sysTaskLogMapper.updateById(SysTaskLogDO.builder()
                .id(taskLogId)
                .executionStatus(1)
                .errorMsg("待配置邮件内容：请先填写邮件主题和正文")
                .build());
    }

    private String buildBusinessErrorMessage(Exception ex) {
        String message = ex == null ? null : ex.getMessage();
        if (!StringUtils.hasText(message)) {
            return "邮件发送失败：邮件服务返回空错误信息";
        }
        String compact = message.replaceAll("[\\r\\n\\t]+", " ").trim();
        if (compact.length() > 300) {
            compact = compact.substring(0, 300);
        }
        return compact;
    }

    private String buildTaskSummary(int successCount, int failCount, int total, List<String> successSamples, List<String> failReasonSamples) {
        String summary = String.format("邮件发送完成: success=%d, fail=%d, total=%d", successCount, failCount, total);
        if (successCount > 0 && successSamples != null && !successSamples.isEmpty()) {
            summary = summary + "；成功示例：" + successSamples.stream().collect(Collectors.joining(", "));
        }
        if (failCount > 0 && failReasonSamples != null && !failReasonSamples.isEmpty()) {
            summary = summary + "；失败示例：" + failReasonSamples.stream().collect(Collectors.joining(" | "));
        }
        return summary;
    }

    /**
     * content_status=3 且任务长时间停留在执行中的兜底回收。
     */
    private void recoverStuckProcessingTask(SysTaskLogDO taskLog) {
        if (!Integer.valueOf(1).equals(taskLog.getExecutionStatus())) {
            return;
        }
        confEmailContentMapper.markDraft(taskLog.getId());
        finishTask(taskLog, 3, "邮件发送状态异常卡在发送中，系统已回退为草稿，请检查后重试");
        log.warn("[EmailTaskDispatch] 检测到邮件任务卡在发送中并已回退, taskLogId={}", taskLog.getId());
    }

    /**
     * 兼容修复：若内容状态已发送但任务仍停留在执行中，自动根据统计/明细回收任务终态。
     */
    private void reconcileTaskStatusIfStuck(SysTaskLogDO taskLog) {
        if (!Integer.valueOf(1).equals(taskLog.getExecutionStatus())) {
            return;
        }

        int success = 0;
        int fail = 0;
        int total = 0;

        SysTaskBatchStatDO stat = taskBatchStatMapper.selectById(taskLog.getId());
        if (stat != null) {
            success = safeInt(stat.getSuccessCount());
            fail = safeInt(stat.getFailCount());
            total = safeInt(stat.getTotalCount());
        }

        if (total == 0) {
            Long successCount = sysMailLogMapper.selectCount(
                    Wrappers.lambdaQuery(SysMailLogDO.class)
                            .eq(SysMailLogDO::getTaskLogId, taskLog.getId())
                            .eq(SysMailLogDO::getSendStatus, 1)
            );
            Long failCount = sysMailLogMapper.selectCount(
                    Wrappers.lambdaQuery(SysMailLogDO.class)
                            .eq(SysMailLogDO::getTaskLogId, taskLog.getId())
                            .eq(SysMailLogDO::getSendStatus, 0)
            );
            success = successCount == null ? 0 : successCount.intValue();
            fail = failCount == null ? 0 : failCount.intValue();
            total = success + fail;
        }

        int finalStatus = success > 0 ? 2 : 3;
        String summary = String.format("邮件发送完成(状态回收): success=%d, fail=%d, total=%d", success, fail, total);
        finishTask(taskLog, finalStatus, summary);
        if (taskLog.getMilestoneId() != null) {
            milestoneCompletionChecker.checkAndComplete(taskLog.getMilestoneId());
        }
    }

    private int safeInt(Integer val) {
        return val == null ? 0 : val;
    }
}
