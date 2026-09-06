package com.jxl.ai.intelliconf.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jxl.ai.intelliconf.common.constant.TaskConstants;
import com.jxl.ai.intelliconf.common.convention.result.Result;
import com.jxl.ai.intelliconf.common.convention.result.Results;
import com.jxl.ai.intelliconf.dao.entity.ConfMilestoneDO;
import com.jxl.ai.intelliconf.dao.entity.ConfTaskDefDO;
import com.jxl.ai.intelliconf.dao.entity.ConfTaskInstanceDO;
import com.jxl.ai.intelliconf.dao.entity.ConfEmailContentDO;
import com.jxl.ai.intelliconf.dao.entity.SysMailLogDO;
import com.jxl.ai.intelliconf.dao.entity.SysTaskBatchStatDO;
import com.jxl.ai.intelliconf.dao.entity.SysTaskLogDO;
import com.jxl.ai.intelliconf.dao.mapper.ConfMilestoneMapper;
import com.jxl.ai.intelliconf.dao.mapper.ConfTaskDefMapper;
import com.jxl.ai.intelliconf.dao.mapper.ConfTaskInstanceMapper;
import com.jxl.ai.intelliconf.dao.mapper.ConfEmailContentMapper;
import com.jxl.ai.intelliconf.dao.mapper.SysMailLogMapper;
import com.jxl.ai.intelliconf.dao.mapper.SysTaskBatchStatMapper;
import com.jxl.ai.intelliconf.dao.mapper.SysTaskLogMapper;
import com.jxl.ai.intelliconf.dispatcher.TaskDispatcher;
import com.jxl.ai.intelliconf.dto.resp.MailLogRespDTO;
import com.jxl.ai.intelliconf.dto.resp.EmailContentRespDTO;
import com.jxl.ai.intelliconf.dto.resp.TaskBatchStatRespDTO;
import com.jxl.ai.intelliconf.dto.resp.TaskDetailRespDTO;
import com.jxl.ai.intelliconf.dto.resp.TaskLogRespDTO;
import com.jxl.ai.intelliconf.service.ConferenceTimelineService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 任务日志查询控制器
 */
@RestController
@RequiredArgsConstructor
public class TaskLogController {

    private final SysTaskLogMapper sysTaskLogMapper;
    private final ConfTaskDefMapper confTaskDefMapper;
    private final ConfMilestoneMapper confMilestoneMapper;
    private final ConfEmailContentMapper confEmailContentMapper;
    private final SysTaskBatchStatMapper sysTaskBatchStatMapper;
    private final SysMailLogMapper sysMailLogMapper;
    private final TaskDispatcher taskDispatcher;
    private final ConfTaskInstanceMapper confTaskInstanceMapper;
    private final ConferenceTimelineService conferenceTimelineService;

