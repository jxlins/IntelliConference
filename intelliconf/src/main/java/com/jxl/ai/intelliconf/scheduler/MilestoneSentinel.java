package com.jxl.ai.intelliconf.scheduler;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jxl.ai.intelliconf.dao.entity.ConfMilestoneDO;
import com.jxl.ai.intelliconf.dao.entity.ConfTaskInstanceDO;
import com.jxl.ai.intelliconf.dao.mapper.ConfMilestoneMapper;
import com.jxl.ai.intelliconf.dao.mapper.ConfTaskInstanceMapper;
import com.jxl.ai.intelliconf.event.MilestoneActiveEvent;
import com.jxl.ai.intelliconf.event.MilestoneCompletedEvent;
import com.jxl.ai.intelliconf.service.ConferenceTimelineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class MilestoneSentinel {

    private static final String SYSTEM_OPERATOR = "SYSTEM";

    private final ConfMilestoneMapper milestoneMapper;
    private final ConfTaskInstanceMapper taskInstanceMapper;
    private final ConferenceTimelineService conferenceTimelineService;
    private final ApplicationEventPublisher eventPublisher;

    @Scheduled(cron = "0 * * * * ?")
    public void checkAndTriggerMilestones() {
        Date now = new Date();
        List<ConfMilestoneDO> candidates = milestoneMapper.selectList(
                Wrappers.lambdaQuery(ConfMilestoneDO.class)
                        .eq(ConfMilestoneDO::getStatus, 0)
                        .eq(ConfMilestoneDO::getIsConfirmed, 1)
                        .le(ConfMilestoneDO::getStartDate, now)
        );

        for (ConfMilestoneDO milestone : candidates) {
            try {
                activateMilestoneByScheduler(milestone);
            } catch (Exception ex) {
                log.error("[MilestoneSentinel] Failed to activate milestone, conferenceId={}, milestoneId={}, nodeCode={}",
                        milestone.getConfereId(), milestone.getId(), milestone.getNodeCode(), ex);
            }
        }

        repairInProgressMilestonesWithoutTaskInstances();
    }

    @EventListener
    public void onMilestoneCompleted(MilestoneCompletedEvent event) {
        List<ConfMilestoneDO> downstreams = milestoneMapper.selectList(
                Wrappers.lambdaQuery(ConfMilestoneDO.class)
                        .eq(ConfMilestoneDO::getConfereId, event.getConfId())
                        .eq(ConfMilestoneDO::getPreNodeCode, event.getNodeCode())
                        .eq(ConfMilestoneDO::getStatus, 0)
                        .eq(ConfMilestoneDO::getIsConfirmed, 1)
        );

        for (ConfMilestoneDO downstream : downstreams) {
            try {
                activateMilestoneByScheduler(downstream);
            } catch (Exception ex) {
                log.error("[MilestoneSentinel] Failed to cascade milestone, conferenceId={}, milestoneId={}, nodeCode={}",
                        downstream.getConfereId(), downstream.getId(), downstream.getNodeCode(), ex);
            }
        }
    }

    @Transactional
    protected void activateMilestoneByScheduler(ConfMilestoneDO milestone) {
        int updated = milestoneMapper.update(null,
                Wrappers.lambdaUpdate(ConfMilestoneDO.class)
                        .set(ConfMilestoneDO::getStatus, 1)
                        .set(milestone.getStartDate() == null, ConfMilestoneDO::getStartDate, new Date())
                        .eq(ConfMilestoneDO::getId, milestone.getId())
                        .eq(ConfMilestoneDO::getStatus, 0));
        if (updated == 0) {
            return;
        }

        log.info("[MilestoneSentinel] Milestone activated, conferenceId={}, milestoneId={}, nodeCode={}",
                milestone.getConfereId(), milestone.getId(), milestone.getNodeCode());
        eventPublisher.publishEvent(new MilestoneActiveEvent(this, milestone.getConfereId(), milestone.getNodeCode()));
        if (!Integer.valueOf(0).equals(milestone.getAutoGenerateTasks())) {
            conferenceTimelineService.generateTasksForMilestone(milestone.getConfereId(), milestone.getId(), SYSTEM_OPERATOR);
        }
    }

    private void repairInProgressMilestonesWithoutTaskInstances() {
        List<ConfMilestoneDO> inProgress = milestoneMapper.selectList(
                Wrappers.lambdaQuery(ConfMilestoneDO.class)
                        .eq(ConfMilestoneDO::getStatus, 1)
                        .eq(ConfMilestoneDO::getIsConfirmed, 1)
        );

        for (ConfMilestoneDO milestone : inProgress) {
            Long taskCount = taskInstanceMapper.selectCount(
                    Wrappers.lambdaQuery(ConfTaskInstanceDO.class)
                            .eq(ConfTaskInstanceDO::getConferenceId, milestone.getConfereId())
                            .eq(ConfTaskInstanceDO::getMilestoneId, milestone.getId())
            );
            if (taskCount != null && taskCount > 0) {
                continue;
            }
            if (Integer.valueOf(0).equals(milestone.getAutoGenerateTasks())) {
                continue;
            }

            log.warn("[MilestoneSentinel] Repair task instances for milestone, conferenceId={}, milestoneId={}, nodeCode={}",
                    milestone.getConfereId(), milestone.getId(), milestone.getNodeCode());
            conferenceTimelineService.generateTasksForMilestone(milestone.getConfereId(), milestone.getId(), SYSTEM_OPERATOR);
        }
    }
}
