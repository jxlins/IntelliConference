package com.jxl.ai.intelliconf.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jxl.ai.intelliconf.common.constant.ConferenceSetupConstant;
import com.jxl.ai.intelliconf.common.constant.ConferenceTaskConstant;
import com.jxl.ai.intelliconf.common.constant.RedisCacheConstant;
import com.jxl.ai.intelliconf.common.convention.exception.ClientException;
import com.jxl.ai.intelliconf.dao.entity.ConfCommitteeRoleDefDO;
import com.jxl.ai.intelliconf.dao.entity.ConfCommitteeDO;
import com.jxl.ai.intelliconf.dao.entity.ConfMemberRoleDO;
import com.jxl.ai.intelliconf.dao.entity.ConfStageDO;
import com.jxl.ai.intelliconf.dao.entity.ConfStageDefDO;
import com.jxl.ai.intelliconf.dao.entity.ConfTaskAttachmentDO;
import com.jxl.ai.intelliconf.dao.entity.ConfTaskDO;
import com.jxl.ai.intelliconf.dao.entity.ConfTaskActionLogDO;
import com.jxl.ai.intelliconf.dao.entity.ConfTaskAssignmentDO;
import com.jxl.ai.intelliconf.dao.entity.ConfTaskDefDO;
import com.jxl.ai.intelliconf.dao.entity.ConfTaskInstanceDO;
import com.jxl.ai.intelliconf.dao.entity.ConfTaskLogDO;
import com.jxl.ai.intelliconf.dao.entity.ConferenceDO;
import com.jxl.ai.intelliconf.dao.mapper.ConfCommitteeMapper;
import com.jxl.ai.intelliconf.dao.mapper.ConfCommitteeRoleDefMapper;
import com.jxl.ai.intelliconf.dao.mapper.ConfMemberRoleMapper;
import com.jxl.ai.intelliconf.dao.mapper.ConfStageDefMapper;
import com.jxl.ai.intelliconf.dao.mapper.ConfStageMapper;
import com.jxl.ai.intelliconf.dao.mapper.ConfTaskAttachmentMapper;
import com.jxl.ai.intelliconf.dao.mapper.ConfTaskActionLogMapper;
import com.jxl.ai.intelliconf.dao.mapper.ConfTaskAssignmentMapper;
import com.jxl.ai.intelliconf.dao.mapper.ConfTaskDefMapper;
import com.jxl.ai.intelliconf.dao.mapper.ConfTaskLogMapper;
import com.jxl.ai.intelliconf.dao.mapper.ConfTaskMapper;
import com.jxl.ai.intelliconf.dao.mapper.ConfTaskInstanceMapper;
import com.jxl.ai.intelliconf.dao.mapper.ConferenceMapper;
import com.jxl.ai.intelliconf.dto.req.ConferenceTaskQueryReqDTO;
import com.jxl.ai.intelliconf.dto.req.TaskAssignReqDTO;
import com.jxl.ai.intelliconf.dto.req.TaskCompleteReqDTO;
import com.jxl.ai.intelliconf.dto.req.TaskActionReqDTO;
import com.jxl.ai.intelliconf.dto.resp.ConferenceTaskRespDTO;
import com.jxl.ai.intelliconf.dto.resp.TaskAssigneeCandidateRespDTO;
import com.jxl.ai.intelliconf.dto.resp.TaskCompleteRespDTO;
import com.jxl.ai.intelliconf.dto.resp.TaskActionLogRespDTO;
import com.jxl.ai.intelliconf.dto.resp.TaskSystemCheckRespDTO;
import com.jxl.ai.intelliconf.enums.ConferenceRole;
import com.jxl.ai.intelliconf.service.ConferencePermissionService;
import com.jxl.ai.intelliconf.service.ConferenceMemberRoleService;
import com.jxl.ai.intelliconf.service.ConferenceTaskService;
import com.jxl.ai.intelliconf.service.ConferenceTaskVisibilityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConferenceTaskServiceImpl implements ConferenceTaskService {

    private static final long TASK_CACHE_TTL_MINUTES = 30L;

    private final ConfTaskInstanceMapper taskMapper;
    private final ConfTaskMapper conferenceTaskMapper;
    private final ConfTaskDefMapper taskDefMapper;
    private final ConfStageMapper stageMapper;
    private final ConfStageDefMapper stageDefMapper;
    private final ConferenceMapper conferenceMapper;
    private final ConfTaskAssignmentMapper assignmentMapper;
    private final ConfTaskActionLogMapper actionLogMapper;
    private final ConfTaskAttachmentMapper taskAttachmentMapper;
    private final ConfTaskLogMapper taskLogMapper;
    private final ConfCommitteeMapper committeeMapper;
    private final ConfCommitteeRoleDefMapper committeeRoleDefMapper;
    private final ConfMemberRoleMapper memberRoleMapper;
    private final ConferenceTaskVisibilityService visibilityService;
    private final ConferenceMemberRoleService memberRoleService;
    private final ConferencePermissionService conferencePermissionService;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public List<ConferenceTaskRespDTO> previewTasksForConference(Long conferenceId, String currentUserId) {
        ConferenceDO conference = requireConferenceForGeneratedTasks(conferenceId);
        List<ConfStageDO> stages = loadConferenceStages(conferenceId);
        List<ConfStageDefDO> stageDefs = loadEnabledStageDefs();
        List<ConfTaskDefDO> taskDefs = loadEnabledTaskDefs(stageDefs);
        log.info("Preview conference tasks generated, conferenceId={}, taskTemplateCount={}", conferenceId, taskDefs.size());
        return buildTaskPreview(conference, stages, stageDefs, taskDefs);
    }

    @Override
    @Transactional
    public List<ConferenceTaskRespDTO> generateTasksForConference(Long conferenceId, String currentUserId) {
        log.info("Start generating conference tasks, conferenceId={}", conferenceId);
        ConferenceDO conference = requireConferenceForGeneratedTasks(conferenceId);
        List<ConfStageDO> stages = loadConferenceStages(conferenceId);
        List<ConfStageDefDO> stageDefs = loadEnabledStageDefs();
        List<ConfTaskDefDO> taskDefs = loadEnabledTaskDefs(stageDefs);
        log.info("Loaded task templates for generation, conferenceId={}, count={}", conferenceId, taskDefs.size());

        Map<String, ConfStageDO> stageMap = buildStageMap(stages);
        Map<Long, ConfStageDefDO> stageDefMap = stageDefs.stream()
                .collect(Collectors.toMap(ConfStageDefDO::getId, item -> item, (left, right) -> left));
        int insertedCount = 0;
        int updatedCount = 0;
        Long operatorId = parseLongSafely(currentUserId);
        Date now = new Date();
        for (ConfTaskDefDO taskDef : taskDefs) {
            ConfStageDefDO stageDef = stageDefMap.get(taskDef.getStageDefId());
            if (stageDef == null) {
                throw new ClientException("Task template cannot match enabled stage template, taskCode=" + taskDef.getTaskCode());
            }
            ConfStageDO stage = stageMap.get(stageDef.getStageCode());
            if (stage == null) {
                throw new ClientException("Conference stage instance missing for stageCode=" + stageDef.getStageCode());
            }
            Date[] plannedTime = calculateTaskPlannedTime(conference, stage, taskDef);
            if (insertOrUpdateTask(conferenceId, stage, taskDef, plannedTime, operatorId, now)) {
                insertedCount++;
            } else {
                updatedCount++;
            }
        }

        ConferenceDO updateConference = new ConferenceDO();
        updateConference.setSetupStatus(ConferenceSetupConstant.TASK_GENERATED);
        conferenceMapper.update(updateConference, Wrappers.lambdaUpdate(ConferenceDO.class)
                .eq(ConferenceDO::getId, conferenceId));

        syncAutoInProgressTasks(conferenceId, stages, now);
        List<ConferenceTaskRespDTO> result = listGeneratedTasksByConference(conferenceId, stages);
        evictGeneratedTaskCacheAfterCommit(conferenceId);
        log.info("Conference task generation completed, conferenceId={}, inserted={}, updated={}, returned={}",
                conferenceId, insertedCount, updatedCount, result.size());
        return result;
    }

    @Override
    public List<ConferenceTaskRespDTO> listGeneratedTasksForConference(Long conferenceId, String currentUserId) {
        requireConferenceTaskViewPermission(conferenceId, currentUserId);
        List<ConfStageDO> stages = loadConferenceStages(conferenceId);
        boolean statusChanged = syncAutoInProgressTasks(conferenceId, stages, new Date());
        if (statusChanged) {
            evictGeneratedTaskCache(conferenceId);
        } else if (memberRoleService.isOrganizer(conferenceId, currentUserId)) {
            List<ConferenceTaskRespDTO> cachedTasks = getGeneratedTasksFromCache(conferenceId);
            if (cachedTasks != null) {
                return cachedTasks;
            }
        }
        List<ConferenceTaskRespDTO> tasks = filterGeneratedTasksByVisibility(
                conferenceId,
                currentUserId,
                listGeneratedTasksByConference(conferenceId, stages)
        );
        if (memberRoleService.isOrganizer(conferenceId, currentUserId)) {
            putGeneratedTasksToCache(conferenceId, tasks);
        }
        return tasks;
    }

    @Override
    public void evictGeneratedTaskCacheAfterCommit(Long conferenceId) {
        Runnable evict = () -> evictGeneratedTaskCache(conferenceId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evict.run();
                }
            });
            return;
        }
        evict.run();
    }

    @Override
    public List<ConferenceTaskRespDTO> listMyTasks(Long conferenceId, String currentUserId, ConferenceTaskQueryReqDTO query) {
        return visibilityService.listMyTasks(conferenceId, currentUserId, query).stream().map(this::toResp).toList();
    }

    @Override
    public List<ConferenceTaskRespDTO> listAllTasksForOrganizer(Long conferenceId, String currentUserId, ConferenceTaskQueryReqDTO query) {
        return visibilityService.listAllTasksForOrganizer(conferenceId, currentUserId, query).stream().map(this::toResp).toList();
    }

    @Override
    public ConferenceTaskRespDTO getTaskDetail(Long conferenceId, Long taskInstanceId, String currentUserId) {
        ConfTaskDO generatedTask = conferenceTaskMapper.selectOne(Wrappers.lambdaQuery(ConfTaskDO.class)
                .eq(ConfTaskDO::getId, taskInstanceId)
                .eq(ConfTaskDO::getConferenceId, conferenceId)
                .last("limit 1"));
        if (generatedTask != null) {
            requireConferenceTaskViewPermission(conferenceId, currentUserId);
            validateGeneratedTaskViewPermission(conferenceId, generatedTask, currentUserId);
            return toGeneratedTaskResp(generatedTask);
        }
        return toResp(requireVisibleTask(conferenceId, taskInstanceId, currentUserId));
    }

    @Override
    public TaskSystemCheckRespDTO getTaskSystemCheck(Long conferenceId, Long taskId, String currentUserId) {
        requireConferenceTaskViewPermission(conferenceId, currentUserId);
        ConfTaskDO task = requireGeneratedTask(conferenceId, taskId);
        validateGeneratedTaskViewPermission(conferenceId, task, currentUserId);
        if (!ConferenceTaskConstant.COMPLETION_TYPE_SYSTEM_CHECK.equals(resolveCompletionType(task))) {
            throw new ClientException("This task does not support system check");
        }
        return buildSystemCheckResp(task);
    }

    @Override
    public List<TaskAssigneeCandidateRespDTO> listTaskAssigneeCandidates(Long conferenceId, Long taskId, String currentUserId) {
        requireConferenceForGeneratedTasks(conferenceId);
        ConfTaskDO task = requireGeneratedTask(conferenceId, taskId);
        return memberRoleMapper.selectList(Wrappers.lambdaQuery(ConfMemberRoleDO.class)
                        .eq(ConfMemberRoleDO::getConferenceId, conferenceId)
                        .eq(ConfMemberRoleDO::getMemberStatus, ConferenceTaskConstant.MEMBER_STATUS_ACTIVE)
                        .eq(StringUtils.hasText(task.getPrincipalRole()), ConfMemberRoleDO::getRoleCode, task.getPrincipalRole())
                        .orderByAsc(ConfMemberRoleDO::getRoleName)
                        .orderByAsc(ConfMemberRoleDO::getMemberName)
                        .orderByAsc(ConfMemberRoleDO::getId))
                .stream()
                .map(item -> TaskAssigneeCandidateRespDTO.builder()
                        .userId(item.getUserId())
                        .memberName(item.getMemberName())
                        .memberEmail(item.getMemberEmail())
                        .roleCode(item.getRoleCode())
                        .roleName(item.getRoleName())
                        .committeeType(item.getCommitteeType())
                        .committeeName(item.getCommitteeName())
                        .build())
                .toList();
    }

    @Override
    @Transactional
    public void assignTask(Long conferenceId, Long taskId, String currentUserId, TaskAssignReqDTO request) {
        requireConferenceForGeneratedTasks(conferenceId);
        if (request == null || request.getAssigneeUserId() == null) {
            throw new ClientException("Assignee user id is required");
        }
        ConfTaskDO task = requireGeneratedTask(conferenceId, taskId);
        ConfMemberRoleDO memberRole = memberRoleMapper.selectOne(Wrappers.lambdaQuery(ConfMemberRoleDO.class)
                .eq(ConfMemberRoleDO::getConferenceId, conferenceId)
                .eq(ConfMemberRoleDO::getUserId, request.getAssigneeUserId())
                .eq(ConfMemberRoleDO::getMemberStatus, ConferenceTaskConstant.MEMBER_STATUS_ACTIVE)
                .last("limit 1"));
        if (memberRole == null) {
            throw new ClientException("Assigned user is not an active committee member of this conference");
        }
        Long operatorId = parseLongSafely(currentUserId);
        Date now = new Date();
        Long oldPrincipalUserId = task.getPrincipalUserId();
        String oldPrincipalName = task.getPrincipalName();
        task.setPrincipalUserId(memberRole.getUserId());
        task.setPrincipalName(memberRole.getMemberName());
        task.setUpdateUser(operatorId);
        task.setUpdateTime(now);
        conferenceTaskMapper.updateById(task);
        writeGeneratedTaskLog(
                conferenceId,
                taskId,
                "ASSIGN_TASK",
                oldPrincipalUserId == null ? null : String.valueOf(oldPrincipalUserId),
                String.valueOf(memberRole.getUserId()),
                "from=" + (oldPrincipalName == null ? "Unassigned" : oldPrincipalName) + ", to=" + memberRole.getMemberName(),
                operatorId,
                resolveOperatorName(),
                now
        );
        evictGeneratedTaskCacheAfterCommit(conferenceId);
    }

    @Override
    @Transactional
    public void startTask(Long conferenceId, Long taskInstanceId, String currentUserId, TaskActionReqDTO command) {
        ConfTaskInstanceDO task = requireVisibleTask(conferenceId, taskInstanceId, currentUserId);
        if (!"PENDING".equals(task.getStatus())) {
            throw new ClientException("当前事务不是待处理状态，不能开始处理");
        }
        String before = task.getStatus();
        task.setStatus("PROCESSING");
        if (!StringUtils.hasText(task.getCurrentHandlerId())) {
            task.setCurrentHandlerId(currentUserId);
            task.setCurrentHandlerName(currentUserId);
        }
        task.setUpdatedAt(new Date());
        taskMapper.updateById(task);
        updateAssignments(task.getId(), "PROCESSING", true);
        writeLog(task, currentUserId, "START", command == null ? null : command.getComment(), before, "PROCESSING");
    }

    @Override
    @Transactional
    public TaskCompleteRespDTO completeTask(Long conferenceId, Long taskInstanceId, String currentUserId, TaskCompleteReqDTO command) {
        ConfTaskDO generatedTask = conferenceTaskMapper.selectOne(Wrappers.lambdaQuery(ConfTaskDO.class)
                .eq(ConfTaskDO::getId, taskInstanceId)
                .eq(ConfTaskDO::getConferenceId, conferenceId)
                .last("limit 1"));
        if (generatedTask != null) {
            return completeGeneratedTask(conferenceId, generatedTask, currentUserId, command);
        }
        ConfTaskInstanceDO task = requireVisibleTask(conferenceId, taskInstanceId, currentUserId);
        if (!List.of("PENDING", "PROCESSING").contains(task.getStatus())) {
            throw new ClientException("该事务当前状态不能完成");
        }
        String before = task.getStatus();
        task.setStatus("COMPLETED");
        task.setCurrentHandlerId(currentUserId);
        task.setCurrentHandlerName(currentUserId);
        task.setUpdatedAt(new Date());
        taskMapper.updateById(task);
        updateAssignments(task.getId(), "COMPLETED", false);
        writeLog(task, currentUserId, "COMPLETE", command == null ? null : command.getCompletionDesc(), before, "COMPLETED");
        return TaskCompleteRespDTO.builder()
                .taskId(task.getId())
                .taskStatus("COMPLETED")
                .completionDesc(command == null ? null : command.getCompletionDesc())
                .completedBy(parseLongSafely(currentUserId))
                .completedByName(resolveOperatorName())
                .submittedAt(task.getUpdatedAt())
                .actualEndTime(task.getUpdatedAt())
                .build();
    }

    @Override
    @Transactional
    public void transferTask(Long conferenceId, Long taskInstanceId, String currentUserId, TaskActionReqDTO command) {
        memberRoleService.requireOrganizer(conferenceId, currentUserId);
        ConfTaskInstanceDO task = requireTask(conferenceId, taskInstanceId);
        ConfCommitteeDO committee = resolveCommitteeAssignee(conferenceId, command);
        String assigneeEmail = committee.getEmail().trim().toLowerCase();
        String assigneeName = StringUtils.hasText(committee.getName()) ? committee.getName().trim() : assigneeEmail;
        String roleCode = StringUtils.hasText(committee.getRole()) ? committee.getRole().trim().toUpperCase() : null;

        assignmentMapper.insert(ConfTaskAssignmentDO.builder()
                .taskInstanceId(task.getId())
                .conferenceId(conferenceId)
                .assigneeType("USER")
                .assigneeUserId(assigneeEmail)
                .assigneeUserName(assigneeName)
                .assigneeRoleCode(roleCode)
                .status("ASSIGNED")
                .assignedBy(currentUserId)
                .assignedAt(new Date())
                .createdAt(new Date())
                .updatedAt(new Date())
                .build());

        task.setCurrentHandlerId(assigneeEmail);
        task.setCurrentHandlerName(assigneeName);
        task.setUpdatedAt(new Date());
        taskMapper.updateById(task);
        writeLog(task, currentUserId, "TRANSFER", command == null ? null : command.getComment(), task.getStatus(), task.getStatus());
    }

    @Override
    public void rejectTask(Long conferenceId, Long taskInstanceId, String currentUserId, TaskActionReqDTO command) {
        changeStatus(conferenceId, taskInstanceId, currentUserId, "REJECTED", "REJECT", command);
    }

    @Override
    public void cancelTask(Long conferenceId, Long taskInstanceId, String currentUserId, TaskActionReqDTO command) {
        memberRoleService.requireOrganizer(conferenceId, currentUserId);
        changeStatus(conferenceId, taskInstanceId, currentUserId, "CANCELLED", "CANCEL", command);
    }

    @Override
    public void addComment(Long conferenceId, Long taskInstanceId, String currentUserId, TaskActionReqDTO command) {
        ConfTaskInstanceDO task = requireVisibleTask(conferenceId, taskInstanceId, currentUserId);
        writeLog(task, currentUserId, "COMMENT", command == null ? null : command.getComment(), task.getStatus(), task.getStatus());
    }

    @Override
    public List<TaskActionLogRespDTO> listTaskLogs(Long conferenceId, Long taskInstanceId, String currentUserId) {
        requireVisibleTask(conferenceId, taskInstanceId, currentUserId);
        return actionLogMapper.selectList(Wrappers.lambdaQuery(ConfTaskActionLogDO.class)
                        .eq(ConfTaskActionLogDO::getConferenceId, conferenceId)
                        .eq(ConfTaskActionLogDO::getTaskInstanceId, taskInstanceId)
                        .orderByAsc(ConfTaskActionLogDO::getCreatedAt))
                .stream().map(this::toLogResp).toList();
    }

    private TaskCompleteRespDTO completeGeneratedTask(Long conferenceId,
                                                      ConfTaskDO task,
                                                      String currentUserId,
                                                      TaskCompleteReqDTO command) {
        requireConferenceForGeneratedTasks(conferenceId);
        validateGeneratedTaskCompletionPermission(conferenceId, task, currentUserId);
        validateGeneratedTaskStatus(task);
        validateGeneratedTaskCompletionCondition(conferenceId, task);

        String oldStatus = task.getTaskStatus();
        Date now = new Date();
        Long operatorId = parseLongSafely(currentUserId);
        String operatorName = resolveOperatorName();
        ConfTaskDO update = new ConfTaskDO();
        update.setTaskStatus(ConferenceTaskConstant.TASK_STATUS_COMPLETED);
        update.setCompletionDesc(command == null ? null : command.getCompletionDesc());
        update.setCompletionUrl(command == null ? null : command.getCompletionUrl());
        update.setCompletedBy(operatorId);
        update.setCompletedByName(operatorName);
        update.setSubmittedAt(now);
        update.setActualEndTime(now);
        if (task.getActualStartTime() == null) {
            update.setActualStartTime(now);
            task.setActualStartTime(now);
        }
        update.setUpdateUser(operatorId);
        update.setUpdateTime(now);
        int updatedRows = conferenceTaskMapper.update(update, Wrappers.lambdaUpdate(ConfTaskDO.class)
                .eq(ConfTaskDO::getId, task.getId())
                .eq(ConfTaskDO::getConferenceId, conferenceId));
        if (updatedRows < 1) {
            throw new ClientException("Failed to update task completion status");
        }
        task.setTaskStatus(ConferenceTaskConstant.TASK_STATUS_COMPLETED);
        task.setCompletionDesc(command == null ? null : command.getCompletionDesc());
        task.setCompletionUrl(command == null ? null : command.getCompletionUrl());
        task.setCompletedBy(operatorId);
        task.setCompletedByName(operatorName);
        task.setSubmittedAt(now);
        task.setActualEndTime(now);
        task.setUpdateUser(operatorId);
        task.setUpdateTime(now);

        try {
            writeGeneratedTaskLog(conferenceId, task.getId(), ConferenceTaskConstant.TASK_LOG_COMPLETE_TASK,
                    oldStatus, ConferenceTaskConstant.TASK_STATUS_COMPLETED, command == null ? null : command.getCompletionDesc(),
                    operatorId, operatorName, now);
        } catch (Exception ex) {
            log.warn("Write generated task completion log failed, conferenceId={}, taskId={}, message={}",
                    conferenceId, task.getId(), ex.getMessage());
        }

        StageProgressResult progressResult;
        try {
            progressResult = recalculateStageProgress(conferenceId, task.getStageId(), operatorId, now);
        } catch (Exception ex) {
            log.warn("Recalculate stage progress failed after task completion, conferenceId={}, stageId={}, message={}",
                    conferenceId, task.getStageId(), ex.getMessage());
            progressResult = new StageProgressResult(BigDecimal.ZERO, Boolean.FALSE);
        }
        evictGeneratedTaskCacheAfterCommit(conferenceId);
        return TaskCompleteRespDTO.builder()
                .taskId(task.getId())
                .taskStatus(task.getTaskStatus())
                .completionDesc(task.getCompletionDesc())
                .completedBy(task.getCompletedBy())
                .completedByName(task.getCompletedByName())
                .submittedAt(task.getSubmittedAt())
                .actualEndTime(task.getActualEndTime())
                .stageId(task.getStageId())
                .stageProgress(progressResult.progress())
                .coreTasksCompleted(progressResult.coreTasksCompleted())
                .build();
    }

    private boolean insertOrUpdateTask(Long conferenceId,
                                       ConfStageDO stage,
                                       ConfTaskDefDO taskDef,
                                       Date[] plannedTime,
                                       Long operatorId,
                                       Date now) {
        ConfTaskDO existing = conferenceTaskMapper.selectOne(Wrappers.lambdaQuery(ConfTaskDO.class)
                .eq(ConfTaskDO::getConferenceId, conferenceId)
                .eq(ConfTaskDO::getTaskCode, taskDef.getTaskCode())
                .last("limit 1"));
        String principalRole = StringUtils.hasText(taskDef.getDefaultRole())
                ? taskDef.getDefaultRole()
                : taskDef.getDefaultAssigneeRole();
        String completionType = StringUtils.hasText(taskDef.getCompletionType())
                ? taskDef.getCompletionType()
                : ConferenceTaskConstant.COMPLETION_TYPE_MANUAL_CONFIRM;
        if (existing == null) {
            ConfTaskDO task = ConfTaskDO.builder()
                    .conferenceId(conferenceId)
                    .stageId(stage.getId())
                    .taskDefId(taskDef.getId())
                    .stageCode(stage.getStageCode())
                    .taskCode(taskDef.getTaskCode())
                    .taskName(taskDef.getTaskName())
                    .taskDesc(taskDef.getTaskDesc())
                    .taskType(taskDef.getTaskType())
                    .principalRole(principalRole)
                    .plannedStartTime(plannedTime[0])
                    .plannedEndTime(plannedTime[1])
                    .taskStatus("NOT_STARTED")
                    .priority("MEDIUM")
                    .riskLevel("NORMAL")
                    .isCore(defaultFlag(taskDef.getIsCore()))
                    .completionType(completionType)
                    .needReview(defaultFlag(taskDef.getNeedReview()))
                    .sortOrder(taskDef.getSortOrder() == null ? 0 : taskDef.getSortOrder())
                    .createUser(operatorId)
                    .updateUser(operatorId)
                    .createTime(now)
                    .updateTime(now)
                    .build();
            conferenceTaskMapper.insert(task);
            return true;
        }

        ConfTaskDO update = new ConfTaskDO();
        update.setStageId(stage.getId());
        update.setTaskDefId(taskDef.getId());
        update.setStageCode(stage.getStageCode());
        update.setTaskName(taskDef.getTaskName());
        update.setTaskDesc(taskDef.getTaskDesc());
        update.setTaskType(taskDef.getTaskType());
        update.setPrincipalRole(principalRole);
        update.setPlannedStartTime(plannedTime[0]);
        update.setPlannedEndTime(plannedTime[1]);
        update.setIsCore(defaultFlag(taskDef.getIsCore()));
        update.setCompletionType(completionType);
        update.setNeedReview(defaultFlag(taskDef.getNeedReview()));
        update.setSortOrder(taskDef.getSortOrder() == null ? 0 : taskDef.getSortOrder());
        update.setUpdateUser(operatorId);
        update.setUpdateTime(now);
        conferenceTaskMapper.update(update, Wrappers.lambdaUpdate(ConfTaskDO.class)
                .eq(ConfTaskDO::getId, existing.getId()));
        return false;
    }

    private List<ConferenceTaskRespDTO> listGeneratedTasksByConference(Long conferenceId, List<ConfStageDO> stages) {
        Map<Long, Integer> stageOrderMap = stages.stream()
                .collect(Collectors.toMap(ConfStageDO::getId, item -> item.getStageOrder() == null ? Integer.MAX_VALUE : item.getStageOrder(), (left, right) -> left));
        return conferenceTaskMapper.selectList(Wrappers.lambdaQuery(ConfTaskDO.class)
                        .eq(ConfTaskDO::getConferenceId, conferenceId))
                .stream()
                .sorted(Comparator
                        .comparing((ConfTaskDO item) -> stageOrderMap.getOrDefault(item.getStageId(), Integer.MAX_VALUE))
                        .thenComparing(item -> item.getSortOrder() == null ? Integer.MAX_VALUE : item.getSortOrder())
                        .thenComparing(ConfTaskDO::getId))
                .map(this::toGeneratedTaskResp)
                .toList();
    }

    private ConferenceDO requireConferenceForGeneratedTasks(Long conferenceId) {
        ConferenceDO conference = conferenceMapper.selectOne(Wrappers.lambdaQuery(ConferenceDO.class)
                .eq(ConferenceDO::getId, conferenceId)
                .eq(ConferenceDO::getDelFlag, 0)
                .last("limit 1"));
        if (conference == null) {
            throw new ClientException("Conference not found");
        }
        conferencePermissionService.requireAtLeastRole(conferenceId, ConferenceRole.COMMITTEE);
        return conference;
    }

    private ConferenceDO requireConferenceTaskViewPermission(Long conferenceId, String currentUserId) {
        ConferenceDO conference = conferenceMapper.selectOne(Wrappers.lambdaQuery(ConferenceDO.class)
                .eq(ConferenceDO::getId, conferenceId)
                .eq(ConferenceDO::getDelFlag, 0)
                .last("limit 1"));
        if (conference == null) {
            throw new ClientException("Conference not found");
        }
        memberRoleService.requireMember(conferenceId, currentUserId);
        return conference;
    }

    private List<ConfStageDO> loadConferenceStages(Long conferenceId) {
        List<ConfStageDO> stages = stageMapper.selectList(Wrappers.lambdaQuery(ConfStageDO.class)
                .eq(ConfStageDO::getConferenceId, conferenceId)
                .orderByAsc(ConfStageDO::getStageOrder));
        if (stages.isEmpty()) {
            throw new ClientException("Conference stage instances have not been generated");
        }
        return stages;
    }

    private List<ConfStageDefDO> loadEnabledStageDefs() {
        return stageDefMapper.selectList(Wrappers.lambdaQuery(ConfStageDefDO.class)
                .eq(ConfStageDefDO::getStatus, 1)
                .orderByAsc(ConfStageDefDO::getStageOrder)
                .orderByAsc(ConfStageDefDO::getId));
    }

    private List<ConfTaskDefDO> loadEnabledTaskDefs(List<ConfStageDefDO> stageDefs) {
        Map<Long, ConfStageDefDO> stageDefMap = stageDefs.stream()
                .collect(Collectors.toMap(ConfStageDefDO::getId, item -> item, (left, right) -> left));
        List<ConfTaskDefDO> taskDefs = taskDefMapper.selectList(Wrappers.lambdaQuery(ConfTaskDefDO.class)
                .eq(ConfTaskDefDO::getStatus, 1)
                .in(!stageDefMap.isEmpty(), ConfTaskDefDO::getStageDefId, stageDefMap.keySet())
                .orderByAsc(ConfTaskDefDO::getSortOrder)
                .orderByAsc(ConfTaskDefDO::getId));
        if (taskDefs.isEmpty()) {
            throw new ClientException("No enabled task templates found");
        }
        return taskDefs;
    }

    private Map<String, ConfStageDO> buildStageMap(List<ConfStageDO> stages) {
        return stages.stream()
                .collect(Collectors.toMap(ConfStageDO::getStageCode, item -> item, (left, right) -> left));
    }

    private List<ConferenceTaskRespDTO> buildTaskPreview(ConferenceDO conference,
                                                         List<ConfStageDO> stages,
                                                         List<ConfStageDefDO> stageDefs,
                                                         List<ConfTaskDefDO> taskDefs) {
        Map<String, ConfStageDO> stageMap = buildStageMap(stages);
        Map<Long, ConfStageDefDO> stageDefMap = stageDefs.stream()
                .collect(Collectors.toMap(ConfStageDefDO::getId, item -> item, (left, right) -> left));
        Map<Long, Integer> stageOrderMap = stages.stream()
                .collect(Collectors.toMap(ConfStageDO::getId, item -> item.getStageOrder() == null ? Integer.MAX_VALUE : item.getStageOrder(), (left, right) -> left));
        List<ConferenceTaskRespDTO> preview = new ArrayList<>();
        for (ConfTaskDefDO taskDef : taskDefs) {
            ConfStageDefDO stageDef = stageDefMap.get(taskDef.getStageDefId());
            if (stageDef == null) {
                throw new ClientException("Task template cannot match enabled stage template, taskCode=" + taskDef.getTaskCode());
            }
            ConfStageDO stage = stageMap.get(stageDef.getStageCode());
            if (stage == null) {
                throw new ClientException("Conference stage instance missing for stageCode=" + stageDef.getStageCode());
            }
            Date[] plannedTime = calculateTaskPlannedTime(conference, stage, taskDef);
            preview.add(ConferenceTaskRespDTO.builder()
                    .conferenceId(conference.getId())
                    .stageId(stage.getId())
                    .stageCode(stage.getStageCode())
                    .taskCode(taskDef.getTaskCode())
                    .taskName(taskDef.getTaskName())
                    .taskDesc(taskDef.getTaskDesc())
                    .taskType(taskDef.getTaskType())
                    .completionType(StringUtils.hasText(taskDef.getCompletionType()) ? taskDef.getCompletionType() : ConferenceTaskConstant.COMPLETION_TYPE_MANUAL_CONFIRM)
                    .principalRole(StringUtils.hasText(taskDef.getDefaultRole()) ? taskDef.getDefaultRole() : taskDef.getDefaultAssigneeRole())
                    .plannedStartTime(plannedTime[0])
                    .plannedEndTime(plannedTime[1])
                    .taskStatus(resolvePreviewTaskStatus(stage))
                    .status(resolvePreviewTaskStatus(stage))
                    .priority("MEDIUM")
                    .riskLevel("NORMAL")
                    .isCore(defaultFlag(taskDef.getIsCore()))
                    .needReview(defaultFlag(taskDef.getNeedReview()))
                    .sortOrder(taskDef.getSortOrder() == null ? 0 : taskDef.getSortOrder())
                    .build());
        }
        return preview.stream()
                .sorted(Comparator
                        .comparing((ConferenceTaskRespDTO item) -> stageOrderMap.getOrDefault(item.getStageId(), Integer.MAX_VALUE))
                        .thenComparing(item -> item.getSortOrder() == null ? Integer.MAX_VALUE : item.getSortOrder())
                        .thenComparing(ConferenceTaskRespDTO::getTaskCode))
                .toList();
    }

    private String resolvePreviewTaskStatus(ConfStageDO stage) {
        Date now = new Date();
        if ("CONFERENCE_STARTUP".equals(stage.getStageCode())
                && stage.getPlannedStartTime() != null
                && !now.before(stage.getPlannedStartTime())) {
            return "IN_PROGRESS";
        }
        return "NOT_STARTED";
    }

    private boolean syncAutoInProgressTasks(Long conferenceId, List<ConfStageDO> stages, Date now) {
        ConfStageDO startupStage = stages.stream()
                .filter(item -> "CONFERENCE_STARTUP".equals(item.getStageCode()))
                .findFirst()
                .orElse(null);
        if (startupStage == null || startupStage.getPlannedStartTime() == null || now.before(startupStage.getPlannedStartTime())) {
            return false;
        }
        List<ConfTaskDO> startupTasks = conferenceTaskMapper.selectList(Wrappers.lambdaQuery(ConfTaskDO.class)
                .eq(ConfTaskDO::getConferenceId, conferenceId)
                .eq(ConfTaskDO::getStageId, startupStage.getId())
                .eq(ConfTaskDO::getTaskStatus, "NOT_STARTED"));
        boolean changed = false;
        for (ConfTaskDO task : startupTasks) {
            task.setTaskStatus("IN_PROGRESS");
            if (task.getActualStartTime() == null) {
                task.setActualStartTime(now);
            }
            task.setUpdateTime(now);
            conferenceTaskMapper.updateById(task);
            changed = true;
        }
        return changed;
    }

    private List<ConferenceTaskRespDTO> getGeneratedTasksFromCache(Long conferenceId) {
        String cacheKey = buildGeneratedTaskCacheKey(conferenceId);
        try {
            String cachedValue = stringRedisTemplate.opsForValue().get(cacheKey);
            if (!StringUtils.hasText(cachedValue)) {
                return null;
            }
            return JSON.parseObject(cachedValue, new TypeReference<List<ConferenceTaskRespDTO>>() {
            });
        } catch (Exception ex) {
            log.warn("Read conference task cache failed, conferenceId={}, message={}", conferenceId, ex.getMessage());
            stringRedisTemplate.delete(cacheKey);
            return null;
        }
    }

    private void putGeneratedTasksToCache(Long conferenceId, List<ConferenceTaskRespDTO> tasks) {
        try {
            stringRedisTemplate.opsForValue().set(
                    buildGeneratedTaskCacheKey(conferenceId),
                    JSON.toJSONString(tasks),
                    TASK_CACHE_TTL_MINUTES,
                    TimeUnit.MINUTES
            );
        } catch (Exception ex) {
            log.warn("Write conference task cache failed, conferenceId={}, message={}", conferenceId, ex.getMessage());
        }
    }

    private void evictGeneratedTaskCache(Long conferenceId) {
        try {
            stringRedisTemplate.delete(buildGeneratedTaskCacheKey(conferenceId));
        } catch (Exception ex) {
            log.warn("Delete conference task cache failed, conferenceId={}, message={}", conferenceId, ex.getMessage());
        }
    }

    private String buildGeneratedTaskCacheKey(Long conferenceId) {
        return RedisCacheConstant.CONFERENCE_TASK_LIST_KEY + conferenceId;
    }

    private Date[] calculateTaskPlannedTime(ConferenceDO conference, ConfStageDO stage, ConfTaskDefDO taskDef) {
        Date stageStart = stage.getPlannedStartTime();
        Date stageEnd = stage.getPlannedEndTime();
        if (stageStart == null || stageEnd == null) {
            throw new ClientException("Stage planned time is missing, stageCode=" + stage.getStageCode());
        }
        if (!StringUtils.hasText(taskDef.getOffsetBase())) {
            return new Date[]{stageStart, stageEnd};
        }
        Date baseTime = resolveBaseTime(conference, stage, taskDef.getOffsetBase());
        if (baseTime == null) {
            return new Date[]{stageStart, stageEnd};
        }
        Date plannedStart = taskDef.getStartOffsetDays() == null ? stageStart : plusDays(baseTime, taskDef.getStartOffsetDays());
        Date plannedEnd = taskDef.getEndOffsetDays() == null ? stageEnd : plusDays(baseTime, taskDef.getEndOffsetDays());
        if (plannedStart.after(plannedEnd)) {
            log.warn("Task planned time fallback to stage time, conferenceId={}, taskCode={}, stageCode={}",
                    conference.getId(), taskDef.getTaskCode(), stage.getStageCode());
            return new Date[]{stageStart, stageEnd};
        }
        return new Date[]{plannedStart, plannedEnd};
    }

    private Date resolveBaseTime(ConferenceDO conference, ConfStageDO stage, String offsetBase) {
        return switch (offsetBase) {
            case "CONFERENCE_CREATE_TIME" -> conference.getCreateTime();
            case "PAPER_SUBMISSION_DEADLINE" -> conference.getPaperSubmissionDeadline();
            case "NOTIFICATION_OF_ACCEPTANCE" -> conference.getNotificationOfAcceptance();
            case "CAMERA_READY_SUBMISSION" -> conference.getCameraReadySubmission();
            case "EARLY_BIRD_REGISTRATION" -> conference.getEarlyBirdRegistration();
            case "CONFERENCE_START_DATE" -> conference.getConferenceStartDate();
            case "CONFERENCE_END_DATE" -> conference.getConferenceEndDate();
            case "STAGE_START_TIME" -> stage.getPlannedStartTime();
            case "STAGE_END_TIME" -> stage.getPlannedEndTime();
            default -> null;
        };
    }

    private ConfTaskDO requireGeneratedTask(Long conferenceId, Long taskId) {
        ConfTaskDO task = conferenceTaskMapper.selectOne(Wrappers.lambdaQuery(ConfTaskDO.class)
                .eq(ConfTaskDO::getId, taskId)
                .eq(ConfTaskDO::getConferenceId, conferenceId)
                .last("limit 1"));
        if (task == null) {
            throw new ClientException("Task not found");
        }
        return task;
    }

    private void validateGeneratedTaskCompletionPermission(Long conferenceId, ConfTaskDO task, String currentUserId) {
        if (memberRoleService.isOrganizer(conferenceId, currentUserId)) {
            return;
        }
        Long currentUserIdValue = parseLongSafely(currentUserId);
        if (currentUserIdValue != null && currentUserIdValue.equals(task.getPrincipalUserId())) {
            return;
        }
        throw new ClientException("You do not have permission to complete this task");
    }

    private void validateGeneratedTaskViewPermission(Long conferenceId, ConfTaskDO task, String currentUserId) {
        if (memberRoleService.isOrganizer(conferenceId, currentUserId)) {
            return;
        }
        Long currentUserIdValue = parseLongSafely(currentUserId);
        if (currentUserIdValue != null && currentUserIdValue.equals(task.getPrincipalUserId())) {
            return;
        }
        throw new ClientException("无权查看任务");
    }

    private void validateGeneratedTaskStatus(ConfTaskDO task) {
        String status = task.getTaskStatus();
        if (ConferenceTaskConstant.TASK_STATUS_COMPLETED.equals(status)) {
            throw new ClientException("Task is already completed");
        }
        if (ConferenceTaskConstant.TASK_STATUS_CANCELLED.equals(status)) {
            throw new ClientException("Cancelled task cannot be completed");
        }
        if (!Set.of(
                ConferenceTaskConstant.TASK_STATUS_NOT_STARTED,
                ConferenceTaskConstant.TASK_STATUS_IN_PROGRESS,
                ConferenceTaskConstant.TASK_STATUS_REJECTED
        ).contains(status)) {
            throw new ClientException("Current task status does not allow completion");
        }
    }

    private void validateGeneratedTaskCompletionCondition(Long conferenceId, ConfTaskDO task) {
        String completionType = resolveCompletionType(task);
        if (ConferenceTaskConstant.COMPLETION_TYPE_MANUAL_CONFIRM.equals(completionType)) {
            return;
        }
        if (ConferenceTaskConstant.COMPLETION_TYPE_FILE_UPLOAD.equals(completionType)) {
            if (ConferenceTaskConstant.TASK_CODE_IMPORT_POTENTIAL_AUTHOR_LIST.equals(task.getTaskCode())) {
                long importSuccessCount = taskAttachmentMapper.selectCount(Wrappers.lambdaQuery(ConfTaskAttachmentDO.class)
                        .eq(ConfTaskAttachmentDO::getConferenceId, conferenceId)
                        .eq(ConfTaskAttachmentDO::getTaskId, task.getId())
                        .eq(ConfTaskAttachmentDO::getProcessType, ConferenceTaskConstant.ATTACHMENT_PROCESS_TYPE_POTENTIAL_AUTHOR_IMPORT)
                        .in(ConfTaskAttachmentDO::getProcessStatus,
                                ConferenceTaskConstant.ATTACHMENT_PROCESS_STATUS_SUCCESS,
                                ConferenceTaskConstant.ATTACHMENT_PROCESS_STATUS_PARTIAL_SUCCESS)
                        .gt(ConfTaskAttachmentDO::getSuccessCount, 0));
                if (importSuccessCount < 1) {
                    throw new ClientException("潜在作者名单尚未成功导入，请先上传并解析有效名单文件。");
                }
                return;
            }
            long attachmentCount = taskAttachmentMapper.selectCount(Wrappers.lambdaQuery(ConfTaskAttachmentDO.class)
                    .eq(ConfTaskAttachmentDO::getConferenceId, conferenceId)
                    .eq(ConfTaskAttachmentDO::getTaskId, task.getId()));
            if (attachmentCount < 1) {
                throw new ClientException("This task requires at least one uploaded file before completion");
            }
            return;
        }
        if (ConferenceTaskConstant.COMPLETION_TYPE_SYSTEM_CHECK.equals(completionType)) {
            TaskSystemCheckRespDTO systemCheck = buildSystemCheckResp(task);
            if (!Boolean.TRUE.equals(systemCheck.getCanComplete())) {
                throw new ClientException(systemCheck.getMessage());
            }
            return;
        }
        throw new ClientException("Unsupported completion type: " + completionType);
    }

    private String resolveCompletionType(ConfTaskDO task) {
        return StringUtils.hasText(task.getCompletionType())
                ? task.getCompletionType()
                : ConferenceTaskConstant.COMPLETION_TYPE_MANUAL_CONFIRM;
    }

    private TaskSystemCheckRespDTO buildSystemCheckResp(ConfTaskDO task) {
        if (!ConferenceTaskConstant.TASK_CODE_CONFIRM_CONFERENCE_COMMITTEE.equals(task.getTaskCode())) {
            throw new ClientException("Unsupported system check task: " + task.getTaskCode());
        }
        List<ConfCommitteeRoleDefDO> requiredRoles = committeeRoleDefMapper.selectList(Wrappers.lambdaQuery(ConfCommitteeRoleDefDO.class)
                .eq(ConfCommitteeRoleDefDO::getStatus, 1)
                .eq(ConfCommitteeRoleDefDO::getIsRequired, 1)
                .orderByAsc(ConfCommitteeRoleDefDO::getSortOrder)
                .orderByAsc(ConfCommitteeRoleDefDO::getId));
        Map<String, ConfCommitteeRoleDefDO> roleMap = requiredRoles.stream()
                .collect(Collectors.toMap(ConfCommitteeRoleDefDO::getRoleCode, item -> item, (left, right) -> left));
        Set<String> activeRoleCodes = memberRoleMapper.selectList(Wrappers.lambdaQuery(ConfMemberRoleDO.class)
                        .select(ConfMemberRoleDO::getRoleCode)
                        .eq(ConfMemberRoleDO::getConferenceId, task.getConferenceId())
                        .eq(ConfMemberRoleDO::getMemberStatus, ConferenceTaskConstant.MEMBER_STATUS_ACTIVE))
                .stream()
                .map(ConfMemberRoleDO::getRoleCode)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(HashSet::new));
        List<TaskSystemCheckRespDTO.MissingRequiredRoleRespDTO> missingRoles = requiredRoles.stream()
                .filter(item -> !activeRoleCodes.contains(item.getRoleCode()))
                .map(item -> TaskSystemCheckRespDTO.MissingRequiredRoleRespDTO.builder()
                        .roleCode(item.getRoleCode())
                        .roleName(item.getRoleName())
                        .build())
                .toList();
        int requiredRoleCount = roleMap.size();
        int acceptedRequiredRoleCount = requiredRoleCount - missingRoles.size();
        boolean canComplete = missingRoles.isEmpty();
        return TaskSystemCheckRespDTO.builder()
                .taskCode(task.getTaskCode())
                .checkType(resolveCompletionType(task))
                .canComplete(canComplete)
                .requiredRoleCount(requiredRoleCount)
                .acceptedRequiredRoleCount(acceptedRequiredRoleCount)
                .missingRequiredRoles(missingRoles)
                .message(canComplete
                        ? "Committee setup requirements are satisfied"
                        : "Still missing required committee roles, task cannot be completed yet")
                .build();
    }

    private StageProgressResult recalculateStageProgress(Long conferenceId, Long stageId, Long operatorId, Date now) {
        List<ConfTaskDO> tasks = conferenceTaskMapper.selectList(Wrappers.lambdaQuery(ConfTaskDO.class)
                .eq(ConfTaskDO::getConferenceId, conferenceId)
                .eq(ConfTaskDO::getStageId, stageId));
        long effectiveTaskCount = tasks.stream()
                .filter(item -> !ConferenceTaskConstant.TASK_STATUS_CANCELLED.equals(item.getTaskStatus()))
                .count();
        long completedTaskCount = tasks.stream()
                .filter(item -> ConferenceTaskConstant.TASK_STATUS_COMPLETED.equals(item.getTaskStatus()))
                .count();
        BigDecimal progress = effectiveTaskCount == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(completedTaskCount)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(effectiveTaskCount), 2, RoundingMode.HALF_UP);
        boolean coreTasksCompleted = tasks.stream()
                .filter(item -> defaultFlag(item.getIsCore()) == 1)
                .filter(item -> !ConferenceTaskConstant.TASK_STATUS_CANCELLED.equals(item.getTaskStatus()))
                .allMatch(item -> ConferenceTaskConstant.TASK_STATUS_COMPLETED.equals(item.getTaskStatus()));
        ConfStageDO update = new ConfStageDO();
        update.setProgress(progress);
        update.setUpdateTime(now);
        stageMapper.update(update, Wrappers.lambdaUpdate(ConfStageDO.class)
                .eq(ConfStageDO::getId, stageId)
                .eq(ConfStageDO::getConferenceId, conferenceId));
        return new StageProgressResult(progress, coreTasksCompleted);
    }

    private void writeGeneratedTaskLog(Long conferenceId,
                                       Long taskId,
                                       String operationType,
                                       String oldValue,
                                       String newValue,
                                       String remark,
                                       Long operatorId,
                                       String operatorName,
                                       Date operationTime) {
        taskLogMapper.insert(ConfTaskLogDO.builder()
                .conferenceId(conferenceId)
                .taskId(taskId)
                .operationType(operationType)
                .oldValue(oldValue)
                .newValue(newValue)
                .operatorId(operatorId)
                .operatorName(operatorName)
                .operationTime(operationTime)
                .remark(remark)
                .build());
    }

    private String resolveOperatorName() {
        return StringUtils.hasText(com.jxl.ai.intelliconf.common.biz.user.UserContext.getRealName())
                ? com.jxl.ai.intelliconf.common.biz.user.UserContext.getRealName()
                : com.jxl.ai.intelliconf.common.biz.user.UserContext.getUsername();
    }

    private Integer defaultFlag(Integer value) {
        return value == null ? 0 : value;
    }

    private Long parseLongSafely(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Date plusDays(Date baseTime, int offsetDays) {
        LocalDateTime localDateTime = LocalDateTime.ofInstant(baseTime.toInstant(), ZoneId.systemDefault()).plusDays(offsetDays);
        return Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
    }

    private void changeStatus(Long conferenceId, Long taskInstanceId, String currentUserId, String status, String action, TaskActionReqDTO command) {
        ConfTaskInstanceDO task = requireVisibleTask(conferenceId, taskInstanceId, currentUserId);
        String before = task.getStatus();
        task.setStatus(status);
        task.setUpdatedAt(new Date());
        taskMapper.updateById(task);
        writeLog(task, currentUserId, action, command == null ? null : command.getComment(), before, status);
    }

    private ConfTaskInstanceDO requireVisibleTask(Long conferenceId, Long taskInstanceId, String currentUserId) {
        if (!visibilityService.canViewTask(conferenceId, taskInstanceId, currentUserId)) {
            throw new ClientException("当前用户无权查看该事务");
        }
        return requireTask(conferenceId, taskInstanceId);
    }

    private ConfTaskInstanceDO requireTask(Long conferenceId, Long taskInstanceId) {
        ConfTaskInstanceDO task = taskMapper.selectById(taskInstanceId);
        if (task == null || !conferenceId.equals(task.getConferenceId())) {
            throw new ClientException("事务不存在");
        }
        return task;
    }

    private ConfCommitteeDO resolveCommitteeAssignee(Long conferenceId, TaskActionReqDTO command) {
        if (command == null) {
            throw new ClientException("请选择要分派的委员会成员");
        }
        ConfCommitteeDO committee = null;
        if (command.getTargetCommitteeId() != null) {
            committee = committeeMapper.selectOne(Wrappers.lambdaQuery(ConfCommitteeDO.class)
                    .eq(ConfCommitteeDO::getId, command.getTargetCommitteeId())
                    .eq(ConfCommitteeDO::getConfId, conferenceId)
                    .eq(ConfCommitteeDO::getDelFlag, 0)
                    .last("limit 1"));
        }
        if (committee == null && StringUtils.hasText(command.getTargetUserId())) {
            committee = committeeMapper.selectOne(Wrappers.lambdaQuery(ConfCommitteeDO.class)
                    .eq(ConfCommitteeDO::getConfId, conferenceId)
                    .eq(ConfCommitteeDO::getEmail, command.getTargetUserId().trim().toLowerCase())
                    .eq(ConfCommitteeDO::getDelFlag, 0)
                    .last("limit 1"));
        }
        if (committee == null) {
            throw new ClientException("目标处理人不是当前会议的委员会成员");
        }
        return committee;
    }

    private void updateAssignments(Long taskId, String status, boolean accepted) {
        List<ConfTaskAssignmentDO> assignments = assignmentMapper.selectList(Wrappers.lambdaQuery(ConfTaskAssignmentDO.class)
                .eq(ConfTaskAssignmentDO::getTaskInstanceId, taskId));
        for (ConfTaskAssignmentDO assignment : assignments) {
            assignment.setStatus(status);
            if (accepted) {
                assignment.setAcceptedAt(new Date());
            }
            if ("COMPLETED".equals(status)) {
                assignment.setCompletedAt(new Date());
            }
            assignment.setUpdatedAt(new Date());
            assignmentMapper.updateById(assignment);
        }
    }

    private void writeLog(ConfTaskInstanceDO task, String userId, String action, String comment, String before, String after) {
        actionLogMapper.insert(ConfTaskActionLogDO.builder()
                .taskInstanceId(task.getId())
                .conferenceId(task.getConferenceId())
                .operatorId(userId)
                .operatorName(userId)
                .operatorRoleCode(memberRoleService.isOrganizer(task.getConferenceId(), userId) ? "ORGANIZER" : null)
                .actionType(action)
                .comment(comment)
                .beforeStatus(before)
                .afterStatus(after)
                .createdAt(new Date())
                .build());
    }

    private ConferenceTaskRespDTO toGeneratedTaskResp(ConfTaskDO task) {
        return ConferenceTaskRespDTO.builder()
                .taskId(task.getId())
                .taskInstanceId(task.getId())
                .conferenceId(task.getConferenceId())
                .stageId(task.getStageId())
                .stageCode(task.getStageCode())
                .taskCode(task.getTaskCode())
                .taskName(task.getTaskName())
                .taskDesc(task.getTaskDesc())
                .taskType(task.getTaskType())
                .completionType(task.getCompletionType())
                .principalRole(task.getPrincipalRole())
                .principalUserId(task.getPrincipalUserId())
                .principalName(task.getPrincipalName())
                .plannedStartTime(task.getPlannedStartTime())
                .plannedEndTime(task.getPlannedEndTime())
                .actualStartTime(task.getActualStartTime())
                .actualEndTime(task.getActualEndTime())
                .taskStatus(task.getTaskStatus())
                .status(task.getTaskStatus())
                .priority(task.getPriority())
                .riskLevel(task.getRiskLevel())
                .isCore(task.getIsCore())
                .needReview(task.getNeedReview())
                .sortOrder(task.getSortOrder())
                .completionDesc(task.getCompletionDesc())
                .completionUrl(task.getCompletionUrl())
                .completedBy(task.getCompletedBy())
                .completedByName(task.getCompletedByName())
                .submittedAt(task.getSubmittedAt())
                .createdAt(task.getCreateTime())
                .updatedAt(task.getUpdateTime())
                .build();
    }

    private List<ConferenceTaskRespDTO> filterGeneratedTasksByVisibility(Long conferenceId,
                                                                         String currentUserId,
                                                                         List<ConferenceTaskRespDTO> tasks) {
        if (memberRoleService.isOrganizer(conferenceId, currentUserId)) {
            return tasks;
        }
        Long currentUserIdValue = parseLongSafely(currentUserId);
        if (currentUserIdValue == null) {
            return List.of();
        }
        return tasks.stream()
                .filter(item -> currentUserIdValue.equals(item.getPrincipalUserId()))
                .toList();
    }

    private ConferenceTaskRespDTO toResp(ConfTaskInstanceDO task) {
        ConfTaskAssignmentDO assignment = assignmentMapper.selectOne(Wrappers.lambdaQuery(ConfTaskAssignmentDO.class)
                .eq(ConfTaskAssignmentDO::getTaskInstanceId, task.getId())
                .orderByDesc(ConfTaskAssignmentDO::getId)
                .last("limit 1"));
        return ConferenceTaskRespDTO.builder()
                .taskInstanceId(task.getId())
                .conferenceId(task.getConferenceId())
                .milestoneId(task.getMilestoneId())
                .nodeCode(task.getNodeCode())
                .taskCode(task.getTaskCode())
                .taskName(task.getTaskName())
                .taskDesc(task.getTaskDesc())
                .taskType(task.getTaskType())
                .priority(task.getPriority())
                .status(task.getStatus())
                .dueTime(task.getDueTime())
                .jumpUrl(task.getJumpUrl())
                .currentHandlerId(task.getCurrentHandlerId())
                .currentHandlerName(task.getCurrentHandlerName())
                .assigneeType(assignment == null ? null : assignment.getAssigneeType())
                .assigneeUserId(assignment == null ? null : assignment.getAssigneeUserId())
                .assigneeUserName(assignment == null ? null : assignment.getAssigneeUserName())
                .assigneeRoleCode(assignment == null ? null : assignment.getAssigneeRoleCode())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }

    private TaskActionLogRespDTO toLogResp(ConfTaskActionLogDO log) {
        return TaskActionLogRespDTO.builder()
                .id(log.getId())
                .taskInstanceId(log.getTaskInstanceId())
                .operatorId(log.getOperatorId())
                .operatorName(log.getOperatorName())
                .operatorRoleCode(log.getOperatorRoleCode())
                .actionType(log.getActionType())
                .comment(log.getComment())
                .beforeStatus(log.getBeforeStatus())
                .afterStatus(log.getAfterStatus())
                .createdAt(log.getCreatedAt())
                .build();
    }

    private record StageProgressResult(BigDecimal progress, boolean coreTasksCompleted) {
    }
}
