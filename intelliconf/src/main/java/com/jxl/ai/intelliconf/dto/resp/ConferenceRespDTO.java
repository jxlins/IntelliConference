package com.jxl.ai.intelliconf.dto.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 会议信息响应参数
 */
@Data
public class ConferenceRespDTO {

    /**
     * 会议ID
     */
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
     * 官方联系邮箱
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
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date startTime;

    /**
     * 会议结束时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date endTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date paperSubmissionDeadline;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date notificationOfAcceptance;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date cameraReadySubmission;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date earlyBirdRegistration;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date conferenceStartDate;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date conferenceEndDate;

    private String setupStatus;

    /**
     * 当前状态
     */
    private String currentState;

    /**
     * 创建者用户名
     */
    private String createUser;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    /**
     * 省名称
     */
    private String province;

    /**
     * 市名称
     */
    private String city;

    /**
     * 国家标识
     */
    private String country;

    /**
     * 详细地址
     */
    private String address;
}
