package com.jxl.ai.intelliconf.author_discovery.util;

import cn.hutool.core.util.StrUtil;
import com.jxl.ai.intelliconf.author_discovery.config.AuthorDiscoveryProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class RobotsTxtChecker {

    private final AuthorDiscoveryProperties properties;
    private final Map<String, RobotsRules> cache = new ConcurrentHashMap<>();
    /** 共享 HttpClient，复用连接；robots.txt 使用独立短超时，避免慢主机拖住整批抓取 */
    private volatile HttpClient sharedClient;

    private HttpClient client() {
        if (sharedClient == null) {
            synchronized (this) {
                if (sharedClient == null) {
                    sharedClient = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(Math.min(5, properties.getRobotsTimeoutSeconds())))
                            .build();
                }
            }
        }
        return sharedClient;
    }

    public boolean isAllowed(String targetUrl) {
        if (!properties.isRespectRobots()) {
            return true;
        }
        try {
            URI target = URI.create(targetUrl);
            String hostKey = target.getScheme() + "://" + target.getHost() + (target.getPort() > 0 ? ":" + target.getPort() : "");
            RobotsRules rules = cache.computeIfAbsent(hostKey, this::fetchRules);
            String path = StrUtil.blankToDefault(target.getRawPath(), "/");
            return rules.isAllowed(path);
        } catch (Exception ex) {
            log.warn("Skip URL because robots check failed: {}, errorType={}", targetUrl, ex.getClass().getSimpleName());
            return allowOnFailure();
        }
    }

    private RobotsRules fetchRules(String hostKey) {
        String robotsUrl = hostKey + "/robots.txt";
        HttpRequest request = HttpRequest.newBuilder(URI.create(robotsUrl))
                .timeout(Duration.ofSeconds(properties.getRobotsTimeoutSeconds()))
                .header("User-Agent", properties.getUserAgent())
                .GET()
                .build();
        try {
            HttpResponse<String> response = client().send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status == 404 || status == 410) {
                return RobotsRules.allowAll();
            }
            if (status == 403) {
                return RobotsRules.denyAll();
            }
            if (status >= 500 || status >= 400) {
                return allowOnFailure() ? RobotsRules.allowAll() : RobotsRules.denyAll();
            }
            return RobotsRules.parse(response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Failed to fetch robots.txt, host={}, errorType={}", hostKey, ex.getClass().getSimpleName());
            return allowOnFailure() ? RobotsRules.allowAll() : RobotsRules.denyAll();
        } catch (IOException ex) {
            log.warn("Failed to fetch robots.txt, host={}, errorType={}", hostKey, ex.getClass().getSimpleName());
            return allowOnFailure() ? RobotsRules.allowAll() : RobotsRules.denyAll();
        }
    }

    private boolean allowOnFailure() {
        return "ALLOW".equalsIgnoreCase(properties.getRobotsFailurePolicy());
    }

    private record RobotsRules(List<Rule> rules, boolean defaultAllow) {
        static RobotsRules allowAll() {
            return new RobotsRules(List.of(), true);
        }

        static RobotsRules denyAll() {
            return new RobotsRules(List.of(), false);
        }

        static RobotsRules parse(String content) {
            List<Rule> rules = new ArrayList<>();
            boolean applies = false;
            for (String rawLine : StrUtil.blankToDefault(content, "").split("\\R")) {
                String line = rawLine.split("#", 2)[0].trim();
                if (line.isEmpty()) {
                    continue;
                }
                String lower = line.toLowerCase(Locale.ROOT);
                if (lower.startsWith("user-agent:")) {
                    String agent = line.substring(line.indexOf(':') + 1).trim();
                    applies = "*".equals(agent);
                    continue;
                }
                if (!applies) {
                    continue;
                }
                if (lower.startsWith("allow:")) {
                    String path = line.substring(line.indexOf(':') + 1).trim();
                    if (StrUtil.isNotBlank(path)) {
                        rules.add(new Rule(path, true));
                    }
                } else if (lower.startsWith("disallow:")) {
                    String path = line.substring(line.indexOf(':') + 1).trim();
                    if (StrUtil.isNotBlank(path)) {
                        rules.add(new Rule(path, false));
                    }
                }
            }
            return new RobotsRules(rules, true);
        }

        boolean isAllowed(String path) {
            if (rules.isEmpty()) {
                return defaultAllow;
            }
            return rules.stream()
                    .filter(rule -> path.startsWith(rule.path()))
                    .max(Comparator.comparingInt(rule -> rule.path().length()))
                    .map(Rule::allow)
                    .orElse(defaultAllow);
        }
    }

    private record Rule(String path, boolean allow) {
    }
}
