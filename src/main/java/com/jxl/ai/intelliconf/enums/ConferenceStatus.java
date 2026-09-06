package com.jxl.ai.intelliconf.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 会议状态枚举
 * 用于标识会议的整体生命周期状态
 */
@Getter
@AllArgsConstructor
public enum ConferenceStatus {

    /**
     * 筹备中：会议刚创建，处于立项筹备阶段（INITIATION）
     */
    PREPARING(0, "PREPARING", "筹备中", "#909399"),

    /**
     * 进行中：会议已开启，包括征稿、评审、注册等阶段
     */
    LIVE(1, "LIVE", "进行中", "#16a34a"),

    /**
     * 收尾中：会议主体已结束，正在进行成果归档等后续工作
     */
    CONCLUDING(2, "CONCLUDING", "收尾中", "#e6a23c"),

    /**
     * 已归档：会议所有工作已完成，进入归档状态
     */
    ARCHIVED(3, "ARCHIVED", "已归档", "#909399");

    /**
     * 状态代码（数值）
     */
    private final Integer code;

    /**
     * 状态标识（字符串）
     */
    private final String status;

    /**
     * 状态名称（中文）
     */
    private final String name;

    /**
     * 显示颜色（十六进制）
     */
    private final String color;

    /**
     * 根据状态字符串获取枚举
     */
    public static ConferenceStatus fromStatus(String status) {
        if (status == null) {
            return PREPARING;
        }
        for (ConferenceStatus value : ConferenceStatus.values()) {
            if (value.status.equals(status)) {
                return value;
            }
        }
        return PREPARING;
    }

    /**
     * 根据代码获取枚举
     */
    public static ConferenceStatus fromCode(Integer code) {
        if (code == null) {
            return PREPARING;
        }
        for (ConferenceStatus value : ConferenceStatus.values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return PREPARING;
    }
}
