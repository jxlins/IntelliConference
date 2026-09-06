package com.jxl.ai.intelliconf.enums;

public enum CommitteeRole {

    CHAIR,
    REVIEWER;

    public static CommitteeRole fromCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return null;
        }
        for (CommitteeRole each : values()) {
            if (each.name().equalsIgnoreCase(code.trim())) {
                return each;
            }
        }
        return null;
    }
}
