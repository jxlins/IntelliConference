package com.jxl.ai.intelliconf.event;

import org.springframework.context.ApplicationEvent;

/**
 * 里程碑激活事件
 * <p>
 * 当里程碑节点状态从【等待中(0)】切换为【处理中(1)】时由 MilestoneSentinel 发布，
 * 下游监听器可据此执行额外的通知或统计逻辑。
 */
public class MilestoneActiveEvent extends ApplicationEvent {

    private final Long confId;
    private final String nodeCode;

    public MilestoneActiveEvent(Object source, Long confId, String nodeCode) {
        super(source);
        this.confId = confId;
        this.nodeCode = nodeCode;
    }

    public Long getConfId() {
        return confId;
    }

    public String getNodeCode() {
        return nodeCode;
    }
}
