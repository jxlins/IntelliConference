package com.jxl.ai.intelliconf.author_discovery.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AuthorDiscoveryCommand {

    private Long conferenceId;
    private List<String> topicKeywords;
    private Integer yearFrom;
    private Integer yearTo;
    private Integer maxAuthors;
    private Boolean enableCrossref;
    private Boolean enableEmailExtraction;
    private Long createdBy;
}
