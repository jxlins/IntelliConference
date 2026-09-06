package com.jxl.ai.intelliconf.author_discovery.service;

import cn.hutool.core.util.StrUtil;
import com.jxl.ai.intelliconf.author_discovery.dto.AuthorCandidate;
import com.jxl.ai.intelliconf.author_discovery.dto.OpenAlexAuthor;
import com.jxl.ai.intelliconf.author_discovery.dto.OpenAlexPaper;
import com.jxl.ai.intelliconf.author_discovery.util.AuthorDiscoveryTextUtil;
import org.springframework.stereotype.Component;

import java.time.Year;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AuthorCandidateBuilder {

    public List<AuthorCandidate> buildCandidates(List<OpenAlexPaper> papers, int maxAuthors) {
        Map<String, MutableCandidate> candidateMap = new LinkedHashMap<>();
        int currentYear = Year.now().getValue();
        for (OpenAlexPaper paper : papers) {
            for (OpenAlexAuthor author : paper.getAuthors()) {
                if (StrUtil.isBlank(author.getDisplayName())) {
                    continue;
                }
                String normalizedName = AuthorDiscoveryTextUtil.normalizeName(author.getDisplayName());
                String key = normalizedName + "|" + StrUtil.blankToDefault(author.getInstitution(), "").toLowerCase();
                MutableCandidate mutable = candidateMap.computeIfAbsent(key, item -> new MutableCandidate(author.getDisplayName(), normalizedName));
                mutable.papers.add(paper);
                mutable.countryRegion = StrUtil.blankToDefault(author.getCountryCode(), mutable.countryRegion);
                mutable.corresponding = mutable.corresponding || author.isCorresponding();
                if (StrUtil.isNotBlank(author.getInstitution())) {
                    mutable.institutionCount.merge(author.getInstitution(), 1, Integer::sum);
                }
                if (paper.getCitedByCount() != null) {
                    mutable.totalCitations += paper.getCitedByCount();
                }
                if (paper.getPublicationYear() != null && paper.getPublicationYear() >= currentYear - 5) {
                    mutable.recentPaperCount++;
                }
                mutable.keywords.addAll(paper.getTopics());
                mutable.keywords.addAll(paper.getKeywords());
            }
        }
        return candidateMap.values().stream()
                .map(MutableCandidate::toCandidate)
                .sorted((left, right) -> Integer.compare(right.getPaperCount(), left.getPaperCount()))
                .limit(Math.max(maxAuthors, 1))
                .toList();
    }

    private static class MutableCandidate {
        private final String authorName;
        private final String normalizedName;
        private final List<OpenAlexPaper> papers = new ArrayList<>();
        private final Map<String, Integer> institutionCount = new HashMap<>();
        private final List<String> keywords = new ArrayList<>();
        private String countryRegion;
        private boolean corresponding;
        private int totalCitations;
        private int recentPaperCount;

        private MutableCandidate(String authorName, String normalizedName) {
            this.authorName = authorName;
            this.normalizedName = normalizedName;
        }

        private AuthorCandidate toCandidate() {
            String organization = institutionCount.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);
            String sourceUrl = papers.stream()
                    .map(OpenAlexPaper::getOpenAccessLandingPageUrl)
                    .filter(StrUtil::isNotBlank)
                    .findFirst()
                    .orElseGet(() -> papers.stream().map(OpenAlexPaper::getLandingPageUrl).filter(StrUtil::isNotBlank).findFirst().orElse(null));
            return AuthorCandidate.builder()
                    .authorName(authorName)
                    .normalizedName(normalizedName)
                    .organization(organization)
                    .countryRegion(countryRegion)
                    .corresponding(corresponding)
                    .paperCount(papers.size())
                    .recentPaperCount(recentPaperCount)
                    .totalCitations(totalCitations)
                    .sourceUrl(sourceUrl)
                    .researchKeywords(keywords.stream().distinct().limit(20).toList())
                    .papers(papers)
                    .build();
        }
    }
}
