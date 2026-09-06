package com.jxl.ai.intelliconf.dto.req;

import lombok.Data;

@Data
public class CommitteeInvitationSaveReqDTO {

    private Long sourceTaskId;

    private String inviteeName;

    private String inviteeEmail;

    private String inviteeAffiliation;

    private Long roleDefId;
}
