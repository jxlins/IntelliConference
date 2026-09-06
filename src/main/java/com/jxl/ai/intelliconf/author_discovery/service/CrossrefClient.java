package com.jxl.ai.intelliconf.author_discovery.service;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONObject;
import com.jxl.ai.intelliconf.author_discovery.config.AuthorDiscoveryProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CrossrefClient {

    private final AuthorDiscoveryProperties properties;

    public List<String> findMetadataUrls(String doi) {
        if (StrUtil.isBlank(doi)) {
            return List.of();
        }
        try {
            String url = properties.getCrossrefBaseUrl() + "/works/" + URLEncoder.encode(doi, StandardCharsets.UTF_8);
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(properties.getRequestTimeoutSeconds()))
                    .build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(properties.getRequestTimeoutSeconds()))
                    .header("User-Agent", properties.getUserAgent())
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Crossref DOI lookup returned HTTP {}", response.statusCode());
                return List.of();
            }
            JSONObject message = JSONObject.parseObject(response.body()).getJSONObject("message");
            if (message == null) {
                return List.of();
            }
            List<String> urls = new ArrayList<>();
            if (StrUtil.isNotBlank(message.getString("URL"))) {
                urls.add(message.getString("URL"));
            }
            return urls;
        } catch (Exception ex) {
            log.warn("Crossref lookup failed for DOI {}", doi);
            return List.of();
        }
    }
}
