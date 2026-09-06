package com.jxl.ai.intelliconf.author_discovery.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PotentialAuthorQueryDTO {

    private String reviewStatus;
    private String contactStatus;
    private BigDecimal minScore;
    private String sourcePlatform;
    private Boolean hasEmail;
}
