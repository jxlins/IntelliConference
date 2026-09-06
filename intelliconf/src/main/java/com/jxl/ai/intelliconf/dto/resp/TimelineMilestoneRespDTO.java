package com.jxl.ai.intelliconf.dto.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
public class TimelineMilestoneRespDTO {
    private Long id;
    private String nodeCode;
    private String nodeName;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date startDate;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date targetEndDate;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date actualEndDate;
    private Integer status;
    private Integer isConfirmed;
    private Integer sortOrder;
    private Integer taskTotalCount;
    private Integer taskCompletedCount;
    private Integer taskPendingCount;
    private Integer taskProcessingCount;
    private Integer taskOverdueCount;
    private Boolean current;
    private Integer taskProgressPercent;
    private String taskProgressText;
}
