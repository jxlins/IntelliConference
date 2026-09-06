package com.jxl.ai.intelliconf.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum MilestoneTemplate {
    INITIATION("INITIATION", "会议创建与启动", null, -365, -330),
    COMMITTEE("COMMITTEE", "委员会组建", "INITIATION", -329, -300),
    CFP("CFP", "征文启事", "COMMITTEE", -299, -270),
    SUBMISSION_SETUP("SUBMISSION_SETUP", "投稿系统准备", "CFP", -269, -240),
    SUBMISSION("SUBMISSION", "投稿阶段", "SUBMISSION_SETUP", -239, -180),
    SCREENING("SCREENING", "稿件初筛", "SUBMISSION", -179, -165),
    REVIEW_ASSIGNMENT("REVIEW_ASSIGNMENT", "审稿分配", "SCREENING", -164, -150),
    REVIEW("REVIEW", "专家评审", "REVIEW_ASSIGNMENT", -149, -110),
    REBUTTAL("REBUTTAL", "作者回复", "REVIEW", -109, -96),
    ACCEPTANCE("ACCEPTANCE", "录用决策", "REBUTTAL", -95, -80),
    CAMERA_READY("CAMERA_READY", "终稿提交", "ACCEPTANCE", -79, -60),
    REGISTRATION("REGISTRATION", "注册缴费", "CAMERA_READY", -59, -30),
    PROGRAM("PROGRAM", "议程编排", "REGISTRATION", -29, -14),
    LOGISTICS("LOGISTICS", "会务准备", "PROGRAM", -13, -1),
    CONFERENCE("CONFERENCE", "会议进行", "LOGISTICS", 0, 3),
    POST_CONF("POST_CONF", "会后收尾", "CONFERENCE", 4, 30);

    private final String code;
    private final String name;
    private final String preNode;
    private final int startOffset;
    private final int endOffset;
}
