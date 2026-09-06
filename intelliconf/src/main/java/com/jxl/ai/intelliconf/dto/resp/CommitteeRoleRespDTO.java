package com.jxl.ai.intelliconf.dto.resp;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CommitteeRoleRespDTO {

    private Long roleDefId;

    private String roleCode;

    private String roleName;

    private String committeeType;

    private String committeeName;

    private String roleDesc;

    private Integer isRequired;

    private Integer canAssignTask;
}
