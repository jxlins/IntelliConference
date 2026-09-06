package com.jxl.ai.intelliconf.author_discovery.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "author-discovery")
public class AuthorDiscoveryProperties {

    private String openalexBaseUrl = "https://api.openalex.org";
    private String crossrefBaseUrl = "https://api.crossref.org";
    private String userAgent = "ConferenceAuthorDiscoveryBot";
    private String contactEmail = "";
    private int requestTimeoutSeconds = 10;
    private int maxPageBytes = 5 * 1024 * 1024;
    private int maxHtmlBytes = 5 * 1024 * 1024;
    private int maxPdfBytes = 15 * 1024 * 1024;
    private int maxDiscoveredPdfPerPage = 3;
    private boolean respectRobots = true;
    private String robotsFailurePolicy = "SKIP";
    private long requestIntervalMs = 200;
    private int maxRetry = 2;
    private int maxPapersFactor = 5;
    private int maxTotalPapers = 500;
    private int pdfParseMaxPages = 2;
}
