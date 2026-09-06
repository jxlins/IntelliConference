package com.jxl.ai.intelliconf.event;

import lombok.Getter;

/**
 * 里程碑确认事件
 * <p>
 * 当会议的所有里程碑被确认并锁定时发布此事件，
 * 触发 INITIATION 阶段的自动激活和任务分发。
 */
@Getter
public class MilestoneConfirmedEvent {
    
    /**
     * 会议ID
     */
    private final Long confId;
    
    /**
     * 要激活的里程碑ID
     */
    private final Long milestoneId;
    
    /**
     * 要激活的节点编码
     */
    private final String nodeCode;

    public MilestoneConfirmedEvent(Long confId, Long milestoneId, String nodeCode) {
        this.confId = confId;
        this.milestoneId = milestoneId;
        this.nodeCode = nodeCode;
    }
}
