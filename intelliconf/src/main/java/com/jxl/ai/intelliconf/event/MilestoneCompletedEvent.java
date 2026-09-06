package com.jxl.ai.intelliconf.event;

import org.springframework.context.ApplicationEvent;

/**
 * 里程碑完成事件
 * <p>
 * 当里程碑节点状态从【处理中(1)】切换为【已达成(2)】时由 MilestoneCompletionChecker 发布，
 * MilestoneSentinel 监听该事件以立即触发以该节点为前置依赖的下游节点的资格校验。
 */
public class MilestoneCompletedEvent extends ApplicationEvent {

    private final Long confId;
    private final String nodeCode;

    public MilestoneCompletedEvent(Object source, Long confId, String nodeCode) {
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
