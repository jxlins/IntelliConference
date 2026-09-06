package com.jxl.ai.intelliconf.dto.req;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 会议修改请求参数
 */
@Data
public class ConferenceUpdateRepDTO {

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
