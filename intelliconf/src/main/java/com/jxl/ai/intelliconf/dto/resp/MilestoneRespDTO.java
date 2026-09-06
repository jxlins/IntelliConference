package com.jxl.ai.intelliconf.dto.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 会议里程碑响应参数
 */
@Data
public class MilestoneRespDTO {

    /**
     * ID
     */
    private Long id;

    /**
     * 关联会议ID
     */
    private Long confereId;

    /**
     * 节点编码
     */
    private String nodeCode;

    /**
     * 节点名称
     */
    private String nodeName;

    /**
     * 开始时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date startDate;

    /**
     * 计划结束时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date targetEndDate;

    /**
     * 实际完成时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date actualEndDate;

    /**
     * 状态 0:等待中, 1:处理中, 2:已达成, 3:已跳过
     */
    private Integer status;

    /**
     * 备注/调整原因
     */
    private String remark;

    /**
     * 是否已确认 0:未确认, 1:已确认
     */
    private Integer isConfirmed;

    private Integer taskTotalCount;

    private Integer taskCompletedCount;

    private Integer taskPendingCount;

    private Integer taskProcessingCount;

    private Integer taskOverdueCount;

    private Boolean current;

    private Integer taskProgressPercent;

    private String taskProgressText;
}
