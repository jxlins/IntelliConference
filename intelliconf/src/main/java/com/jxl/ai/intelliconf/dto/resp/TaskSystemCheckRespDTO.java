package com.jxl.ai.intelliconf.dto.resp;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class TaskSystemCheckRespDTO {

    private String taskCode;

    private String checkType;

    private Boolean canComplete;

    private Integer requiredRoleCount;

    private Integer acceptedRequiredRoleCount;

    private List<MissingRequiredRoleRespDTO> missingRequiredRoles;

    private String message;

    @Data
    @Builder
    public static class MissingRequiredRoleRespDTO {
        private String roleCode;
        private String roleName;
    }
}
