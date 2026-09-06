package com.jxl.ai.intelliconf.dto.req;

import lombok.Data;

@Data
public class BindMailAccountReqDTO {

    private String providerType;

    private String fromEmail;

    private String fromName;

    private String replyTo;

    private String smtpHost;

    private Integer smtpPort;

    private String username;

    private String password;

    private Boolean sslEnabled;

    private Boolean starttlsEnabled;
}
