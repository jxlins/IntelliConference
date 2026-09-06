package com.jxl.ai.intelliconf.dto.resp;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CommitteeSetupSummaryRespDTO {

    private Integer requiredRoleCount;
    private Integer acceptedRequiredRoleCount;
    private List<TaskSystemCheckRespDTO.MissingRequiredRoleRespDTO> missingRequiredRoles;
    private Integer totalInvitationCount;
    private Integer sentCount;
    private Integer acceptedCount;
    private Integer declinedCount;
    private Integer expiredCount;
    private Boolean canCompleteCommitteeTask;
}
