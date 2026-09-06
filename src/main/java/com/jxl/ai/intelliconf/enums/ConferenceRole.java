package com.jxl.ai.intelliconf.enums;

import lombok.Getter;

/**
 * 会议级角色定义（按能力从高到低）。
 */
@Getter
public enum ConferenceRole {

    OWNER(4),
    COMMITTEE(3),
    OPERATOR(2),
    VIEWER(1);

    private final int level;

    ConferenceRole(int level) {
        this.level = level;
    }

    public static ConferenceRole fromCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return null;
        }
        for (ConferenceRole each : values()) {
            if (each.name().equalsIgnoreCase(code.trim())) {
                return each;
            }
        }
        return null;
    }
}
