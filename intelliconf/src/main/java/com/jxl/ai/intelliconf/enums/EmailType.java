package com.jxl.ai.intelliconf.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum EmailType {
    // 类型(代码), 描述, 默认受众角色
    CFP_REMINDER("CFP_REMINDER", "征文通知", "PROSPECT"),
    SUBMISSION_CONFIRM("SUBMISSION_CONFIRM", "投稿确认", "AUTHOR"),
    REVIEW_INVITE("REVIEW_INVITE", "审稿邀请", "REVIEWER"),
    RESULT_ANNOUNCE("RESULT_ANNOUNCE", "结果公布", "AUTHOR");

    private final String code;
    private final String description;
    private final String defaultRole; // 这里对应你数据库中的角色标识
}