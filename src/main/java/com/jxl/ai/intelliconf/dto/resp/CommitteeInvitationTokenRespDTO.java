package com.jxl.ai.intelliconf.dto.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
public class CommitteeInvitationTokenRespDTO {

    private Long invitationId;
    private Long conferenceId;
    private String conferenceName;
    private String conferenceShortName;
    private String inviteeEmail;
    private String inviteeName;
    private String roleName;
    private String committeeName;
    private String invitationStatus;
    private Boolean expired;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date expiredAt;
}
