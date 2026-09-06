package com.jxl.ai.intelliconf.author_discovery.dto;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class AuthorCandidate {

    private String authorName;
    private String normalizedName;
    private String organization;
    private String countryRegion;
    private boolean corresponding;
    private int paperCount;
    private int recentPaperCount;
    private int totalCitations;
    private String sourceUrl;
    @Builder.Default
    private List<String> researchKeywords = new ArrayList<>();
    @Builder.Default
    private List<OpenAlexPaper> papers = new ArrayList<>();
    @Builder.Default
    private List<EmailEvidence> emailEvidences = new ArrayList<>();
    private String selectedEmail;
    private double topicSimilarity;
    private double emailConfidence;
    private double overallScore;
}
