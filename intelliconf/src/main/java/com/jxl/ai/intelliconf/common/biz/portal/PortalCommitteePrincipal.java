package com.jxl.ai.intelliconf.common.biz.portal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortalCommitteePrincipal {

    private Long id;

    private Long confId;

    private String email;

    private String name;

    private String role;

    private String inviteStatus;

    private String accessToken;

    private Date tokenExpireTime;
}
