package com.jxl.ai.intelliconf.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jxl.ai.intelliconf.common.biz.user.UserContext;
import com.jxl.ai.intelliconf.common.convention.result.Result;
import com.jxl.ai.intelliconf.common.convention.result.Results;
import com.jxl.ai.intelliconf.dao.entity.ConfTaskInstanceDO;
import com.jxl.ai.intelliconf.dao.entity.SysTaskLogDO;
import com.jxl.ai.intelliconf.dao.mapper.ConfTaskInstanceMapper;
import com.jxl.ai.intelliconf.dao.mapper.SysTaskLogMapper;
import com.jxl.ai.intelliconf.dto.req.TaskCompleteReqDTO;
import com.jxl.ai.intelliconf.enums.ConferenceRole;
import com.jxl.ai.intelliconf.service.ConferencePermissionService;
import com.jxl.ai.intelliconf.service.ConferenceTaskService;
import com.jxl.ai.intelliconf.service.MilestoneCompletionChecker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;

@Slf4j
@RestController
@RequestMapping("/api/task/manual")
@RequiredArgsConstructor
public class ManualTaskController {

    private final SysTaskLogMapper sysTaskLogMapper;
    private final ConfTaskInstanceMapper confTaskInstanceMapper;
    private final ConferencePermissionService conferencePermissionService;
    private final MilestoneCompletionChecker milestoneCompletionChecker;
    private final ConferenceTaskService conferenceTaskService;

    @PostMapping("/complete/{logId}")
    public Result<Void> completeTask(@PathVariable Long logId) {
        log.info("[ManualTask] Received completion callback, logId={}", logId);
        SysTaskLogDO taskLog = sysTaskLogMapper.selectById(logId);

        if (taskLog == null) {
            ConfTaskInstanceDO taskInstance = confTaskInstanceMapper.selectById(logId);
            if (taskInstance != null) {
                TaskCompleteReqDTO command = new TaskCompleteReqDTO();
                command.setCompletionDesc("Legacy manual completion entry");
                conferenceTaskService.completeTask(taskInstance.getConferenceId(), taskInstance.getId(), currentUserId(), command);
                return Results.success();
            }
            return Results.failure("TASK_NOT_FOUND", "Task not found");
        }

        conferencePermissionService.requireAtLeastRole(taskLog.getConfereId(), ConferenceRole.OPERATOR);
        if (Integer.valueOf(2).equals(taskLog.getExecutionStatus())) {
            return Results.failure("TASK_ALREADY_DONE", "Task already completed");
        }

        int updated = sysTaskLogMapper.update(null,
                Wrappers.lambdaUpdate(SysTaskLogDO.class)
                        .set(SysTaskLogDO::getExecutionStatus, 2)
                        .set(SysTaskLogDO::getEndTime, new Date())
                        .eq(SysTaskLogDO::getId, logId)
                        .eq(SysTaskLogDO::getExecutionStatus, 1));
        if (updated == 0 && Integer.valueOf(0).equals(taskLog.getExecutionStatus())) {
            taskLog.setExecutionStatus(2);
            taskLog.setEndTime(new Date());
            updated = sysTaskLogMapper.updateById(taskLog);
        }
        if (updated == 0) {
            return Results.failure("TASK_STATUS_CONFLICT", "Task status conflict");
        }

        milestoneCompletionChecker.checkAndComplete(taskLog.getMilestoneId());
        return Results.success();
    }

    private String currentUserId() {
        String username = UserContext.getUsername();
        return username == null ? UserContext.getUserId() : username;
    }
}
