package com.jxl.ai.intelliconf.author_discovery.dto;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class OpenAlexPaper {

    private String id;
    private String title;
    private Integer publicationYear;
    private String publicationDate;
    private String doi;
    private Integer citedByCount;
    private String landingPageUrl;
    private String openAccessLandingPageUrl;
    private String pdfUrl;
    private boolean openAccess;
    @Builder.Default
    private List<String> additionalUrls = new ArrayList<>();
    @Builder.Default
    private List<String> topics = new ArrayList<>();
    @Builder.Default
    private List<String> keywords = new ArrayList<>();
    @Builder.Default
    private List<OpenAlexAuthor> authors = new ArrayList<>();
}
