package com.jxl.ai.intelliconf.author_discovery.service;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.jxl.ai.intelliconf.author_discovery.config.AuthorDiscoveryProperties;
import com.jxl.ai.intelliconf.author_discovery.dto.OpenAlexAuthor;
import com.jxl.ai.intelliconf.author_discovery.dto.OpenAlexPaper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Semantic Scholar 论文源（https://api.semanticscholar.org）。
 * 作为 OpenAlex 的补充：其 openAccessPdf 字段通常给出可直接下载的 PDF 直链，
 * 能显著提高公开邮箱核验的命中率。未配置 API Key 时按官方约 100 次/5 分钟限速。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SemanticScholarClient {

    private static final String SEARCH_FIELDS = "paperId,title,year,externalIds,citationCount,"
            + "isOpenAccess,openAccessPdf,authors.name,authors.authorId,authors.affiliations";
    private static final int PAGE_SIZE = 100;
    /** S2 单关键词最多返回 1000 条（offset 分页上限） */
    private static final int MAX_OFFSET = 1000;

    private final AuthorDiscoveryProperties properties;
    private final AtomicLong lastRequestAt = new AtomicLong(0);
    private final AtomicLong rateLimitedUntil = new AtomicLong(0);

    public List<OpenAlexPaper> searchWorks(List<String> keywords, Integer yearFrom, Integer yearTo, int maxPapers) {
        long validKeywordCount = keywords.stream().filter(StrUtil::isNotBlank).count();
        int perKeywordLimit = Math.max(100, (int) Math.ceil(maxPapers / (double) Math.max(validKeywordCount, 1)));
        Map<String, OpenAlexPaper> paperMap = new LinkedHashMap<>();
        for (String keyword : keywords) {
            if (System.currentTimeMillis() < rateLimitedUntil.get()) break;
            if (StrUtil.isBlank(keyword)) {
                continue;
            }
            try {
                for (OpenAlexPaper paper : searchWorksByKeyword(keyword, yearFrom, yearTo, perKeywordLimit)) {
                    paperMap.putIfAbsent(dedupeKey(paper), paper);
                    if (paperMap.size() >= maxPapers) {
                        return new ArrayList<>(paperMap.values());
                    }
                }
            } catch (RuntimeException ex) {
                log.warn("Semantic Scholar keyword search failed, keyword={}, errorType={}", keyword, ex.getClass().getSimpleName());
            }
        }
        return new ArrayList<>(paperMap.values());
    }

    private List<OpenAlexPaper> searchWorksByKeyword(String query, Integer yearFrom, Integer yearTo, int limit) {
        List<OpenAlexPaper> papers = new ArrayList<>();
        int offset = 0;
        while (papers.size() < limit && offset < MAX_OFFSET) {
            UriComponentsBuilder builder = UriComponentsBuilder
                    .fromHttpUrl(properties.getSemanticScholarBaseUrl() + "/graph/v1/paper/search")
                    .queryParam("query", query)
                    .queryParam("fields", SEARCH_FIELDS)
                    .queryParam("limit", Math.min(PAGE_SIZE, limit - papers.size()))
                    .queryParam("offset", offset);
            if (yearFrom != null || yearTo != null) {
                String from = yearFrom == null ? "" : String.valueOf(yearFrom);
                String to = yearTo == null ? "" : String.valueOf(yearTo);
                builder.queryParam("year", from + "-" + to);
            }
            JSONObject body;
            try {
                body = executeJson(builder.encode().build().toUriString());
            } catch (RuntimeException ex) {
                if (papers.isEmpty()) throw ex;
                log.warn("Semantic Scholar later page unavailable; retained {} papers", papers.size());
                break;
            }
            JSONArray data = body.getJSONArray("data");
            if (data == null || data.isEmpty()) break;
            for (int i = 0; i < data.size() && papers.size() < limit; i++) {
                OpenAlexPaper paper = parsePaper(data.getJSONObject(i));
                if (paper != null) papers.add(paper);
            }
            Integer next = body.getInteger("next");
            if (next == null || next <= offset || data.size() < PAGE_SIZE) break;
            offset = next;
        }
        return papers;
    }

    private JSONObject executeJson(String url) {
        RuntimeException last = null;
        for (int attempt = 0; attempt <= properties.getMaxRetry(); attempt++) {
            try {
                waitForRateLimit();
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(properties.getRequestTimeoutSeconds()))
                        .build();
                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(properties.getRequestTimeoutSeconds()))
                        .header("User-Agent", properties.getUserAgent())
                        .GET();
                if (StrUtil.isNotBlank(properties.getSemanticScholarApiKey())) {
                    requestBuilder.header("x-api-key", properties.getSemanticScholarApiKey());
                }
                HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return JSONObject.parseObject(response.body());
                }
                if (response.statusCode() == 429) {
                    rateLimitedUntil.set(System.currentTimeMillis() + 300000);
                    throw new RateLimitedException();
                }
                last = new IllegalStateException("Semantic Scholar returned HTTP " + response.statusCode());
            } catch (RateLimitedException ex) {
                throw ex;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Semantic Scholar request interrupted", ex);
            } catch (Exception ex) {
                last = new IllegalStateException("Semantic Scholar request failed", ex);
                log.warn("Semantic Scholar request failed, attempt={}, errorType={}", attempt + 1, ex.getClass().getSimpleName());
            }
        }
        throw last == null ? new IllegalStateException("Semantic Scholar request failed") : last;
    }

    private void waitForRateLimit() throws InterruptedException {
        long interval = StrUtil.isNotBlank(properties.getSemanticScholarApiKey())
                ? Math.max(200L, properties.getSemanticScholarRequestIntervalMs() / 3)
                : properties.getSemanticScholarRequestIntervalMs();
        long now = System.currentTimeMillis();
        long waitMs = interval - (now - lastRequestAt.get());
        if (waitMs > 0) {
            Thread.sleep(waitMs);
        }
        lastRequestAt.set(System.currentTimeMillis());
    }

    private static class RateLimitedException extends RuntimeException {
        RateLimitedException() { super("Semantic Scholar rate limited; source paused for five minutes"); }
    }

    private OpenAlexPaper parsePaper(JSONObject item) {
        if (item == null || StrUtil.isBlank(item.getString("title"))) {
            return null;
        }
        String paperId = item.getString("paperId");
        JSONObject externalIds = item.getJSONObject("externalIds");
        String doi = externalIds == null ? null : externalIds.getString("DOI");
        JSONObject openAccessPdf = item.getJSONObject("openAccessPdf");
        String pdfUrl = openAccessPdf == null ? null : openAccessPdf.getString("url");
        boolean openAccess = Boolean.TRUE.equals(item.getBoolean("isOpenAccess")) || StrUtil.isNotBlank(pdfUrl);
        return OpenAlexPaper.builder()
                .id(StrUtil.isNotBlank(paperId) ? "https://www.semanticscholar.org/paper/" + paperId : null)
                .title(item.getString("title"))
                .publicationYear(item.getInteger("year"))
                .doi(StrUtil.isBlank(doi) ? null : doi.trim().toLowerCase())
                .citedByCount(item.getInteger("citationCount"))
                .landingPageUrl(StrUtil.isNotBlank(paperId) ? "https://www.semanticscholar.org/paper/" + paperId : null)
                .openAccessLandingPageUrl(null)
                .pdfUrl(pdfUrl)
                .openAccess(openAccess)
                .authors(parseAuthors(item.getJSONArray("authors")))
                .build();
    }

    private List<OpenAlexAuthor> parseAuthors(JSONArray authors) {
        if (authors == null) {
            return List.of();
        }
        List<OpenAlexAuthor> result = new ArrayList<>();
        for (int i = 0; i < authors.size(); i++) {
            JSONObject author = authors.getJSONObject(i);
            JSONArray affiliations = author.getJSONArray("affiliations");
            String institution = affiliations == null || affiliations.isEmpty() ? null : affiliations.getString(0);
            String authorId = author.getString("authorId");
            result.add(OpenAlexAuthor.builder()
                    .id(StrUtil.isNotBlank(authorId) ? "S2:" + authorId : null)
                    .displayName(author.getString("name"))
                    .institution(institution)
                    .corresponding(false)
                    .build());
        }
        return result;
    }

    private String dedupeKey(OpenAlexPaper paper) {
        if (StrUtil.isNotBlank(paper.getDoi())) {
            return "doi:" + paper.getDoi();
        }
        return "title:" + StrUtil.blankToDefault(paper.getTitle(), "").toLowerCase().replaceAll("\\s+", " ").trim();
    }
}