    /**
     * 查询指定里程碑下的所有任务日志（包含任务定义信息）
     *
     * @param milestoneId 里程碑ID
     * @return 任务日志列表
     */
    @GetMapping("/api/task/logs/{milestoneId}")
    public Result<List<TaskLogRespDTO>> getTaskLogs(@PathVariable Long milestoneId) {
        // 1. 查询任务日志
        List<SysTaskLogDO> logs = sysTaskLogMapper.selectList(
                Wrappers.lambdaQuery(SysTaskLogDO.class)
                        .eq(SysTaskLogDO::getMilestoneId, milestoneId)
                        .orderByAsc(SysTaskLogDO::getId)
        );

        if (logs.isEmpty()) {
            // 里程碑未激活时，日志表可能还没有记录。此处返回任务定义预览，便于前端提前展示未来任务。
            ConfMilestoneDO milestone = confMilestoneMapper.selectById(milestoneId);
            if (milestone == null) {
            return Results.success(List.of());
            }

            // 若里程碑已进行中但日志缺失，触发一次幂等分发进行补偿。
            List<ConfTaskInstanceDO> existingInstances = confTaskInstanceMapper.selectList(
                    Wrappers.lambdaQuery(ConfTaskInstanceDO.class)
                            .eq(ConfTaskInstanceDO::getMilestoneId, milestoneId)
                            .ne(ConfTaskInstanceDO::getStatus, "CANCELLED")
                            .orderByAsc(ConfTaskInstanceDO::getId)
            );
            if (!existingInstances.isEmpty()) {
                return Results.success(existingInstances.stream()
                        .map(this::buildTaskLogRespFromInstance)
                        .collect(Collectors.toList()));
            }

            if (Integer.valueOf(1).equals(milestone.getStatus())) {
                conferenceTimelineService.generateTasksForMilestone(milestone.getConfereId(), milestone.getId(), "SYSTEM");
                List<ConfTaskInstanceDO> instances = confTaskInstanceMapper.selectList(
                        Wrappers.lambdaQuery(ConfTaskInstanceDO.class)
                                .eq(ConfTaskInstanceDO::getMilestoneId, milestoneId)
                                .ne(ConfTaskInstanceDO::getStatus, "CANCELLED")
                                .orderByAsc(ConfTaskInstanceDO::getId)
                );
                if (!instances.isEmpty()) {
                    return Results.success(instances.stream()
                            .map(this::buildTaskLogRespFromInstance)
                            .collect(Collectors.toList()));
                }
            }

            if (!logs.isEmpty()) {
                // 继续走后续通用映射逻辑
            } else {

                List<ConfTaskDefDO> taskDefs = confTaskDefMapper.selectList(
                    Wrappers.lambdaQuery(ConfTaskDefDO.class)
                        .eq(ConfTaskDefDO::getNodeCode, milestone.getNodeCode())
                        .eq(ConfTaskDefDO::getStatus, 1)
                        .orderByAsc(ConfTaskDefDO::getPriority)
                );

                List<TaskLogRespDTO> previewTasks = taskDefs.stream().map(taskDef -> TaskLogRespDTO.builder()
                    .id(-taskDef.getId())
                    .confereId(milestone.getConfereId())
                    .milestoneId(milestoneId)
                    .taskDefId(taskDef.getId())
                    .handlerBean(taskDef.getHandlerBean())
                    .executionStatus(0)
                    .retryCount(0)
                    .taskName(taskDef.getTaskName())
                    .params(taskDef.getParams())
                    .priority(taskDef.getPriority())
                    .build()).collect(Collectors.toList());

                return Results.success(previewTasks);
            }
        }

        // 2. 查询关联的任务定义
        List<Long> taskDefIds = logs.stream()
                .map(SysTaskLogDO::getTaskDefId)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, ConfTaskDefDO> taskDefMap = confTaskDefMapper.selectList(
                Wrappers.lambdaQuery(ConfTaskDefDO.class)
                        .in(ConfTaskDefDO::getId, taskDefIds)
        ).stream().collect(Collectors.toMap(ConfTaskDefDO::getId, def -> def));

        // 3. 组装响应DTO
        List<TaskLogRespDTO> result = logs.stream().filter(log -> {
            ConfTaskDefDO taskDef = taskDefMap.get(log.getTaskDefId());
            return taskDef != null && Integer.valueOf(1).equals(taskDef.getStatus());
        }).map(log -> {
            ConfTaskDefDO taskDef = taskDefMap.get(log.getTaskDefId());
            return TaskLogRespDTO.builder()
                    .id(log.getId())
                    .confereId(log.getConfereId())
                    .milestoneId(log.getMilestoneId())
                    .taskDefId(log.getTaskDefId())
                    .handlerBean(log.getHandlerBean())
                    .executionStatus(log.getExecutionStatus())
                    .retryCount(log.getRetryCount())
                    .errorMsg(log.getErrorMsg())
                    .startTime(log.getStartTime())
                    .endTime(log.getEndTime())
                    .createTime(log.getCreateTime())
                    .taskName(taskDef != null ? taskDef.getTaskName() : null)
                    .params(taskDef != null ? taskDef.getParams() : null)
                    .priority(taskDef != null ? taskDef.getPriority() : null)
                    .build();
        }).collect(Collectors.toList());

        return Results.success(result);
    }

