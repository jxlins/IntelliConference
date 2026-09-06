package com.jxl.ai.intelliconf.dto.resp;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MailCheckRespDTO {

    private boolean success;

    private String errorCode;

    private String errorMessage;
}
