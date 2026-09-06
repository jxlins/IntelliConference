package com.jxl.ai.intelliconf.common.constant;

/**
 * 里程碑常量类
 */
public class MilestoneConstant {

    /**
     * 里程碑节点编码
     */
    public static final String NODE_CODE_INITIATION = "INITIATION";
    public static final String NODE_CODE_CFP = "CFP";
    public static final String NODE_CODE_REVIEW = "REVIEW";
    public static final String NODE_CODE_REGISTRATION = "REGISTRATION";
    public static final String NODE_CODE_PREPARATION = "PREPARATION";
    public static final String NODE_CODE_LIVE_EVENT = "LIVE_EVENT";
    public static final String NODE_CODE_POST_EVENT = "POST_EVENT";
    public static final String NODE_CODE_ARCHIVE = "ARCHIVE";

    /**
     * 里程碑状态
     */
    public static final Integer STATUS_WAITING = 0;      // 等待中
    public static final Integer STATUS_IN_PROGRESS = 1;  // 进行中
    public static final Integer STATUS_COMPLETED = 2;    // 已完成
    public static final Integer STATUS_SKIPPED = 3;      // 已跳过

    /**
     * 确认状态
     */
    public static final Integer CONFIRM_NO = 0;   // 未确认
    public static final Integer CONFIRM_YES = 1;  // 已确认

    private MilestoneConstant() {
        // 工具类不允许实例化
    }
}
