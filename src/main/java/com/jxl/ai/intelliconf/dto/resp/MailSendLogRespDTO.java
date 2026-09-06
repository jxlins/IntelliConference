package com.jxl.ai.intelliconf.dto.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
public class MailSendLogRespDTO {

    private String recipientEmail;

    private String subject;

    private String status;

    private String errorCode;

    private String errorMessage;

    private String providerType;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date sentAt;
}
