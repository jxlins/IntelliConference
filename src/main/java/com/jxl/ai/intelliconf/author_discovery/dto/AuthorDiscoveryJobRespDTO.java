package com.jxl.ai.intelliconf.author_discovery.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthorDiscoveryJobRespDTO {

    private Long jobId;
    private String status;
    private Integer totalPapers;
    private Integer totalCandidates;
    private Integer highConfidenceEmails;
    private String errorMessage;
}
