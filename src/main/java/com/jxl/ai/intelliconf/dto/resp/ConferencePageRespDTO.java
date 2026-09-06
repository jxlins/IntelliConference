package com.jxl.ai.intelliconf.dto.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 会议信息响应参数
 */
@Data
public class ConferencePageRespDTO {

    /**
     * 会议全称
     */
    private String title;

    /**
     * 会议缩写
     */
    private String shortName;

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
     * 当前状态
     */
    private String currentState;

    private String setupStatus;

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
