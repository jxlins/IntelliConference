package com.jxl.ai.intelliconf.dto.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
public class CommitteeInviteRespDTO {

    private Long confId;

    private String email;

    private String name;

    private String institution;

    private String role;

    private String inviteStatus;

    private String inviteLink;

    private String accessToken;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date tokenExpireTime;
}
