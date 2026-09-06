package com.jxl.ai.intelliconf.author_discovery.service;

import cn.hutool.core.util.StrUtil;
import com.jxl.ai.intelliconf.author_discovery.config.AuthorDiscoveryProperties;
import com.jxl.ai.intelliconf.author_discovery.dto.EmailEvidence;
import com.jxl.ai.intelliconf.author_discovery.enums.EmailSourceType;
import com.jxl.ai.intelliconf.author_discovery.util.RobotsTxtChecker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class PublicEmailExtractor {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern OBFUSCATED_EMAIL_PATTERN = Pattern.compile(
            "([A-Za-z0-9._%+-]+)\\s*(?:\\[at\\]|\\(at\\)|\\{at\\}|\\s+at\\s+)\\s*([A-Za-z0-9-]+(?:\\s*(?:\\[dot\\]|\\(dot\\)|\\{dot\\}|\\s+dot\\s+|\\.)\\s*[A-Za-z0-9-]+)+)",
            Pattern.CASE_INSENSITIVE);
    private static final Set<String> GENERIC_LOCAL_PARTS = Set.of(
            "noreply", "no-reply", "support", "admin", "info", "editorial", "editor",
            "webmaster", "postmaster", "newsletter", "conference", "submission", "help", "contact");
    private static final Set<String> BLOCKED_DOMAINS = Set.of("example.com", "test.com", "invalid.com", "localhost");

    private final AuthorDiscoveryProperties properties;
    private final RobotsTxtChecker robotsTxtChecker;
    private final Map<String, List<EmailEvidence>> extractionCache = Collections.synchronizedMap(
            new LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, List<EmailEvidence>> eldest) {
                    return size() > 2000;
                }
            });
    /**
     * 共享 HttpClient：复用 TCP/TLS 连接（keep-alive），避免每个请求重新握手。
     * HttpClient 线程安全；connect 超时取较短值，慢主机快速失败交由熔断处理。
     */
    private volatile HttpClient sharedClient;
    /** 主机级熔断：连续连接失败达到阈值后跳过该主机，避免在不可达站点上反复白等超时 */
    private final Map<String, Integer> hostFailures = new java.util.concurrent.ConcurrentHashMap<>();
    private final Set<String> circuitOpenHosts = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private HttpClient client() {
        if (sharedClient == null) {
            synchronized (this) {
                if (sharedClient == null) {
                    sharedClient = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(Math.min(5, properties.getExtractionTimeoutSeconds())))
                            .followRedirects(HttpClient.Redirect.NORMAL)
                            .build();
                }
            }
        }
        return sharedClient;
    }

    private String hostKey(String url) {
        try {
            URI uri = URI.create(url);
            return uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        } catch (Exception ex) {
            return "";
        }
    }

    private boolean isCircuitOpen(String host) {
        return StrUtil.isNotBlank(host) && circuitOpenHosts.contains(host);
    }

    private void recordSuccess(String host) {
        if (StrUtil.isNotBlank(host)) {
            hostFailures.remove(host);
        }
    }

    private void recordFailure(String host) {
        if (StrUtil.isBlank(host)) {
            return;
        }
        int failures = hostFailures.merge(host, 1, Integer::sum);
        if (failures >= Math.max(2, properties.getHostFailureThreshold()) && circuitOpenHosts.add(host)) {
            log.info("Circuit opened for unreachable host: {} ({} consecutive failures)", host, failures);
        }
    }

    public List<EmailEvidence> extractFromUrls(List<String> urls) {
        List<EmailEvidence> evidences = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String url : urls) {
            if (Thread.currentThread().isInterrupted()) break;
            if (StrUtil.isBlank(url) || !seen.add(url) || !isAllowedMetadataUrl(url)) {
                continue;
            }
            if (isCircuitOpen(hostKey(url))) {
                continue;
            }
            if (!robotsTxtChecker.isAllowed(url)) {
                continue;
            }
            try {
                List<EmailEvidence> cached = extractionCache.get(url);
                if (cached != null) {
                    evidences.addAll(copyEvidences(cached));
                    continue;
                }
                List<EmailEvidence> extracted = extractFromUrl(url);
                extractionCache.put(url, copyEvidences(extracted));
                evidences.addAll(extracted);
            } catch (Exception ex) {
                log.warn("Email extraction failed, url={}, errorType={}", url, ex.getClass().getSimpleName());
            }
        }
        return deduplicate(evidences);
    }

    public List<EmailEvidence> extractFromHtmlText(String html, String sourceUrl, String sourceType) {
        Document document = Jsoup.parse(StrUtil.blankToDefault(html, ""), sourceUrl);
        document.select("script,style,noscript").remove();
        String pageText = normalizeEmailObfuscation(document.text());
        List<EmailEvidence> evidences = new ArrayList<>();
        evidences.addAll(extractFromPlainText(pageText, sourceUrl, EmailSourceType.HTML_BODY.name()));
        evidences.addAll(extractMailtoEmails(document, pageText, sourceUrl));
        return deduplicate(evidences);
    }

    public List<EmailEvidence> extractFromPlainText(String text, String sourceUrl, String sourceType) {
        String normalizedText = normalizeEmailObfuscation(Jsoup.parse(StrUtil.blankToDefault(text, "")).text());
        List<EmailEvidence> evidences = new ArrayList<>();
        Matcher matcher = EMAIL_PATTERN.matcher(normalizedText);
        while (matcher.find()) {
            String email = normalizeEmail(matcher.group());
            if (StrUtil.isBlank(email) || isBlockedEmail(email)) {
                continue;
            }
            evidences.add(EmailEvidence.builder()
                    .email(email)
                    .sourceType(sourceType)
                    .sourceUrl(sourceUrl)
                    .evidenceText(extractEvidence(normalizedText, matcher.start(), matcher.end()))
                    .pageText(StrUtil.maxLength(normalizedText, 20000))
                    .confidence(defaultConfidence(sourceType, normalizedText))
                    .generic(isGenericEmail(email))
                    .build());
        }
        return deduplicate(evidences);
    }

    public List<String> discoverPdfLinks(String html, String baseUrl) {
        Document document = Jsoup.parse(StrUtil.blankToDefault(html, ""), baseUrl);
        Set<String> links = new LinkedHashSet<>();
        for (Element meta : document.select("meta[name=citation_pdf_url],meta[name=DC.identifier]")) {
            String value = meta.attr("content");
            try {
                String link = URI.create(baseUrl).resolve(value).toString();
                if (isPdfLink(link, "") && isAllowedMetadataUrl(link)) links.add(link);
            } catch (IllegalArgumentException ignored) { }
        }
        for (Element anchor : document.select("a[href]")) {
            String href = anchor.attr("abs:href");
            String text = anchor.text();
            if (isPdfLink(href, text) && isAllowedMetadataUrl(href)) {
                links.add(href);
            }
            if (links.size() >= properties.getMaxDiscoveredPdfPerPage()) {
                break;
            }
        }
        return new ArrayList<>(links);
    }

    private List<EmailEvidence> extractFromUrl(String url) throws Exception {
        if (looksLikePdfUrl(url)) {
            return extractFromPdfUrl(url);
        }

        HttpResponse<byte[]> response = get(url, properties.getMaxHtmlBytes());
        if (response.statusCode() >= 400 || response.body().length > properties.getMaxHtmlBytes()) {
            return List.of();
        }
        String contentType = response.headers().firstValue("content-type").orElse("").toLowerCase(Locale.ROOT);
        if (contentType.contains("pdf")) {
            return extractPdfBytes(response.body(), url);
        }
        if (StrUtil.isNotBlank(contentType) && !contentType.contains("html") && !contentType.contains("text")) {
            return List.of();
        }

        String html = new String(response.body(), StandardCharsets.UTF_8);
        List<EmailEvidence> htmlEvidences = extractFromHtmlText(html, url, EmailSourceType.HTML_BODY.name());
        List<EmailEvidence> all = new ArrayList<>(htmlEvidences);
        for (String pdfUrl : discoverPdfLinks(html, url)) {
            if (!robotsTxtChecker.isAllowed(pdfUrl)) {
                continue;
            }
            try {
                all.addAll(extractFromPdfUrl(pdfUrl));
            } catch (Exception ex) {
                log.warn("PDF email extraction failed, url={}, errorType={}", pdfUrl, ex.getClass().getSimpleName());
            }
        }
        return deduplicate(all);
    }

    private List<EmailEvidence> extractFromPdfUrl(String url) throws Exception {
        HttpResponse<byte[]> response = get(url, properties.getMaxPdfBytes());
        if (response.statusCode() >= 400 || response.body().length > properties.getMaxPdfBytes()) {
            return List.of();
        }
        String contentType = response.headers().firstValue("content-type").orElse("").toLowerCase(Locale.ROOT);
        if (StrUtil.isNotBlank(contentType) && !contentType.contains("pdf") && !looksLikePdfUrl(url)) {
            return List.of();
        }
        return extractPdfBytes(response.body(), url);
    }

    private List<EmailEvidence> extractPdfBytes(byte[] pdfBytes, String sourceUrl) {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(Math.min(properties.getPdfParseMaxPages(), Math.max(document.getNumberOfPages(), 1)));
            String text = stripper.getText(document);
            return extractFromPlainText(text, sourceUrl, EmailSourceType.PAPER_PDF.name());
        } catch (Exception ex) {
            log.warn("PDFBox parse failed, url={}, errorType={}", sourceUrl, ex.getClass().getSimpleName());
            return List.of();
        }
    }

    private List<EmailEvidence> extractMailtoEmails(Document document, String pageText, String sourceUrl) {
        List<EmailEvidence> evidences = new ArrayList<>();
        for (Element anchor : document.select("a[href^=mailto:]")) {
            String href = anchor.attr("href");
            String mailto = href.substring("mailto:".length());
            int queryIndex = mailto.indexOf('?');
            if (queryIndex >= 0) {
                mailto = mailto.substring(0, queryIndex);
            }
            String decoded = decode(mailto);
            Matcher matcher = EMAIL_PATTERN.matcher(decoded.replace(';', ','));
            while (matcher.find()) {
                String email = normalizeEmail(matcher.group());
                if (StrUtil.isBlank(email) || isBlockedEmail(email)) {
                    continue;
                }
                String evidenceText = evidenceForMailto(pageText, email, anchor.text());
                evidences.add(EmailEvidence.builder()
                        .email(email)
                        .sourceType(EmailSourceType.MAILTO.name())
                        .sourceUrl(sourceUrl)
                        .evidenceText(evidenceText)
                        .pageText(StrUtil.maxLength(pageText, 20000))
                        .confidence(defaultConfidence(EmailSourceType.MAILTO.name(), pageText))
                        .generic(isGenericEmail(email))
                        .build());
            }
        }
        return evidences;
    }

    private HttpResponse<byte[]> get(String url, int maxBytes) throws Exception {
        String host = hostKey(url);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(properties.getExtractionTimeoutSeconds()))
                .header("User-Agent", properties.getUserAgent())
                .GET()
                .build();
        try {
            HttpResponse<byte[]> response = client().send(request, HttpResponse.BodyHandlers.ofByteArray());
            recordSuccess(host);
            if (response.body().length > maxBytes) {
                log.warn("Downloaded content exceeds size limit, url={}, size={}", url, response.body().length);
            }
            return response;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw ex;
        } catch (Exception ex) {
            // 连接重置/超时/未知主机等一律计入熔断；达到阈值后该主机直接跳过
            recordFailure(host);
            throw ex;
        }
    }

    private String evidenceForMailto(String pageText, String email, String anchorText) {
        String text = StrUtil.blankToDefault(pageText, "");
        String lower = text.toLowerCase(Locale.ROOT);
        String emailLower = email.toLowerCase(Locale.ROOT);
        int index = lower.indexOf(emailLower);
        if (index < 0) {
            String local = emailLower.split("@", 2)[0];
            index = lower.indexOf(local);
        }
        if (index < 0 && StrUtil.isNotBlank(anchorText)) {
            index = lower.indexOf(anchorText.toLowerCase(Locale.ROOT));
        }
        if (index < 0) {
            return StrUtil.maxLength(text, 240);
        }
        return extractEvidence(text, index, Math.min(text.length(), index + email.length()));
    }

    private String normalizeEmailObfuscation(String text) {
        Matcher grouped = Pattern.compile("\\{([A-Za-z0-9._%+\\-,;\\s]+)\\}\\s*@\\s*([A-Za-z0-9.-]+\\.[A-Za-z]{2,})").matcher(StrUtil.blankToDefault(text, ""));
        StringBuffer expanded = new StringBuffer();
        while (grouped.find()) {
            String domain = grouped.group(2);
            String emails = java.util.Arrays.stream(grouped.group(1).split("[,;]"))
                    .map(String::trim).filter(s -> s.matches("[A-Za-z0-9._%+\\-]+"))
                    .map(s -> s + "@" + domain).collect(java.util.stream.Collectors.joining(" "));
            grouped.appendReplacement(expanded, Matcher.quoteReplacement(emails));
        }
        grouped.appendTail(expanded);
        text = expanded.toString();
        Matcher matcher = OBFUSCATED_EMAIL_PATTERN.matcher(StrUtil.blankToDefault(text, ""));
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String localPart = matcher.group(1);
            String domain = matcher.group(2)
                    .replaceAll("(?i)\\s*(?:\\[dot\\]|\\(dot\\)|\\{dot\\}|\\s+dot\\s+|\\.)\\s*", ".")
                    .replaceAll("\\s+", "");
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(localPart + "@" + domain));
        }
        matcher.appendTail(buffer);
        return buffer.toString()
                .replace("&#64;", "@")
                .replace("&#x40;", "@")
                .replace("&commat;", "@")
                .replace("&#46;", ".")
                .replace("&#x2e;", ".")
                .replace("&period;", ".")
                .replaceAll("\\s*@\\s*", "@");
    }

    private String extractEvidence(String text, int start, int end) {
        int from = Math.max(0, start - 120);
        int to = Math.min(text.length(), end + 180);
        return text.substring(from, to).replaceAll("\\s+", " ").trim();
    }

    private boolean hasPromisingEvidence(List<EmailEvidence> evidences) {
        return evidences.stream().anyMatch(item -> !item.isGeneric() && item.getConfidence() >= 0.75);
    }

    private boolean isPdfLink(String href, String text) {
        String lowerHref = StrUtil.blankToDefault(href, "").toLowerCase(Locale.ROOT);
        String lowerText = StrUtil.blankToDefault(text, "").toLowerCase(Locale.ROOT);
        return lowerHref.endsWith(".pdf")
                || lowerHref.contains("/pdf")
                || lowerHref.contains("article-pdf")
                || (lowerHref.contains("download") && lowerText.contains("pdf"))
                || lowerText.contains("pdf")
                || lowerText.contains("download pdf")
                || lowerText.contains("view pdf")
                || lowerText.contains("full text pdf");
    }

    private boolean looksLikePdfUrl(String url) {
        String lower = StrUtil.blankToDefault(url, "").toLowerCase(Locale.ROOT);
        return lower.endsWith(".pdf") || lower.contains("/pdf") || lower.contains("article-pdf");
    }

    private boolean isAllowedMetadataUrl(String url) {
        String lower = StrUtil.blankToDefault(url, "").toLowerCase(Locale.ROOT);
        if (!(lower.startsWith("http://") || lower.startsWith("https://"))) {
            return false;
        }
        return !(lower.contains("google.") || lower.contains("bing.com") || lower.contains("baidu.com")
                || lower.contains("login") || lower.contains("signin") || lower.contains("captcha")
                || lower.contains("paywall"));
    }

    private double defaultConfidence(String sourceType, String text) {
        String lower = StrUtil.blankToDefault(text, "").toLowerCase(Locale.ROOT);
        boolean correspondence = lower.contains("corresponding author")
                || lower.contains("correspondence")
                || lower.contains("email");
        if (EmailSourceType.PAPER_PDF.name().equals(sourceType)) {
            return correspondence ? 0.90 : 0.85;
        }
        if (EmailSourceType.MAILTO.name().equals(sourceType)) {
            return 0.85;
        }
        if (EmailSourceType.HTML_BODY.name().equals(sourceType) || EmailSourceType.OPEN_ACCESS_PAGE.name().equals(sourceType)) {
            return 0.80;
        }
        if (EmailSourceType.PUBLISHER_PAGE.name().equals(sourceType)) {
            return 0.65;
        }
        return 0.30;
    }

    private List<EmailEvidence> deduplicate(List<EmailEvidence> evidences) {
        List<EmailEvidence> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (EmailEvidence evidence : evidences) {
            String email = normalizeEmail(evidence.getEmail());
            if (StrUtil.isBlank(email)) {
                continue;
            }
            evidence.setEmail(email);
            String key = email + "|" + evidence.getSourceUrl() + "|" + evidence.getSourceType();
            if (seen.add(key)) {
                result.add(evidence);
            }
        }
        return result;
    }

    private boolean isBlockedEmail(String email) {
        String[] parts = StrUtil.blankToDefault(email, "").split("@", 2);
        if (parts.length != 2) {
            return true;
        }
        String domain = parts[1].toLowerCase(Locale.ROOT);
        return BLOCKED_DOMAINS.contains(domain);
    }

    private boolean isGenericEmail(String email) {
        String[] parts = StrUtil.blankToDefault(email, "").split("@", 2);
        return parts.length == 2 && GENERIC_LOCAL_PARTS.contains(parts[0].toLowerCase(Locale.ROOT));
    }

    private String normalizeEmail(String email) {
        if (StrUtil.isBlank(email)) {
            return null;
        }
        return email.trim().replaceAll("^[<('\"]+", "").replaceAll("[>)'\".,;:]+$", "").toLowerCase(Locale.ROOT);
    }

    private String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return value;
        }
    }

    private List<EmailEvidence> copyEvidences(List<EmailEvidence> source) {
        return source.stream().map(item -> EmailEvidence.builder()
                .email(item.getEmail())
                .sourceType(item.getSourceType())
                .sourceUrl(item.getSourceUrl())
                .evidenceText(item.getEvidenceText())
                .pageText(item.getPageText())
                .generic(item.isGeneric())
                .identityScore(item.getIdentityScore())
                .confidence(item.getConfidence())
                .build()).toList();
    }
}
