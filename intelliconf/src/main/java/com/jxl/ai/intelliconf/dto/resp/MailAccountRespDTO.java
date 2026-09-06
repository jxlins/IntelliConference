package com.jxl.ai.intelliconf.dto.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
public class MailAccountRespDTO {

    private Long id;

    private Long conferenceId;

    private String providerType;

    private String fromEmail;

    private String fromName;

    private String replyTo;

    private String smtpHost;

    private Integer smtpPort;

    private Boolean sslEnabled;

    private Boolean starttlsEnabled;

    private Boolean enabled;

    private String lastTestStatus;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date lastTestTime;
}
