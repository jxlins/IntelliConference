package com.jxl.ai.intelliconf.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jxl.ai.intelliconf.dao.entity.ConfMilestoneDO;
import com.jxl.ai.intelliconf.dao.entity.SysTaskLogDO;
import com.jxl.ai.intelliconf.dao.mapper.ConfMilestoneMapper;
import com.jxl.ai.intelliconf.dao.mapper.SysTaskLogMapper;
import com.jxl.ai.intelliconf.enums.ConferenceStatus;
import com.jxl.ai.intelliconf.event.ConferenceStatusChangeEvent;
import com.jxl.ai.intelliconf.event.MilestoneCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * 里程碑完成检测器
 * <p>
 * 在每次任务分发结束后被调用，检查该里程碑下是否仍有未完成的任务；
 * 若全部任务执行成功（executionStatus=2），则将里程碑状态更新为 2（已达成），
 * 并发布 {@link MilestoneCompletedEvent} 以触发下游节点的级联激活。
 * <p>
 * 状态流转：1（处理中）→ 2（已达成）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MilestoneCompletionChecker {

    private final ConfMilestoneMapper milestoneMapper;
    private final SysTaskLogMapper sysTaskLogMapper;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 检查指定里程碑是否满足完成条件，满足则将其更新为"已达成"并发布完成事件。
     *
     * @param milestoneId 里程碑ID
     */
    public void checkAndComplete(Long milestoneId) {
        ConfMilestoneDO milestone = milestoneMapper.selectById(milestoneId);
        if (milestone == null) {
            log.warn("[MilestoneCompletion] milestoneId={} not found, skip", milestoneId);
            return;
        }
        // 仅对【处理中】节点执行检查
        if (!Integer.valueOf(1).equals(milestone.getStatus())) {
            log.debug("[MilestoneCompletion] milestoneId={} status={}, not in-progress, skip",
                    milestoneId, milestone.getStatus());
            return;
        }

        // 统计该里程碑下仍未成功（executionStatus != 2）的任务日志数
        long incompleteTasks = sysTaskLogMapper.selectCount(
                Wrappers.lambdaQuery(SysTaskLogDO.class)
                        .eq(SysTaskLogDO::getMilestoneId, milestoneId)
                        .ne(SysTaskLogDO::getExecutionStatus, 2)
        );

        if (incompleteTasks > 0) {
            log.info("[MilestoneCompletion] milestoneId={} nodeCode={} still has {} incomplete task(s), waiting",
                    milestoneId, milestone.getNodeCode(), incompleteTasks);
            return;
        }

        // 乐观锁：WHERE status=1，防止并发重复触发
        int updated = milestoneMapper.update(null,
                Wrappers.lambdaUpdate(ConfMilestoneDO.class)
                        .set(ConfMilestoneDO::getStatus, 2)
                        .set(ConfMilestoneDO::getActualEndDate, new Date())
                        .eq(ConfMilestoneDO::getId, milestoneId)
                        .eq(ConfMilestoneDO::getStatus, 1)
        );

        if (updated > 0) {
            log.info("[MilestoneCompletion] Milestone ACHIEVED: confId={}, nodeCode={}, milestoneId={}",
                    milestone.getConfereId(), milestone.getNodeCode(), milestoneId);
            
            // 发布完成事件，由 MilestoneSentinel 监听并立即激活下游节点
            eventPublisher.publishEvent(
                    new MilestoneCompletedEvent(this, milestone.getConfereId(), milestone.getNodeCode())
            );

            // 状态自动流转：当 INITIATION 阶段完成时，会议状态从 PREPARING 切换为 LIVE
            if ("INITIATION".equals(milestone.getNodeCode())) {
                log.info("[MilestoneCompletion] INITIATION 阶段已完成，发布会议状态变更事件: confId={}", 
                        milestone.getConfereId());
                // 发布事件，由 ConferenceService 监听并执行状态更新（解耦依赖）
                eventPublisher.publishEvent(
                        new ConferenceStatusChangeEvent(
                                milestone.getConfereId(), 
                                ConferenceStatus.LIVE, 
                                "INITIATION 阶段完成"
                        )
                );
            }
        } else {
            log.debug("[MilestoneCompletion] milestoneId={} already transitioned by another thread, skip", milestoneId);
        }
    }
}
