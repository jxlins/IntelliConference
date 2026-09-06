package com.jxl.ai.intelliconf.scheduler;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jxl.ai.intelliconf.common.constant.RedisCacheConstant;
import com.jxl.ai.intelliconf.dao.entity.ConfStageDO;
import com.jxl.ai.intelliconf.dao.entity.ConfTaskDO;
import com.jxl.ai.intelliconf.dao.entity.ConfTaskLogDO;
import com.jxl.ai.intelliconf.dao.mapper.ConfStageMapper;
import com.jxl.ai.intelliconf.dao.mapper.ConfTaskLogMapper;
import com.jxl.ai.intelliconf.dao.mapper.ConfTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Activates generated conference tasks and stages when their planned time arrives.
 * Manual tasks become actionable; their business work is never marked completed automatically.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConferenceWorkflowScheduler {

    private final ConfTaskMapper taskMapper;
    private final ConfStageMapper stageMapper;
    private final ConfTaskLogMapper taskLogMapper;
    private final StringRedisTemplate stringRedisTemplate;

    @Scheduled(cron = "15 * * * * ?")
    @Transactional
    public void activateDueWorkflowItems() {
        Date now = new Date();
        Set<Long> changedConferenceIds = activateDueTasks(now);
        changedConferenceIds.addAll(synchronizeStages(now));
        changedConferenceIds.forEach(this::evictConferenceCache);
        if (!changedConferenceIds.isEmpty()) {
            log.info("[ConferenceWorkflowScheduler] synchronized conference workflows, count={}", changedConferenceIds.size());
        }
    }

    private Set<Long> activateDueTasks(Date now) {
        List<ConfTaskDO> dueTasks = taskMapper.selectList(
                Wrappers.lambdaQuery(ConfTaskDO.class)
                        .eq(ConfTaskDO::getTaskStatus, "NOT_STARTED")
                        .le(ConfTaskDO::getPlannedStartTime, now)
        );
        Set<Long> changedConferenceIds = new HashSet<>();
        for (ConfTaskDO task : dueTasks) {
            ConfTaskDO update = new ConfTaskDO();
            update.setTaskStatus("IN_PROGRESS");
            update.setActualStartTime(task.getActualStartTime() == null ? now : task.getActualStartTime());
            update.setRiskLevel(task.getPlannedEndTime() != null && now.after(task.getPlannedEndTime()) ? "OVERDUE" : "NORMAL");
            update.setUpdateTime(now);
            int changed = taskMapper.update(update, Wrappers.lambdaUpdate(ConfTaskDO.class)
                    .eq(ConfTaskDO::getId, task.getId())
                    .eq(ConfTaskDO::getTaskStatus, "NOT_STARTED"));
            if (changed == 0) {
                continue;
            }
            changedConferenceIds.add(task.getConferenceId());
            taskLogMapper.insert(ConfTaskLogDO.builder()
                    .conferenceId(task.getConferenceId())
                    .taskId(task.getId())
                    .operationType("AUTO_TRIGGER")
                    .oldValue("NOT_STARTED")
                    .newValue("IN_PROGRESS")
                    .operatorName("SYSTEM")
                    .operationTime(now)
                    .remark("任务计划开始时间已到，系统自动触发")
                    .build());
        }

        List<ConfTaskDO> overdueTasks = taskMapper.selectList(
                Wrappers.lambdaQuery(ConfTaskDO.class)
                        .in(ConfTaskDO::getTaskStatus, List.of("NOT_STARTED", "IN_PROGRESS", "REJECTED"))
                        .lt(ConfTaskDO::getPlannedEndTime, now)
                        .ne(ConfTaskDO::getRiskLevel, "OVERDUE")
        );
        for (ConfTaskDO task : overdueTasks) {
            ConfTaskDO update = new ConfTaskDO();
            update.setRiskLevel("OVERDUE");
            update.setUpdateTime(now);
            taskMapper.update(update, Wrappers.lambdaUpdate(ConfTaskDO.class).eq(ConfTaskDO::getId, task.getId()));
            changedConferenceIds.add(task.getConferenceId());
        }
        return changedConferenceIds;
    }

    private Set<Long> synchronizeStages(Date now) {
        Set<Long> changedConferenceIds = new HashSet<>();
        List<ConfStageDO> stages = stageMapper.selectList(Wrappers.lambdaQuery(ConfStageDO.class)
                .ne(ConfStageDO::getStageStatus, "CANCELLED"));
        for (ConfStageDO stage : stages) {
            List<ConfTaskDO> tasks = taskMapper.selectList(Wrappers.lambdaQuery(ConfTaskDO.class)
                    .eq(ConfTaskDO::getStageId, stage.getId()));
            int total = tasks.size();
            long completed = tasks.stream()
                    .filter(task -> List.of("COMPLETED", "CANCELLED").contains(task.getTaskStatus()))
                    .count();
            BigDecimal progress = total == 0
                    ? BigDecimal.ZERO
                    : BigDecimal.valueOf(completed * 100D / total).setScale(2, RoundingMode.HALF_UP);
            String nextStatus = resolveStageStatus(stage, total, completed, now);
            boolean changed = !Objects.equals(stage.getStageStatus(), nextStatus)
                    || stage.getProgress() == null
                    || stage.getProgress().compareTo(progress) != 0;
            if (!changed) {
                continue;
            }
            ConfStageDO update = new ConfStageDO();
            update.setStageStatus(nextStatus);
            update.setProgress(progress);
            update.setIsCurrent("IN_PROGRESS".equals(nextStatus) ? 1 : 0);
            if ("IN_PROGRESS".equals(nextStatus) && stage.getActualStartTime() == null) {
                update.setActualStartTime(now);
            }
            if ("COMPLETED".equals(nextStatus) && stage.getActualEndTime() == null) {
                update.setActualEndTime(now);
            }
            update.setUpdateTime(now);
            stageMapper.update(update, Wrappers.lambdaUpdate(ConfStageDO.class).eq(ConfStageDO::getId, stage.getId()));
            changedConferenceIds.add(stage.getConferenceId());
        }
        return changedConferenceIds;
    }

    private String resolveStageStatus(ConfStageDO stage, int total, long completed, Date now) {
        if (total > 0 && completed == total) {
            return "COMPLETED";
        }
        if (stage.getPlannedStartTime() != null && now.before(stage.getPlannedStartTime())) {
            return "PLANNED";
        }
        if (stage.getPlannedEndTime() != null && now.after(stage.getPlannedEndTime())) {
            return "DELAYED";
        }
        if (stage.getPlannedStartTime() != null && !now.before(stage.getPlannedStartTime())) {
            return "IN_PROGRESS";
        }
        return stage.getStageStatus();
    }

    private void evictConferenceCache(Long conferenceId) {
        try {
            stringRedisTemplate.delete(List.of(
                    RedisCacheConstant.CONFERENCE_STAGE_LIST_KEY + conferenceId,
                    RedisCacheConstant.CONFERENCE_TASK_LIST_KEY + conferenceId
            ));
        } catch (Exception ex) {
            log.warn("[ConferenceWorkflowScheduler] cache eviction failed, conferenceId={}", conferenceId, ex);
        }
    }
}
