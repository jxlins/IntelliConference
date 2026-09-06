package com.jxl.ai.intelliconf.dto.req;

import lombok.Data;

@Data
public class TaskActionReqDTO {
    private String comment;
    private Long targetCommitteeId;
    private String targetUserId;
    private String targetUserName;
    private String targetRoleCode;
}
