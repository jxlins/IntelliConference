package com.jxl.ai.intelliconf.event;

import com.jxl.ai.intelliconf.enums.ConferenceStatus;
import lombok.Getter;

/**
 * 会议状态变更请求事件
 * <p>
 * 当需要更新会议状态时发布此事件，由 ConferenceService 监听并执行状态更新。
 * 用于解耦 MilestoneCompletionChecker 和 ConferenceService 之间的循环依赖。
 */
@Getter
public class ConferenceStatusChangeEvent {
    
    /**
     * 会议ID
     */
    private final Long confId;
    
    /**
     * 目标状态
     */
    private final ConferenceStatus targetStatus;
    
    /**
     * 触发原因（用于日志）
     */
    private final String reason;

    public ConferenceStatusChangeEvent(Long confId, ConferenceStatus targetStatus, String reason) {
        this.confId = confId;
        this.targetStatus = targetStatus;
        this.reason = reason;
    }
}
