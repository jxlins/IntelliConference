package com.jxl.ai.intelliconf.dto.req;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

@Data
public class ConferenceStageGenerateReqDTO {

    private Long stageDefId;

    private String stageCode;

    private String stageName;

    private Integer stageOrder;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date plannedStartTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date plannedEndTime;
}
