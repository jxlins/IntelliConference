package com.jxl.ai.intelliconf.author_discovery.enums;

public enum DiscoveryJobStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    /** 手动停止：已入库结果保留，可再次发起任务继续补充 */
    CANCELLED
}
