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
    private String semanticScholarBaseUrl = "https://api.semanticscholar.org";
    private String semanticScholarApiKey = "";
    private boolean semanticScholarEnabled = true;
    private String userAgent = "ConferenceAuthorDiscoveryBot";
    private String contactEmail = "";
    private String apiKey = "";
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
    private int maxTotalPapers = 2000;
    private int pdfParseMaxPages = 2;
    /** 发现任务整体时限（分钟），超时后保留已入库结果 */
    private int jobDeadlineMinutes = 180;
    /** 邮箱核验并发线程数 */
    private int emailExtractionWorkers = 12;
    /** Semantic Scholar 未配置 API Key 时的请求间隔（毫秒），官方限制约 100 次/5 分钟 */
    private long semanticScholarRequestIntervalMs = 3000;
    /** 页面/PDF 抓取超时（秒）。比 API 请求更短，避免慢站点拖垮整体吞吐 */
    private int extractionTimeoutSeconds = 8;
    /** robots.txt 抓取超时（秒） */
    private int robotsTimeoutSeconds = 5;
    /** 同一主机连续连接失败达到该次数后，本进程内跳过该主机的所有 URL（应对 arXiv 等不可达站点） */
    private int hostFailureThreshold = 3;
    /** 每位候选作者最多抓取的论文数（已按 PDF 直链优先排序） */
    private int maxPapersPerCandidate = 4;
    /** 每篇论文最多抓取的 URL 数 */
    private int maxUrlsPerPaper = 4;
}
