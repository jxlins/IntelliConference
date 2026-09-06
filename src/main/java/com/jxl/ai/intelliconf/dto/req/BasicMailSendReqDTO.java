package com.jxl.ai.intelliconf.dto.req;

import lombok.Data;

import java.util.List;

@Data
public class BasicMailSendReqDTO {

    private Long confId;

    private String subject;

    private String contentBody;

    private List<BasicMailRecipientReqDTO> recipients;
}
