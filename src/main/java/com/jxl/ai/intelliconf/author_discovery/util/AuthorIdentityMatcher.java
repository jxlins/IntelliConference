package com.jxl.ai.intelliconf.author_discovery.util;

import cn.hutool.core.util.StrUtil;
import com.jxl.ai.intelliconf.author_discovery.dto.AuthorCandidate;
import com.jxl.ai.intelliconf.author_discovery.dto.EmailEvidence;
import com.jxl.ai.intelliconf.author_discovery.dto.OpenAlexPaper;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Component
public class AuthorIdentityMatcher {

    private static final double BIND_THRESHOLD = 0.55;

    public double score(AuthorCandidate candidate, EmailEvidence evidence) {
        String pageText = normalizeText(evidence.getPageText());
        String evidenceText = normalizeText(evidence.getEvidenceText());
        String emailLocalPart = emailLocalPart(evidence.getEmail());
        List<String> nameParts = nameParts(candidate.getAuthorName());
        double score = 0.0;

        String normalizedName = AuthorDiscoveryTextUtil.normalizeName(candidate.getAuthorName());
        if (StrUtil.isNotBlank(normalizedName) && pageText.contains(normalizedName)) {
            score += 0.40;
        }
        String reversedName = reversedName(nameParts);
        if (StrUtil.isNotBlank(reversedName) && pageText.contains(reversedName)) {
            score += 0.30;
        }
        if (matchesInitialForm(pageText, nameParts)) {
            score += 0.20;
        }
        if (localPartContainsNamePart(emailLocalPart, nameParts)) {
            score += 0.25;
        }
        if (localPartOverlap(emailLocalPart, nameParts)) {
            score += 0.25;
        }
        if (StrUtil.isNotBlank(candidate.getOrganization())
                && pageText.contains(candidate.getOrganization().toLowerCase(Locale.ROOT))) {
            score += 0.20;
        }
        if (institutionKeywordMatch(pageText, candidate.getOrganization())) {
            score += 0.10;
        }
        if (representativeTitleMatch(pageText, candidate.getPapers())) {
            score += 0.20;
        }
        if (candidate.isCorresponding()) {
            score += 0.20;
        }
        if (evidenceText.contains("corresponding author")
                || evidenceText.contains("correspondence")
                || evidenceText.contains("email")) {
            score += 0.15;
        }
        return Math.min(score, 1.0);
    }

    public boolean canBindEmail(AuthorCandidate candidate, EmailEvidence evidence) {
        return score(candidate, evidence) >= BIND_THRESHOLD && !evidence.isGeneric();
    }

    public boolean canBindEmail(AuthorCandidate candidate, String pageText, String evidenceText) {
        EmailEvidence evidence = EmailEvidence.builder()
                .pageText(pageText)
                .evidenceText(evidenceText)
                .build();
        return score(candidate, evidence) >= BIND_THRESHOLD;
    }

    private String normalizeText(String value) {
        return AuthorDiscoveryTextUtil.normalizeName(StrUtil.blankToDefault(value, ""));
    }

    private List<String> nameParts(String name) {
        return Arrays.stream(AuthorDiscoveryTextUtil.normalizeName(name).split(" "))
                .filter(item -> item.length() > 1)
                .toList();
    }

    private String reversedName(List<String> parts) {
        if (parts.size() < 2) {
            return "";
        }
        return parts.get(parts.size() - 1) + " " + parts.get(0);
    }

    private boolean matchesInitialForm(String text, List<String> parts) {
        if (parts.size() < 2) {
            return false;
        }
        String first = parts.get(0);
        String last = parts.get(parts.size() - 1);
        String firstInitial = first.substring(0, 1);
        return text.contains(firstInitial + " " + last) || text.contains(last + " " + firstInitial);
    }

    private String emailLocalPart(String email) {
        if (StrUtil.isBlank(email) || !email.contains("@")) {
            return "";
        }
        return email.substring(0, email.indexOf('@')).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private boolean localPartContainsNamePart(String localPart, List<String> parts) {
        return parts.stream()
                .filter(item -> item.length() > 2)
                .anyMatch(item -> localPart.contains(item.replaceAll("[^a-z0-9]", "")));
    }

    private boolean localPartOverlap(String localPart, List<String> parts) {
        if (StrUtil.isBlank(localPart)) {
            return false;
        }
        int overlap = 0;
        for (String part : parts) {
            if (part.length() > 1 && localPart.contains(part.substring(0, Math.min(3, part.length())))) {
                overlap++;
            }
        }
        return overlap >= Math.min(2, parts.size());
    }

    private boolean institutionKeywordMatch(String pageText, String organization) {
        if (StrUtil.isBlank(organization)) {
            return false;
        }
        return Arrays.stream(AuthorDiscoveryTextUtil.normalizeName(organization).split(" "))
                .filter(item -> item.length() >= 5)
                .filter(item -> !List.of("university", "college", "school", "department").contains(item))
                .anyMatch(pageText::contains);
    }

    private boolean representativeTitleMatch(String pageText, List<OpenAlexPaper> papers) {
        return papers.stream()
                .map(OpenAlexPaper::getTitle)
                .map(AuthorDiscoveryTextUtil::titleCoreWords)
                .anyMatch(words -> words.stream().filter(pageText::contains).count() >= 2);
    }
}
