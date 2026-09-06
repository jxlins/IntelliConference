package com.jxl.ai.intelliconf.dto.req;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class SendTemplateMailReqDTO {

    private String sceneCode;

    private List<TemplateMailRecipientReqDTO> recipients;

    private Map<String, Object> variables;

    private String createdBy;
}
