package com.jxl.ai.intelliconf.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.jxl.ai.intelliconf.common.database.BaseDO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 会议基础信息持久层实体
 */
@TableName("conferences")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ConferenceDO extends BaseDO {

    /**
     * ID
     */
//    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 会议全称
     */
    private String title;

    /**
     * 会议缩写
     */
    private String shortName;

    /**
     * 会议简介
     */
    private String description;

    /**
     * 会议网站
     */
    private String websiteUrl;

    /**
     * 官方联系网站
     */
    private String contactEmail;

    /**
     * 主办方
     */
    private String host;

    /**
     * 承办方
     */
    private String coOriganizer;

    /**
     * 会议开始时间
     */
    private Date startTime;

    /**
     * 会议结束时间
     */
    private Date endTime;

    private Date paperSubmissionDeadline;

    private Date notificationOfAcceptance;

    private Date cameraReadySubmission;

    private Date earlyBirdRegistration;

    private Date conferenceStartDate;

    private Date conferenceEndDate;

    private String setupStatus;

    /**
     * 当前状态
     */
    private String currentState;

    /**
     * 创建者
     */
    private String createUser;
}
