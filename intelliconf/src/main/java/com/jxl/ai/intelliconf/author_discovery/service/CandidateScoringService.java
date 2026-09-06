package com.jxl.ai.intelliconf.author_discovery.service;

import cn.hutool.core.util.StrUtil;
import com.jxl.ai.intelliconf.author_discovery.dto.AuthorCandidate;
import com.jxl.ai.intelliconf.author_discovery.dto.EmailEvidence;
import com.jxl.ai.intelliconf.author_discovery.dto.OpenAlexPaper;
import com.jxl.ai.intelliconf.author_discovery.enums.EmailSourceType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class CandidateScoringService {

    public void score(AuthorCandidate candidate, List<String> topicKeywords) {
        double topicSimilarity = calculateTopicSimilarity(candidate, topicKeywords);
        double emailConfidence = candidate.getEmailEvidences().stream()
                .mapToDouble(EmailEvidence::getConfidence)
                .max()
                .orElse(0.0);
        double activity = normalize(candidate.getRecentPaperCount(), 5);
        double citationOrCount = normalize(candidate.getPaperCount() + candidate.getTotalCitations() / 25.0, 10);
        double overall = topicSimilarity * 0.55 + emailConfidence * 0.30 + activity * 0.10 + citationOrCount * 0.05;
        candidate.setTopicSimilarity(clamp(topicSimilarity));
        candidate.setEmailConfidence(clamp(emailConfidence));
        candidate.setOverallScore(clamp(overall));
        candidate.getEmailEvidences().stream()
                .max(java.util.Comparator.comparingDouble(EmailEvidence::getConfidence)
                        .thenComparingDouble(EmailEvidence::getIdentityScore))
                .ifPresent(evidence -> candidate.setSelectedEmail(evidence.getEmail()));
    }

    public double calculateTopicSimilarity(AuthorCandidate candidate, List<String> topicKeywords) {
        if (topicKeywords == null || topicKeywords.isEmpty()) {
            return 0.0;
        }
        int totalHits = 0;
        for (OpenAlexPaper paper : candidate.getPapers()) {
            String text = (StrUtil.blankToDefault(paper.getTitle(), "") + " "
                    + String.join(" ", paper.getTopics()) + " "
                    + String.join(" ", paper.getKeywords())).toLowerCase(Locale.ROOT);
            for (String keyword : topicKeywords) {
                String normalized = StrUtil.blankToDefault(keyword, "").toLowerCase(Locale.ROOT);
                if (StrUtil.isNotBlank(normalized) && text.contains(normalized)) {
                    totalHits++;
                }
            }
        }
        double keywordCoverage = normalize(totalHits, Math.max(topicKeywords.size(), 1) * 2.0);
        double paperBoost = normalize(candidate.getPaperCount(), 4);
        return clamp(keywordCoverage * 0.8 + paperBoost * 0.2);
    }

    public double evidenceConfidence(EmailEvidence evidence, boolean corresponding) {
        if (evidence == null || evidence.isGeneric() || evidence.getIdentityScore() < 0.55) {
            return 0.30;
        }
        String sourceType = evidence.getSourceType();
        String text = (StrUtil.blankToDefault(evidence.getEvidenceText(), "") + " "
                + StrUtil.blankToDefault(evidence.getPageText(), "")).toLowerCase(Locale.ROOT);
        boolean correspondence = text.contains("corresponding author") || text.contains("correspondence");
        if (EmailSourceType.PAPER_PDF.name().equals(sourceType) && corresponding) {
            return 0.95;
        }
        if (EmailSourceType.PAPER_PDF.name().equals(sourceType) && correspondence) {
            return 0.95;
        }
        if (EmailSourceType.PAPER_PDF.name().equals(sourceType)) {
            return 0.85;
        }
        if (EmailSourceType.MAILTO.name().equals(sourceType)) {
            return 0.88;
        }
        if (EmailSourceType.HTML_BODY.name().equals(sourceType) || EmailSourceType.OPEN_ACCESS_PAGE.name().equals(sourceType)) {
            return 0.82;
        }
        if (EmailSourceType.PUBLISHER_PAGE.name().equals(sourceType)) {
            return 0.65;
        }
        return 0.30;
    }

    public double evidenceConfidence(String sourceType, boolean identityMatched, boolean corresponding) {
        return evidenceConfidence(EmailEvidence.builder()
                .sourceType(sourceType)
                .identityScore(identityMatched ? 0.55 : 0.0)
                .build(), corresponding);
    }

    private double normalize(double value, double max) {
        if (max <= 0) {
            return 0.0;
        }
        return clamp(value / max);
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
