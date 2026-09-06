package com.jxl.ai.intelliconf.dto.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
@Builder
public class TaskCompleteRespDTO {

    private Long taskId;

    private String taskStatus;

    private String completionDesc;

    private Long completedBy;

    private String completedByName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date submittedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date actualEndTime;

    private Long stageId;

    private BigDecimal stageProgress;

    private Boolean coreTasksCompleted;
}
