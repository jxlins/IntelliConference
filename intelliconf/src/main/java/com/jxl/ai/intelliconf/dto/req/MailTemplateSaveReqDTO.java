package com.jxl.ai.intelliconf.dto.req;

import lombok.Data;

@Data
public class MailTemplateSaveReqDTO {

    private String sceneCode;

    private String templateName;

    private String subjectTemplate;

    private String htmlTemplate;

    private String textTemplate;

    private Boolean enabled;
}
