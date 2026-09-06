package com.jxl.ai.intelliconf.author_discovery.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class PotentialAuthorRespDTO {

    private Long id;
    private Long conferenceId;
    private Long discoveryJobId;
    private String authorName;
    private String email;
    private String organization;
    private String countryRegion;
    private String researchKeywords;
    private String representativePapers;
    private String sourcePlatform;
    private String sourceUrl;
    private BigDecimal topicSimilarity;
    private BigDecimal emailConfidence;
    private BigDecimal overallScore;
    private String reviewStatus;
    private String contactStatus;
}
