package com.jxl.ai.intelliconf.dto.resp;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MailTemplateRespDTO {

    private Long id;

    private Long conferenceId;

    private String sceneCode;

    private String templateName;

    private String subjectTemplate;

    private String htmlTemplate;

    private String textTemplate;

    private Boolean enabled;
}
