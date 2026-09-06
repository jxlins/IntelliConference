package com.jxl.ai.intelliconf.author_discovery.dto;

import lombok.Data;

import java.util.List;

@Data
public class CreateAuthorDiscoveryJobReqDTO {

    private List<String> topicKeywords;
    private Integer yearFrom;
    private Integer yearTo;
    private Integer maxAuthors;
    private Boolean enableCrossref;
    private Boolean enableEmailExtraction;
}
