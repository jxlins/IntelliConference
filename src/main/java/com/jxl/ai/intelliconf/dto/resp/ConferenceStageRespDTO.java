package com.jxl.ai.intelliconf.dto.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
@Builder
public class ConferenceStageRespDTO {

    private Long id;

    private Long stageDefId;

    private String stageCode;

    private String stageName;

    private Integer stageOrder;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date plannedStartTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date plannedEndTime;

    private String stageStatus;

    private BigDecimal progress;

    private Integer isCurrent;
}
