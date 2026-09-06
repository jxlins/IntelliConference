package com.jxl.ai.intelliconf.author_discovery.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EmailEvidence {

    private String email;
    private String sourceType;
    private String sourceUrl;
    private String evidenceText;
    private String pageText;
    private boolean generic;
    private double identityScore;
    private double confidence;
}
