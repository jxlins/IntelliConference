package com.jxl.ai.intelliconf.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jxl.ai.intelliconf.dao.entity.ConfTaskActionLogDO;
import com.jxl.ai.intelliconf.dao.entity.ConfTaskAssignmentDO;
import com.jxl.ai.intelliconf.dao.entity.ConfTaskInstanceDO;
import com.jxl.ai.intelliconf.dao.entity.ConfMilestoneDO;
import com.jxl.ai.intelliconf.dao.mapper.ConfMilestoneMapper;
import com.jxl.ai.intelliconf.dao.mapper.ConfTaskActionLogMapper;
import com.jxl.ai.intelliconf.dao.mapper.ConfTaskAssignmentMapper;
import com.jxl.ai.intelliconf.dao.mapper.ConfTaskInstanceMapper;
import com.jxl.ai.intelliconf.service.ConferenceTaskAutomationService;
import com.jxl.ai.intelliconf.service.ConferenceTimelineService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ConferenceTaskAutomationServiceImpl implements ConferenceTaskAutomationService {

    private static final Set<String> MAIL_ACCOUNT_TASK_CODES = Set.of(
            "CONFIG_MAIL_ACCOUNT",
            "BIND_MAIL_ACCOUNT",
            "MAIL_ACCOUNT_CONFIG",
            "SETUP_MAIL_ACCOUNT",
            "CONFIGURE_EMAIL",
            "CONFIGURE_MAIL_ACCOUNT",
            "BIND_OFFICIAL_MAIL"
    );

    private final ConfTaskInstanceMapper taskInstanceMapper;
    private final ConfTaskAssignmentMapper assignmentMapper;
    private final ConfTaskActionLogMapper actionLogMapper;
    private final ConfMilestoneMapper milestoneMapper;
    private final ConferenceTimelineService conferenceTimelineService;

    @Override
    @Transactional
    public void markMailAccountConfigured(Long conferenceId, String operatorId) {
        if (conferenceId == null) {
            return;
        }
        ConfMilestoneDO initiation = milestoneMapper.selectOne(
                Wrappers.lambdaQuery(ConfMilestoneDO.class)
                        .eq(ConfMilestoneDO::getConfereId, conferenceId)
                        .eq(ConfMilestoneDO::getNodeCode, "INITIATION")
                        .last("limit 1")
        );
        if (initiation != null) {
            conferenceTimelineService.generateTasksForMilestone(conferenceId, initiation.getId(), operatorId);
        }
        List<ConfTaskInstanceDO> tasks = taskInstanceMapper.selectList(
                Wrappers.lambdaQuery(ConfTaskInstanceDO.class)
                        .eq(ConfTaskInstanceDO::getConferenceId, conferenceId)
                        .eq(ConfTaskInstanceDO::getNodeCode, "INITIATION")
                        .in(ConfTaskInstanceDO::getStatus, List.of("PENDING", "PROCESSING"))
        );
        for (ConfTaskInstanceDO task : tasks) {
            if (isMailAccountTask(task)) {
                completeTask(task, operatorId);
            }
        }
    }

    private boolean isMailAccountTask(ConfTaskInstanceDO task) {
        if (task == null) {
            return false;
        }
        if (StringUtils.hasText(task.getTaskCode())
                && MAIL_ACCOUNT_TASK_CODES.contains(task.getTaskCode().trim().toUpperCase(Locale.ROOT))) {
            return true;
        }
        String combined = ((task.getTaskName() == null ? "" : task.getTaskName()) + " "
                + (task.getTaskDesc() == null ? "" : task.getTaskDesc()) + " "
                + (task.getParams() == null ? "" : task.getParams())).toLowerCase(Locale.ROOT);
        return combined.contains("mail_account")
                || combined.contains("mail account")
                || combined.contains("official mail")
                || combined.contains("official email")
                || combined.contains("smtp")
                || combined.contains("邮箱")
                || combined.contains("邮件");
    }

    private void completeTask(ConfTaskInstanceDO task, String operatorId) {
        String before = task.getStatus();
        Date now = new Date();
        String operator = StringUtils.hasText(operatorId) ? operatorId : "SYSTEM";
        task.setStatus("COMPLETED");
        task.setCurrentHandlerId(operator);
        task.setCurrentHandlerName(operator);
        task.setUpdatedAt(now);
        taskInstanceMapper.updateById(task);

        List<ConfTaskAssignmentDO> assignments = assignmentMapper.selectList(
                Wrappers.lambdaQuery(ConfTaskAssignmentDO.class)
                        .eq(ConfTaskAssignmentDO::getTaskInstanceId, task.getId())
        );
        for (ConfTaskAssignmentDO assignment : assignments) {
            assignment.setStatus("COMPLETED");
            if (assignment.getAcceptedAt() == null) {
                assignment.setAcceptedAt(now);
            }
            assignment.setCompletedAt(now);
            assignment.setUpdatedAt(now);
            assignmentMapper.updateById(assignment);
        }

        actionLogMapper.insert(ConfTaskActionLogDO.builder()
                .taskInstanceId(task.getId())
                .conferenceId(task.getConferenceId())
                .operatorId(operator)
                .operatorName(operator)
                .operatorRoleCode("ORGANIZER")
                .actionType("COMPLETE")
                .comment("Official mail account configured.")
                .beforeStatus(before)
                .afterStatus("COMPLETED")
                .createdAt(now)
                .build());
    }
}
