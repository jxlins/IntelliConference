package com.jxl.ai.intelliconf.dto.resp;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class TimelineOverviewRespDTO {
    private Long conferenceId;
    private String currentNodeCode;
    private String currentNodeName;
    private String overallStatus;
    private List<TimelineMilestoneRespDTO> milestones;
}
