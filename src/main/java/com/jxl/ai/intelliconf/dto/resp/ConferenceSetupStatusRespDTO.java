package com.jxl.ai.intelliconf.dto.resp;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ConferenceSetupStatusRespDTO {

    private Long conferenceId;

    private String shortName;

    private String title;

    private String setupStatus;

    private Boolean datesCompleted;

    private Boolean stagesGenerated;

    private Boolean needCompleteDates;

    private Boolean needGenerateStages;

    private String nextAction;
}
