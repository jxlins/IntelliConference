package com.jxl.ai.intelliconf.dto.req;

import lombok.Data;

import java.util.Map;

@Data
public class TemplateMailRecipientReqDTO {

    private String email;

    private String name;

    private Map<String, Object> variables;
}
