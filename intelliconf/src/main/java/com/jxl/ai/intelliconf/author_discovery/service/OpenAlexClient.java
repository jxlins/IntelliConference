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

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAlexClient {

    private final AuthorDiscoveryProperties properties;
    private final AtomicLong lastRequestAt = new AtomicLong(0);

    public List<OpenAlexPaper> searchWorks(List<String> keywords, Integer yearFrom, Integer yearTo, int perPage) {
        int maxPapers = Math.min(properties.getMaxTotalPapers(), Math.max(perPage * properties.getMaxPapersFactor(), perPage));
        long validKeywordCount = keywords.stream().filter(StrUtil::isNotBlank).count();
        int perKeywordLimit = Math.max(100, (int) Math.ceil(maxPapers / (double) Math.max(validKeywordCount, 1)));
        Map<String, OpenAlexPaper> paperMap = new LinkedHashMap<>();
        int failedKeywordCount = 0;
        int requestedKeywordCount = 0;
        for (String keyword : keywords) {
            if (StrUtil.isBlank(keyword)) {
                continue;
            }
            requestedKeywordCount++;
            try {
                for (OpenAlexPaper paper : searchWorksByKeyword(keyword, yearFrom, yearTo, perKeywordLimit)) {
                    String key = StrUtil.blankToDefault(paper.getId(), StrUtil.blankToDefault(paper.getDoi(), paper.getTitle()));
                    paperMap.putIfAbsent(key, paper);
                    if (paperMap.size() >= maxPapers) {
                        return new ArrayList<>(paperMap.values());
                    }
                }
            } catch (RuntimeException ex) {
                failedKeywordCount++;
                log.warn("OpenAlex keyword search failed, keyword={}, errorType={}", keyword, ex.getClass().getSimpleName());
            }
        }
        if (paperMap.isEmpty() && requestedKeywordCount > 0 && failedKeywordCount == requestedKeywordCount) {
            throw new IllegalStateException("OpenAlex request failed for all topic keywords");
        }
        return new ArrayList<>(paperMap.values());
    }

    private List<OpenAlexPaper> searchWorksByKeyword(String search, Integer yearFrom, Integer yearTo, int limit) {
        int pageSize = Math.min(Math.max(limit, 1), 100);
        List<String> filters = new ArrayList<>();
        filters.add("is_oa:true");
        if (yearFrom != null) {
            filters.add("from_publication_date:" + yearFrom + "-01-01");
        }
        if (yearTo != null) {
            filters.add("to_publication_date:" + yearTo + "-12-31");
        }
        List<OpenAlexPaper> papers = new ArrayList<>();
        String cursor = "*";
        while (papers.size() < limit && StrUtil.isNotBlank(cursor)) {
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(properties.getOpenalexBaseUrl() + "/works")
                    .queryParam("search", search)
                    .queryParam("per-page", Math.min(pageSize, limit - papers.size()))
                    .queryParam("cursor", cursor)
                    .queryParam("select", "id,display_name,publication_year,publication_date,doi,cited_by_count,primary_location,best_oa_location,open_access,locations,topics,keywords,authorships");
            if (StrUtil.isNotBlank(properties.getApiKey())) builder.queryParam("api_key", properties.getApiKey());
            if (!filters.isEmpty()) builder.queryParam("filter", String.join(",", filters));
            if (StrUtil.isNotBlank(properties.getContactEmail())) builder.queryParam("mailto", properties.getContactEmail());
            JSONObject body;
            try { body = executeJson(builder.encode().build().toUriString()); }
            catch (RuntimeException ex) {
                if (papers.isEmpty()) throw ex;
                log.warn("OpenAlex later page unavailable; retained {} papers", papers.size());
                break;
            }
            JSONArray results = body.getJSONArray("results");
            if (results == null || results.isEmpty()) break;
            for (int i = 0; i < results.size() && papers.size() < limit; i++) {
                papers.add(parsePaper(results.getJSONObject(i)));
            }
            JSONObject meta = body.getJSONObject("meta");
            String nextCursor = meta == null ? null : meta.getString("next_cursor");
            if (results.size() < pageSize || StrUtil.isBlank(nextCursor) || nextCursor.equals(cursor)) break;
            cursor = nextCursor;
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
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(properties.getRequestTimeoutSeconds()))
                        .header("User-Agent", userAgent())
                        .GET()
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return JSONObject.parseObject(response.body());
                }
                last = new IllegalStateException("OpenAlex returned HTTP " + response.statusCode());
            } catch (Exception ex) {
                last = new IllegalStateException("OpenAlex request failed", ex);
                log.warn("OpenAlex request failed, attempt={}, errorType={}", attempt + 1, ex.getClass().getSimpleName());
            }
        }
        throw last == null ? new IllegalStateException("OpenAlex request failed") : last;
    }

    private void waitForRateLimit() throws InterruptedException {
        long now = System.currentTimeMillis();
        long previous = lastRequestAt.get();
        long waitMs = properties.getRequestIntervalMs() - (now - previous);
        if (waitMs > 0) {
            Thread.sleep(waitMs);
        }
        lastRequestAt.set(System.currentTimeMillis());
    }

    private String userAgent() {
        if (StrUtil.isBlank(properties.getContactEmail())) {
            return properties.getUserAgent();
        }
        return properties.getUserAgent() + " (mailto:" + properties.getContactEmail() + ")";
    }

    private OpenAlexPaper parsePaper(JSONObject item) {
        JSONObject primaryLocation = item.getJSONObject("primary_location");
        JSONObject oaLocation = item.getJSONObject("best_oa_location");
        JSONObject openAccess = item.getJSONObject("open_access");
        java.util.Set<String> urls = new java.util.LinkedHashSet<>();
        JSONArray locations = item.getJSONArray("locations");
        if (locations != null) for (int i = 0; i < locations.size(); i++) {
            JSONObject location = locations.getJSONObject(i);
            if (!Boolean.TRUE.equals(location.getBoolean("is_oa"))) continue;
            for (String field : List.of("pdf_url", "landing_page_url")) {
                String value = location.getString(field);
                if (StrUtil.isNotBlank(value)) urls.add(value);
            }
        }
        return OpenAlexPaper.builder()
                .additionalUrls(new ArrayList<>(urls))
                .id(item.getString("id"))
                .title(item.getString("display_name"))
                .publicationYear(item.getInteger("publication_year"))
                .publicationDate(item.getString("publication_date"))
                .doi(normalizeDoi(item.getString("doi")))
                .citedByCount(item.getInteger("cited_by_count"))
                .landingPageUrl(primaryLocation == null ? null : primaryLocation.getString("landing_page_url"))
                .openAccessLandingPageUrl(oaLocation == null ? null : oaLocation.getString("landing_page_url"))
                .pdfUrl(oaLocation == null ? null : oaLocation.getString("pdf_url"))
                .openAccess(openAccess != null && Boolean.TRUE.equals(openAccess.getBoolean("is_oa")))
                .topics(parseNames(item.getJSONArray("topics")))
                .keywords(parseNames(item.getJSONArray("keywords")))
                .authors(parseAuthors(item.getJSONArray("authorships")))
                .build();
    }

    private List<OpenAlexAuthor> parseAuthors(JSONArray authorships) {
        if (authorships == null) {
            return List.of();
        }
        List<OpenAlexAuthor> authors = new ArrayList<>();
        for (int i = 0; i < authorships.size(); i++) {
            JSONObject authorship = authorships.getJSONObject(i);
            JSONObject author = authorship.getJSONObject("author");
            JSONArray institutions = authorship.getJSONArray("institutions");
            JSONObject institution = institutions == null || institutions.isEmpty() ? null : institutions.getJSONObject(0);
            authors.add(OpenAlexAuthor.builder()
                    .id(author == null ? null : author.getString("id"))
                    .displayName(author == null ? null : author.getString("display_name"))
                    .institution(institution == null ? null : institution.getString("display_name"))
                    .countryCode(institution == null ? null : institution.getString("country_code"))
                    .corresponding(Boolean.TRUE.equals(authorship.getBoolean("is_corresponding")))
                    .build());
        }
        return authors;
    }

    private List<String> parseNames(JSONArray array) {
        if (array == null) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            JSONObject item = array.getJSONObject(i);
            String name = item.getString("display_name");
            if (StrUtil.isBlank(name)) {
                name = item.getString("keyword");
            }
            if (StrUtil.isNotBlank(name)) {
                values.add(name);
            }
        }
        return values;
    }

    private String normalizeDoi(String doi) {
        if (StrUtil.isBlank(doi)) {
            return null;
        }
        return doi.replace("https://doi.org/", "").trim();
    }
}
