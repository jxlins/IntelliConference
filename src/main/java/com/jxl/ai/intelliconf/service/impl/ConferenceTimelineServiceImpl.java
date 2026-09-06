package com.jxl.ai.intelliconf.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jxl.ai.intelliconf.common.convention.exception.ClientException;
import com.jxl.ai.intelliconf.dao.entity.ConfMilestoneDO;
import com.jxl.ai.intelliconf.dao.entity.ConfTaskActionLogDO;
import com.jxl.ai.intelliconf.dao.entity.ConfTaskAssignmentDO;
import com.jxl.ai.intelliconf.dao.entity.ConfTaskDefDO;
import com.jxl.ai.intelliconf.dao.entity.ConfTaskInstanceDO;
import com.jxl.ai.intelliconf.dao.entity.ConferenceDO;
import com.jxl.ai.intelliconf.dao.mapper.ConfMilestoneMapper;
import com.jxl.ai.intelliconf.dao.mapper.ConfTaskActionLogMapper;
import com.jxl.ai.intelliconf.dao.mapper.ConfTaskAssignmentMapper;
import com.jxl.ai.intelliconf.dao.mapper.ConfTaskDefMapper;
import com.jxl.ai.intelliconf.dao.mapper.ConfTaskInstanceMapper;
import com.jxl.ai.intelliconf.dao.mapper.ConferenceMapper;
import com.jxl.ai.intelliconf.dto.resp.TimelineMilestoneRespDTO;
import com.jxl.ai.intelliconf.dto.resp.TimelineOverviewRespDTO;
import com.jxl.ai.intelliconf.enums.MilestoneTemplate;
import com.jxl.ai.intelliconf.service.ConferenceMemberRoleService;
import com.jxl.ai.intelliconf.service.ConferenceTimelineService;
import com.jxl.ai.intelliconf.service.DeadlineRuleService;
import com.jxl.ai.intelliconf.toolkit.MilestoneUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConferenceTimelineServiceImpl implements ConferenceTimelineService {

    private static final Set<String> AUTO_COMPLETED_AFTER_TIMELINE_GENERATED = Set.of(
            "GENERATE_PROCESS_TASK_PLAN",
            "SET_KEY_MILESTONES"
    );

    private final ConferenceMapper conferenceMapper;
    private final ConfMilestoneMapper milestoneMapper;
    private final ConfTaskDefMapper taskDefMapper;
    private final ConfTaskInstanceMapper taskInstanceMapper;
    private final ConfTaskAssignmentMapper assignmentMapper;
    private final ConfTaskActionLogMapper actionLogMapper;
    private final MilestoneUtil milestoneUtil;
    private final DeadlineRuleService deadlineRuleService;
    private final ConferenceMemberRoleService memberRoleService;

    @Override
    @Transactional
    public void initTimelineForConference(Long conferenceId) {
        ConferenceDO conference = conferenceMapper.selectById(conferenceId);
        if (conference == null) {
            throw new ClientException("会议不存在");
        }
        Long existingCount = milestoneMapper.selectCount(Wrappers.lambdaQuery(ConfMilestoneDO.class)
                .eq(ConfMilestoneDO::getConfereId, conferenceId));
        if (existingCount != null && existingCount > 0) {
            ConfMilestoneDO current = getCurrentMilestone(conferenceId);
            if (current != null && Integer.valueOf(1).equals(current.getAutoGenerateTasks())) {
                generateTasksForMilestone(conferenceId, current.getId(), conference.getCreateUser());
            }
            return;
        }

        List<ConfMilestoneDO> milestones = milestoneUtil.generateDefaultTimeline(conferenceId, conference.getStartTime());
        for (ConfMilestoneDO milestone : milestones) {
            if (MilestoneTemplate.INITIATION.getCode().equals(milestone.getNodeCode())) {
                milestone.setStatus(1);
                milestone.setStartDate(milestone.getStartDate() == null ? new Date() : milestone.getStartDate());
            } else {
                milestone.setStatus(0);
            }
            milestoneMapper.insert(milestone);
        }
        ConfMilestoneDO first = milestoneMapper.selectOne(Wrappers.lambdaQuery(ConfMilestoneDO.class)
                .eq(ConfMilestoneDO::getConfereId, conferenceId)
                .eq(ConfMilestoneDO::getNodeCode, MilestoneTemplate.INITIATION.getCode())
                .last("limit 1"));
        if (first != null) {
            writeLog(null, conferenceId, conference.getCreateUser(), conference.getCreateUser(), "ORGANIZER",
                    "MILESTONE_START", "会议创建后自动启动 INITIATION", null, "PROCESSING");
            generateTasksForMilestone(conferenceId, first.getId(), conference.getCreateUser());
            autoCompleteTimelineGeneratedTasks(conferenceId, first.getId(), conference.getCreateUser());
        }
    }

    @Override
    public TimelineOverviewRespDTO getTimeline(Long conferenceId) {
        List<ConfMilestoneDO> milestones = listMilestones(conferenceId);
        List<ConfTaskInstanceDO> tasks = taskInstanceMapper.selectList(Wrappers.lambdaQuery(ConfTaskInstanceDO.class)
                .eq(ConfTaskInstanceDO::getConferenceId, conferenceId));
        Map<Long, List<ConfTaskInstanceDO>> taskMap = tasks.stream().collect(Collectors.groupingBy(ConfTaskInstanceDO::getMilestoneId));
        ConfMilestoneDO current = milestones.stream().filter(m -> Integer.valueOf(1).equals(m.getStatus())).findFirst().orElse(null);
        return TimelineOverviewRespDTO.builder()
                .conferenceId(conferenceId)
                .currentNodeCode(current == null ? null : current.getNodeCode())
                .currentNodeName(current == null ? null : current.getNodeName())
                .overallStatus(resolveOverallStatus(milestones))
                .milestones(milestones.stream().map(m -> toMilestoneResp(m, taskMap.getOrDefault(m.getId(), List.of()))).toList())
                .build();
    }

    @Override
    @Transactional
    public ConfMilestoneDO startMilestone(Long conferenceId, String nodeCode, String currentUserId) {
        memberRoleService.requireOrganizer(conferenceId, currentUserId);
        ConfMilestoneDO milestone = requireMilestone(conferenceId, nodeCode);
        if (Integer.valueOf(2).equals(milestone.getStatus()) || Integer.valueOf(3).equals(milestone.getStatus())) {
            throw new ClientException("当前阶段已结束，不能重新启动");
        }
        milestone.setStatus(1);
        if (milestone.getStartDate() == null) {
            milestone.setStartDate(new Date());
        }
        milestoneMapper.updateById(milestone);
        writeLog(null, conferenceId, currentUserId, currentUserId, "ORGANIZER", "MILESTONE_START", nodeCode, null, "PROCESSING");
        if (Integer.valueOf(1).equals(milestone.getAutoGenerateTasks())) {
            generateTasksForMilestone(conferenceId, milestone.getId(), currentUserId);
        }
        return milestone;
    }

    @Override
    @Transactional
    public int generateTasksForMilestone(Long conferenceId, Long milestoneId, String currentUserId) {
        ConfMilestoneDO milestone = milestoneMapper.selectById(milestoneId);
        if (milestone == null || !conferenceId.equals(milestone.getConfereId())) {
            throw new ClientException("阶段不存在");
        }
        List<ConfTaskDefDO> defs = taskDefMapper.selectList(Wrappers.lambdaQuery(ConfTaskDefDO.class)
                .eq(ConfTaskDefDO::getNodeCode, milestone.getNodeCode())
                .eq(ConfTaskDefDO::getStatus, 1)
                .orderByAsc(ConfTaskDefDO::getSortOrder)
                .orderByAsc(ConfTaskDefDO::getPriority));
        int created = 0;
        for (ConfTaskDefDO def : defs) {
            if (createTaskInstance(conferenceId, milestone, def, currentUserId)) {
                created++;
            }
        }
        autoCompleteTimelineGeneratedTasks(conferenceId, milestoneId, currentUserId);
        return created;
    }

    @Override
    @Transactional
    public void completeMilestone(Long conferenceId, String nodeCode, String currentUserId) {
        memberRoleService.requireOrganizer(conferenceId, currentUserId);
        ConfMilestoneDO milestone = requireMilestone(conferenceId, nodeCode);
        if (!checkMilestoneCompletion(conferenceId, nodeCode)) {
            throw new ClientException("当前阶段仍有未完成事务，不能完成该阶段");
        }
        milestone.setStatus(2);
        milestone.setActualEndDate(new Date());
        milestone.setIsConfirmed(1);
        milestoneMapper.updateById(milestone);
        writeLog(null, conferenceId, currentUserId, currentUserId, "ORGANIZER", "MILESTONE_COMPLETE", nodeCode, "PROCESSING", "COMPLETED");
        moveToNextMilestone(conferenceId, nodeCode, currentUserId);
    }

    @Override
    @Transactional
    public void moveToNextMilestone(Long conferenceId, String currentNodeCode, String currentUserId) {
        ConfMilestoneDO current = requireMilestone(conferenceId, currentNodeCode);
        ConfMilestoneDO next = milestoneMapper.selectOne(Wrappers.lambdaQuery(ConfMilestoneDO.class)
                .eq(ConfMilestoneDO::getConfereId, conferenceId)
                .gt(ConfMilestoneDO::getSortOrder, current.getSortOrder() == null ? 0 : current.getSortOrder())
                .orderByAsc(ConfMilestoneDO::getSortOrder)
                .last("limit 1"));
        if (next == null) {
            finishConference(conferenceId, currentUserId);
            return;
        }
        next.setStatus(1);
        if (next.getStartDate() == null) {
            next.setStartDate(new Date());
        }
        milestoneMapper.updateById(next);
        writeLog(null, conferenceId, currentUserId, currentUserId, "ORGANIZER", "MILESTONE_START", next.getNodeCode(), null, "PROCESSING");
        if (Integer.valueOf(1).equals(next.getAutoGenerateTasks())) {
            generateTasksForMilestone(conferenceId, next.getId(), currentUserId);
        }
    }

    @Override
    public boolean checkMilestoneCompletion(Long conferenceId, String nodeCode) {
        ConfMilestoneDO milestone = requireMilestone(conferenceId, nodeCode);
        Long unfinished = taskInstanceMapper.selectCount(Wrappers.lambdaQuery(ConfTaskInstanceDO.class)
                .eq(ConfTaskInstanceDO::getConferenceId, conferenceId)
                .eq(ConfTaskInstanceDO::getMilestoneId, milestone.getId())
                .in(ConfTaskInstanceDO::getStatus, List.of("PENDING", "PROCESSING", "REJECTED", "OVERDUE")));
        return unfinished == null || unfinished == 0;
    }

    @Override
    public ConfMilestoneDO getCurrentMilestone(Long conferenceId) {
        return milestoneMapper.selectOne(Wrappers.lambdaQuery(ConfMilestoneDO.class)
                .eq(ConfMilestoneDO::getConfereId, conferenceId)
                .eq(ConfMilestoneDO::getStatus, 1)
                .orderByAsc(ConfMilestoneDO::getSortOrder)
                .last("limit 1"));
    }

    @Override
    public void finishConference(Long conferenceId, String currentUserId) {
        ConferenceDO conference = conferenceMapper.selectById(conferenceId);
        if (conference != null) {
            conference.setCurrentState("ARCHIVED");
            conferenceMapper.updateById(conference);
        }
    }

    @Override
    @Transactional
    public void skipMilestone(Long conferenceId, String nodeCode, String currentUserId, String reason) {
        memberRoleService.requireOrganizer(conferenceId, currentUserId);
        ConfMilestoneDO milestone = requireMilestone(conferenceId, nodeCode);
        if (Integer.valueOf(1).equals(milestone.getIsRequired())) {
            throw new ClientException("必经阶段不允许跳过");
        }
        milestone.setStatus(3);
        milestone.setRemark(reason);
        milestoneMapper.updateById(milestone);
        writeLog(null, conferenceId, currentUserId, currentUserId, "ORGANIZER", "SKIP", reason, null, "SKIPPED");
        moveToNextMilestone(conferenceId, nodeCode, currentUserId);
    }

    private boolean createTaskInstance(Long conferenceId, ConfMilestoneDO milestone, ConfTaskDefDO def, String currentUserId) {
        ConfTaskInstanceDO task = ConfTaskInstanceDO.builder()
                .conferenceId(conferenceId)
                .milestoneId(milestone.getId())
                .taskDefId(def.getId())
                .nodeCode(def.getNodeCode())
                .taskCode(StringUtils.hasText(def.getTaskCode()) ? def.getTaskCode() : def.getNodeCode() + "_" + def.getId())
                .taskName(def.getTaskName())
                .taskType(StringUtils.hasText(def.getTaskType()) ? def.getTaskType() : inferTaskType(def.getHandlerBean()))
                .handlerBean(def.getHandlerBean())
                .params(def.getParams())
                .priority(convertPriority(def.getPriority()))
                .status("PENDING")
                .dueTime(deadlineRuleService.calculateDueTime(conferenceId, milestone, def.getDeadlineRule()))
                .jumpUrl(def.getJumpUrl())
                .createdBy(currentUserId)
                .createdAt(new Date())
                .updatedAt(new Date())
                .build();
        try {
            taskInstanceMapper.insert(task);
        } catch (DuplicateKeyException ex) {
            return false;
        }
        writeLog(task.getId(), conferenceId, currentUserId, currentUserId, null, "CREATE", task.getTaskName(), null, "PENDING");
        createAssignment(task, def, currentUserId);
        return true;
    }

    private void createAssignment(ConfTaskInstanceDO task, ConfTaskDefDO def, String currentUserId) {
        String assigneeType = StringUtils.hasText(def.getDefaultAssigneeType()) ? def.getDefaultAssigneeType().trim().toUpperCase() : "ROLE";
        String role = StringUtils.hasText(def.getDefaultAssigneeRole()) ? def.getDefaultAssigneeRole().trim().toUpperCase() : "ORGANIZER";
        if ("USER".equals(assigneeType)) {
            assigneeType = "ROLE";
            role = "ORGANIZER";
        }
        assignmentMapper.insert(ConfTaskAssignmentDO.builder()
                .taskInstanceId(task.getId())
                .conferenceId(task.getConferenceId())
                .assigneeType(assigneeType)
                .assigneeRoleCode(role)
                .status("ASSIGNED")
                .assignedBy(currentUserId)
                .assignedAt(new Date())
                .createdAt(new Date())
                .updatedAt(new Date())
                .build());
        writeLog(task.getId(), task.getConferenceId(), currentUserId, currentUserId, role, "ASSIGN", "分派给 " + role, null, "ASSIGNED");
    }

    private List<ConfMilestoneDO> listMilestones(Long conferenceId) {
        return milestoneMapper.selectList(Wrappers.lambdaQuery(ConfMilestoneDO.class)
                .eq(ConfMilestoneDO::getConfereId, conferenceId)
                .orderByAsc(ConfMilestoneDO::getSortOrder)
                .orderByAsc(ConfMilestoneDO::getStartDate));
    }

    private ConfMilestoneDO requireMilestone(Long conferenceId, String nodeCode) {
        ConfMilestoneDO milestone = milestoneMapper.selectOne(Wrappers.lambdaQuery(ConfMilestoneDO.class)
                .eq(ConfMilestoneDO::getConfereId, conferenceId)
                .eq(ConfMilestoneDO::getNodeCode, nodeCode)
                .last("limit 1"));
        if (milestone == null) {
            throw new ClientException("阶段不存在");
        }
        return milestone;
    }

    private TimelineMilestoneRespDTO toMilestoneResp(ConfMilestoneDO milestone, List<ConfTaskInstanceDO> tasks) {
        int total = tasks.size();
        int completed = (int) tasks.stream().filter(t -> "COMPLETED".equals(t.getStatus())).count();
        int progressPercent = total > 0 ? Math.round(completed * 100f / total) : (Integer.valueOf(2).equals(milestone.getStatus()) ? 100 : 0);
        return TimelineMilestoneRespDTO.builder()
                .id(milestone.getId())
                .nodeCode(milestone.getNodeCode())
                .nodeName(milestone.getNodeName())
                .startDate(milestone.getStartDate())
                .targetEndDate(milestone.getTargetEndDate())
                .actualEndDate(milestone.getActualEndDate())
                .status(milestone.getStatus())
                .isConfirmed(milestone.getIsConfirmed())
                .sortOrder(milestone.getSortOrder())
                .taskTotalCount(total)
                .taskCompletedCount(completed)
                .taskPendingCount((int) tasks.stream().filter(t -> "PENDING".equals(t.getStatus())).count())
                .taskProcessingCount((int) tasks.stream().filter(t -> "PROCESSING".equals(t.getStatus())).count())
                .taskOverdueCount((int) tasks.stream().filter(t -> "OVERDUE".equals(t.getStatus())).count())
                .current(Integer.valueOf(1).equals(milestone.getStatus()))
                .taskProgressPercent(progressPercent)
                .taskProgressText(completed + "/" + total)
                .build();
    }

    private String resolveOverallStatus(List<ConfMilestoneDO> milestones) {
        if (milestones.stream().allMatch(m -> Integer.valueOf(2).equals(m.getStatus()) || Integer.valueOf(3).equals(m.getStatus()))) {
            return "FINISHED";
        }
        return milestones.stream().anyMatch(m -> Integer.valueOf(1).equals(m.getStatus())) ? "RUNNING" : "WAITING";
    }

    private String convertPriority(Integer priority) {
        if (priority == null) {
            return "MEDIUM";
        }
        if (priority <= 1) {
            return "HIGH";
        }
        if (priority == 2) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private String inferTaskType(String handlerBean) {
        if ("emailTaskHandler".equals(handlerBean)) {
            return "EMAIL";
        }
        return "manualTaskHandler".equals(handlerBean) ? "MANUAL" : "SYSTEM";
    }

    private void autoCompleteTimelineGeneratedTasks(Long conferenceId, Long milestoneId, String currentUserId) {
        List<ConfTaskInstanceDO> tasks = taskInstanceMapper.selectList(Wrappers.lambdaQuery(ConfTaskInstanceDO.class)
                .eq(ConfTaskInstanceDO::getConferenceId, conferenceId)
                .eq(ConfTaskInstanceDO::getMilestoneId, milestoneId)
                .in(ConfTaskInstanceDO::getStatus, List.of("PENDING", "PROCESSING")));
        for (ConfTaskInstanceDO task : tasks) {
            if (!isTimelineGeneratedAutoTask(task)) {
                continue;
            }
            String before = task.getStatus();
            Date now = new Date();
            task.setStatus("COMPLETED");
            task.setCurrentHandlerId(currentUserId);
            task.setCurrentHandlerName(currentUserId);
            task.setUpdatedAt(now);
            taskInstanceMapper.updateById(task);

            List<ConfTaskAssignmentDO> assignments = assignmentMapper.selectList(Wrappers.lambdaQuery(ConfTaskAssignmentDO.class)
                    .eq(ConfTaskAssignmentDO::getTaskInstanceId, task.getId()));
            for (ConfTaskAssignmentDO assignment : assignments) {
                assignment.setStatus("COMPLETED");
                if (assignment.getAcceptedAt() == null) {
                    assignment.setAcceptedAt(now);
                }
                assignment.setCompletedAt(now);
                assignment.setUpdatedAt(now);
                assignmentMapper.updateById(assignment);
            }
            writeLog(task.getId(), conferenceId, currentUserId, currentUserId, "ORGANIZER",
                    "COMPLETE", "Timeline and task plan generated automatically.", before, "COMPLETED");
        }
    }

    private boolean isTimelineGeneratedAutoTask(ConfTaskInstanceDO task) {
        if (task == null) {
            return false;
        }
        if (AUTO_COMPLETED_AFTER_TIMELINE_GENERATED.contains(task.getTaskCode())) {
            return true;
        }
        String name = task.getTaskName() == null ? "" : task.getTaskName();
        String params = task.getParams() == null ? "" : task.getParams();
        return name.contains("生成会议全过程事务计划")
                || name.contains("设置会议关键时间节点")
                || params.contains("GENERATE_PROCESS_TASK_PLAN")
                || params.contains("SET_KEY_MILESTONES");
    }

    private void writeLog(Long taskId, Long conferenceId, String operatorId, String operatorName, String role,
                          String actionType, String comment, String beforeStatus, String afterStatus) {
        actionLogMapper.insert(ConfTaskActionLogDO.builder()
                .taskInstanceId(taskId)
                .conferenceId(conferenceId)
                .operatorId(operatorId)
                .operatorName(operatorName)
                .operatorRoleCode(role)
                .actionType(actionType)
                .comment(comment)
                .beforeStatus(beforeStatus)
                .afterStatus(afterStatus)
                .createdAt(new Date())
                .build());
    }
}
