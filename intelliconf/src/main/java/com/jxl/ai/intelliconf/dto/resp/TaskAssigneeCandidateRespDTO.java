package com.jxl.ai.intelliconf.dto.resp;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TaskAssigneeCandidateRespDTO {

    private Long userId;
    private String memberName;
    private String memberEmail;
    private String roleCode;
    private String roleName;
    private String committeeType;
    private String committeeName;
}