    @GetMapping("/api/task/detail/{taskId}")
    public Result<TaskDetailRespDTO> getTaskDetail(@PathVariable Long taskId) {
        SysTaskLogDO taskLog = sysTaskLogMapper.selectById(taskId);
        if (taskLog == null) {
            return new Result<TaskDetailRespDTO>()
                    .setCode("TASK_NOT_FOUND")
                    .setMessage("任务日志不存在");
        }

        ConfTaskDefDO taskDef = confTaskDefMapper.selectById(taskLog.getTaskDefId());
        TaskLogRespDTO taskResp = buildTaskLogResp(taskLog, taskDef);

        TaskBatchStatRespDTO batchStat = null;
        EmailContentRespDTO emailContent = null;
        if (TaskConstants.EMAIL_TASK_HANDLER.equals(taskLog.getHandlerBean())) {
            SysTaskBatchStatDO statDO = sysTaskBatchStatMapper.selectById(taskId);
            if (statDO != null) {
                batchStat = TaskBatchStatRespDTO.builder()
                        .taskLogId(statDO.getTaskLogId())
                        .totalCount(statDO.getTotalCount())
                        .successCount(statDO.getSuccessCount())
                        .failCount(statDO.getFailCount())
                        .build();
            }

            ConfEmailContentDO emailContentDO = confEmailContentMapper.selectById(taskId);
            if (emailContentDO != null) {
                emailContent = EmailContentRespDTO.builder()
                        .taskLogId(emailContentDO.getTaskLogId())
                        .confId(emailContentDO.getConfId())
                        .subject(emailContentDO.getSubject())
                        .contentBody(emailContentDO.getContentBody())
                        .targetRole(emailContentDO.getTargetRole())
                        .sendTime(emailContentDO.getSendTime())
                        .contentStatus(emailContentDO.getContentStatus())
                        .build();
            }
        }

        return Results.success(TaskDetailRespDTO.builder()
                .task(taskResp)
                .batchStat(batchStat)
                .emailContent(emailContent)
                .build());
    }

    @GetMapping("/api/task/mail-logs/{taskId}")
    public Result<List<MailLogRespDTO>> getMailLogs(
            @PathVariable Long taskId,
            @RequestParam(defaultValue = "false") boolean onlyFailed) {
        List<SysMailLogDO> mailLogs = sysMailLogMapper.selectList(
                Wrappers.lambdaQuery(SysMailLogDO.class)
                        .eq(SysMailLogDO::getTaskLogId, taskId)
                        .eq(onlyFailed, SysMailLogDO::getSendStatus, 0)
                        .orderByDesc(SysMailLogDO::getCreateTime)
                        .orderByDesc(SysMailLogDO::getId)
        );

        List<MailLogRespDTO> result = mailLogs.stream()
                .map(item -> MailLogRespDTO.builder()
                        .id(item.getId())
                        .taskLogId(item.getTaskLogId())
                        .recipientEmail(item.getRecipientEmail())
                        .sendStatus(item.getSendStatus())
                        .errorMsg(item.getErrorMsg())
                        .createTime(item.getCreateTime())
                        .build())
                .collect(Collectors.toList());

        return Results.success(result);
    }

    private TaskLogRespDTO buildTaskLogResp(SysTaskLogDO log, ConfTaskDefDO taskDef) {
        return TaskLogRespDTO.builder()
                .id(log.getId())
                .confereId(log.getConfereId())
                .milestoneId(log.getMilestoneId())
                .taskDefId(log.getTaskDefId())
                .handlerBean(log.getHandlerBean())
                .executionStatus(log.getExecutionStatus())
                .retryCount(log.getRetryCount())
                .errorMsg(log.getErrorMsg())
                .startTime(log.getStartTime())
                .endTime(log.getEndTime())
                .createTime(log.getCreateTime())
                .taskName(taskDef != null ? taskDef.getTaskName() : null)
                .params(taskDef != null ? taskDef.getParams() : null)
                .priority(taskDef != null ? taskDef.getPriority() : null)
                .build();
    }

    private TaskLogRespDTO buildTaskLogRespFromInstance(ConfTaskInstanceDO task) {
        return TaskLogRespDTO.builder()
                .id(task.getId())
                .confereId(task.getConferenceId())
                .milestoneId(task.getMilestoneId())
                .taskDefId(task.getTaskDefId())
                .handlerBean(task.getHandlerBean())
                .executionStatus(toLegacyExecutionStatus(task.getStatus()))
                .retryCount(0)
                .startTime(task.getCreatedAt())
                .endTime("COMPLETED".equals(task.getStatus()) ? task.getUpdatedAt() : null)
                .createTime(task.getCreatedAt())
                .taskName(task.getTaskName())
                .params(task.getParams())
                .priority(toLegacyPriority(task.getPriority()))
                .build();
    }

    private Integer toLegacyExecutionStatus(String status) {
        if ("COMPLETED".equals(status)) {
            return 2;
        }
        if ("FAILED".equals(status) || "REJECTED".equals(status) || "CANCELLED".equals(status)) {
            return 3;
        }
        return 1;
    }

    private Integer toLegacyPriority(String priority) {
        if ("HIGH".equals(priority)) {
            return 1;
        }
        if ("LOW".equals(priority)) {
            return 3;
        }
        return 2;
    }
}
