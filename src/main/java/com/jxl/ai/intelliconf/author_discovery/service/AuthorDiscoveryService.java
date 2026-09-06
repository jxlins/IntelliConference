package com.jxl.ai.intelliconf.author_discovery.service;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jxl.ai.intelliconf.author_discovery.dto.AuthorCandidate;
import com.jxl.ai.intelliconf.author_discovery.dto.AuthorDiscoveryCommand;
import com.jxl.ai.intelliconf.author_discovery.dto.AuthorDiscoveryJobRespDTO;
import com.jxl.ai.intelliconf.author_discovery.dto.EmailEvidence;
import com.jxl.ai.intelliconf.author_discovery.dto.OpenAlexPaper;
import com.jxl.ai.intelliconf.author_discovery.dto.PotentialAuthorQueryDTO;
import com.jxl.ai.intelliconf.author_discovery.dto.PotentialAuthorRespDTO;
import com.jxl.ai.intelliconf.author_discovery.dto.RunAuthorDiscoveryResultDTO;
import com.jxl.ai.intelliconf.author_discovery.entity.AuthorDiscoveryJobDO;
import com.jxl.ai.intelliconf.author_discovery.entity.PotentialAuthorDO;
import com.jxl.ai.intelliconf.author_discovery.enums.ContactStatus;
import com.jxl.ai.intelliconf.author_discovery.enums.DiscoveryJobStatus;
import com.jxl.ai.intelliconf.author_discovery.enums.ReviewStatus;
import com.jxl.ai.intelliconf.author_discovery.repository.AuthorDiscoveryJobMapper;
import com.jxl.ai.intelliconf.author_discovery.repository.PotentialAuthorMapper;
import com.jxl.ai.intelliconf.author_discovery.repository.PotentialAuthorRepository;
import com.jxl.ai.intelliconf.author_discovery.util.AuthorIdentityMatcher;
import com.jxl.ai.intelliconf.common.convention.exception.ClientException;
import com.jxl.ai.intelliconf.dao.entity.ConfContactPoolDO;
import com.jxl.ai.intelliconf.dao.entity.ConfMemberDO;
import com.jxl.ai.intelliconf.dao.entity.ConferenceDO;
import com.jxl.ai.intelliconf.dao.mapper.ConfContactPoolMapper;
import com.jxl.ai.intelliconf.dao.mapper.ConfMemberMapper;
import com.jxl.ai.intelliconf.dao.mapper.ConferenceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Year;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthorDiscoveryService {

    private static final String MEMBER_ROLE_PROSPECT = "PROSPECT";

    private final AuthorDiscoveryJobMapper jobMapper;
    private final PotentialAuthorMapper potentialAuthorMapper;
    private final ConferenceMapper conferenceMapper;
    private final ConfContactPoolMapper contactPoolMapper;
    private final ConfMemberMapper memberMapper;
    private final OpenAlexClient openAlexClient;
    private final CrossrefClient crossrefClient;
    private final AuthorCandidateBuilder candidateBuilder;
    private final PublicEmailExtractor emailExtractor;
    private final CandidateScoringService scoringService;
    private final AuthorIdentityMatcher identityMatcher;
    private final PotentialAuthorRepository potentialAuthorRepository;

    public AuthorDiscoveryJobDO createJob(AuthorDiscoveryCommand command) {
        List<String> keywords = resolveKeywords(command.getConferenceId(), command.getTopicKeywords());
        if (keywords.isEmpty()) {
            throw new IllegalArgumentException("topicKeywords is required when conference topic fields are empty");
        }
        Date now = new Date();
        AuthorDiscoveryJobDO job = AuthorDiscoveryJobDO.builder()
                .conferenceId(command.getConferenceId())
                .topicKeywords(JSON.toJSONString(keywords))
                .yearFrom(command.getYearFrom() == null ? Year.now().getValue() - 5 : command.getYearFrom())
                .yearTo(command.getYearTo() == null ? Year.now().getValue() : command.getYearTo())
                .maxAuthors(command.getMaxAuthors() == null ? 200 : command.getMaxAuthors())
                .status(DiscoveryJobStatus.PENDING.name())
                .totalPapers(0)
                .totalCandidates(0)
                .highConfidenceEmails(0)
                .createdBy(command.getCreatedBy())
                .createdAt(now)
                .build();
        jobMapper.insert(job);
        return job;
    }

    public RunAuthorDiscoveryResultDTO runJob(Long jobId, boolean enableCrossref, boolean enableEmailExtraction) {
        AuthorDiscoveryJobDO job = jobMapper.selectById(jobId);
        if (job == null) {
            throw new IllegalArgumentException("Author discovery job not found");
        }
        markRunning(jobId);
        try {
            List<String> keywords = JSON.parseArray(job.getTopicKeywords(), String.class);
            List<OpenAlexPaper> papers = openAlexClient.searchWorks(keywords, job.getYearFrom(), job.getYearTo(), job.getMaxAuthors());
            List<AuthorCandidate> candidates = candidateBuilder.buildCandidates(papers, job.getMaxAuthors());
            int savedCount = 0;
            int highConfidenceEmails = 0;
            for (AuthorCandidate candidate : candidates) {
                try {
                    if (enableEmailExtraction) {
                        attachEmailEvidence(candidate, enableCrossref);
                    }
                    scoringService.score(candidate, keywords);
                    PotentialAuthorDO candidateDO = toPotentialAuthor(job, candidate);
                    if (StrUtil.isBlank(candidateDO.getEmail())) {
                        continue;
                    }
                    PotentialAuthorDO saved = potentialAuthorRepository.saveOrUpdateCandidate(candidateDO, candidate.getEmailEvidences());
                    savedCount++;
                    if (saved.getEmail() != null && candidate.getEmailConfidence() >= 0.8) {
                        highConfidenceEmails++;
                    }
                } catch (Exception ex) {
                    log.warn("Failed to process candidate {}", candidate.getAuthorName(), ex);
                }
            }
            markCompleted(jobId, papers.size(), savedCount, highConfidenceEmails);
            return RunAuthorDiscoveryResultDTO.builder()
                    .jobId(jobId)
                    .status(DiscoveryJobStatus.COMPLETED.name())
                    .totalCandidates(savedCount)
                    .highConfidenceEmails(highConfidenceEmails)
                    .build();
        } catch (Exception ex) {
            markFailed(jobId, StrUtil.maxLength(ex.getMessage(), 2000));
            throw ex;
        }
    }

    public AuthorDiscoveryJobRespDTO getJob(Long jobId) {
        AuthorDiscoveryJobDO job = jobMapper.selectById(jobId);
        if (job == null) {
            throw new IllegalArgumentException("Author discovery job not found");
        }
        return toJobResp(job);
    }

    public List<PotentialAuthorRespDTO> listCandidates(Long conferenceId, PotentialAuthorQueryDTO query) {
        return potentialAuthorRepository.listCandidates(conferenceId, query).stream()
                .map(this::toPotentialAuthorResp)
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public void approveCandidate(Long authorId) {
        PotentialAuthorDO author = potentialAuthorMapper.selectById(authorId);
        if (author == null) {
            throw new ClientException("Potential author not found");
        }
        if (StrUtil.isBlank(author.getEmail())) {
            throw new ClientException("Potential author has no trusted email");
        }
        upsertProspectMember(author);
        potentialAuthorRepository.approveCandidate(authorId);
    }

    public void rejectCandidate(Long authorId) {
        potentialAuthorRepository.rejectCandidate(authorId);
    }

    public void markRunning(Long jobId) {
        AuthorDiscoveryJobDO update = AuthorDiscoveryJobDO.builder()
                .id(jobId)
                .status(DiscoveryJobStatus.RUNNING.name())
                .startedAt(new Date())
                .build();
        jobMapper.updateById(update);
    }

    public void markCompleted(Long jobId, int totalPapers, int totalCandidates, int highConfidenceEmails) {
        AuthorDiscoveryJobDO update = AuthorDiscoveryJobDO.builder()
                .id(jobId)
                .status(DiscoveryJobStatus.COMPLETED.name())
                .totalPapers(totalPapers)
                .totalCandidates(totalCandidates)
                .highConfidenceEmails(highConfidenceEmails)
                .finishedAt(new Date())
                .build();
        jobMapper.updateById(update);
    }

    public void markFailed(Long jobId, String errorMessage) {
        AuthorDiscoveryJobDO update = AuthorDiscoveryJobDO.builder()
                .id(jobId)
                .status(DiscoveryJobStatus.FAILED.name())
                .errorMessage(errorMessage)
                .finishedAt(new Date())
                .build();
        jobMapper.updateById(update);
    }

    private void attachEmailEvidence(AuthorCandidate candidate, boolean enableCrossref) {
        List<String> urls = new ArrayList<>();
        for (OpenAlexPaper paper : candidate.getPapers()) {
            addIfPresent(urls, paper.getOpenAccessLandingPageUrl());
            addIfPresent(urls, paper.getLandingPageUrl());
            addIfPresent(urls, paper.getPdfUrl());
            if (enableCrossref) {
                urls.addAll(crossrefClient.findMetadataUrls(paper.getDoi()));
            }
        }
        List<EmailEvidence> rawEvidences = emailExtractor.extractFromUrls(urls);
        for (EmailEvidence evidence : rawEvidences) {
            double identityScore = identityMatcher.score(candidate, evidence);
            evidence.setIdentityScore(identityScore);
            evidence.setConfidence(scoringService.evidenceConfidence(evidence, candidate.isCorresponding()));
            candidate.getEmailEvidences().add(evidence);
        }
    }

    private PotentialAuthorDO toPotentialAuthor(AuthorDiscoveryJobDO job, AuthorCandidate candidate) {
        String representativePapers = JSON.toJSONString(candidate.getPapers().stream()
                .limit(5)
                .map(paper -> JSON.parseObject(JSON.toJSONString(paper)))
                .toList());
        String selectedEmail = candidate.getEmailConfidence() > 0.30 ? candidate.getSelectedEmail() : null;
        return PotentialAuthorDO.builder()
                .conferenceId(job.getConferenceId())
                .discoveryJobId(job.getId())
                .authorName(candidate.getAuthorName())
                .normalizedName(candidate.getNormalizedName())
                .email(selectedEmail)
                .organization(candidate.getOrganization())
                .countryRegion(candidate.getCountryRegion())
                .researchKeywords(JSON.toJSONString(candidate.getResearchKeywords()))
                .representativePapers(representativePapers)
                .sourcePlatform("OPENALEX")
                .sourceUrl(candidate.getSourceUrl())
                .topicSimilarity(BigDecimal.valueOf(candidate.getTopicSimilarity()))
                .emailConfidence(BigDecimal.valueOf(candidate.getEmailConfidence()))
                .overallScore(BigDecimal.valueOf(candidate.getOverallScore()))
                .reviewStatus(ReviewStatus.PENDING.name())
                .contactStatus(ContactStatus.NOT_CONTACTED.name())
                .build();
    }

    private void upsertProspectMember(PotentialAuthorDO author) {
        ConferenceDO conference = conferenceMapper.selectById(author.getConferenceId());
        Long ownerOrgId = resolveOwnerOrgId(conference, author.getConferenceId());
        String email = normalizeEmail(author.getEmail());

        ConfContactPoolDO contact = contactPoolMapper.selectOne(Wrappers.lambdaQuery(ConfContactPoolDO.class)
                .eq(ConfContactPoolDO::getOwnerOrgId, ownerOrgId)
                .eq(ConfContactPoolDO::getEmail, email)
                .last("limit 1"));
        if (contact == null) {
            contact = ConfContactPoolDO.builder()
                    .ownerOrgId(ownerOrgId)
                    .name(author.getAuthorName())
                    .email(email)
                    .institution(author.getOrganization())
                    .build();
            contactPoolMapper.insert(contact);
        } else {
            contact.setName(StrUtil.blankToDefault(author.getAuthorName(), contact.getName()));
            contact.setInstitution(StrUtil.blankToDefault(author.getOrganization(), contact.getInstitution()));
            contactPoolMapper.updateById(contact);
        }

        Long existingMemberCount = memberMapper.selectCount(Wrappers.lambdaQuery(ConfMemberDO.class)
                .eq(ConfMemberDO::getConfId, author.getConferenceId())
                .eq(ConfMemberDO::getContactId, contact.getId())
                .eq(ConfMemberDO::getRole, MEMBER_ROLE_PROSPECT));
        if (existingMemberCount == null || existingMemberCount == 0) {
            memberMapper.insert(ConfMemberDO.builder()
                    .confId(author.getConferenceId())
                    .contactId(contact.getId())
                    .role(MEMBER_ROLE_PROSPECT)
                    .build());
        }
    }

    private Long resolveOwnerOrgId(ConferenceDO conference, Long confId) {
        String createUser = conference == null ? null : conference.getCreateUser();
        if (StrUtil.isNotBlank(createUser) && createUser.matches("\\d+")) {
            return Long.parseLong(createUser);
        }
        return confId == null ? 0L : confId;
    }

    private String normalizeEmail(String email) {
        return StrUtil.isBlank(email) ? null : email.trim().toLowerCase();
    }

    private List<String> resolveKeywords(Long conferenceId, List<String> requestKeywords) {
        if (requestKeywords != null && !requestKeywords.isEmpty()) {
            return cleanupKeywords(requestKeywords);
        }
        ConferenceDO conference = conferenceMapper.selectById(conferenceId);
        if (conference == null) {
            return List.of();
        }
        List<String> keywords = new ArrayList<>();
        addIfPresent(keywords, conference.getTitle());
        addIfPresent(keywords, conference.getShortName());
        addIfPresent(keywords, conference.getDescription());
        return cleanupKeywords(keywords);
    }

    private List<String> cleanupKeywords(List<String> values) {
        Set<String> cleaned = new LinkedHashSet<>();
        for (String value : values) {
            if (StrUtil.isBlank(value)) {
                continue;
            }
            String normalized = value.trim();
            if (normalized.length() > 120) {
                normalized = normalized.substring(0, 120);
            }
            cleaned.add(normalized);
        }
        return cleaned.stream().limit(10).toList();
    }

    private void addIfPresent(List<String> values, String value) {
        if (StrUtil.isNotBlank(value)) {
            values.add(value);
        }
    }

    private AuthorDiscoveryJobRespDTO toJobResp(AuthorDiscoveryJobDO job) {
        return AuthorDiscoveryJobRespDTO.builder()
                .jobId(job.getId())
                .status(job.getStatus())
                .totalPapers(job.getTotalPapers())
                .totalCandidates(job.getTotalCandidates())
                .highConfidenceEmails(job.getHighConfidenceEmails())
                .errorMessage(job.getErrorMessage())
                .build();
    }

    private PotentialAuthorRespDTO toPotentialAuthorResp(PotentialAuthorDO author) {
        return PotentialAuthorRespDTO.builder()
                .id(author.getId())
                .conferenceId(author.getConferenceId())
                .discoveryJobId(author.getDiscoveryJobId())
                .authorName(author.getAuthorName())
                .email(author.getEmail())
                .organization(author.getOrganization())
                .countryRegion(author.getCountryRegion())
                .researchKeywords(author.getResearchKeywords())
                .representativePapers(author.getRepresentativePapers())
                .sourcePlatform(author.getSourcePlatform())
                .sourceUrl(author.getSourceUrl())
                .topicSimilarity(author.getTopicSimilarity())
                .emailConfidence(author.getEmailConfidence())
                .overallScore(author.getOverallScore())
                .reviewStatus(author.getReviewStatus())
                .contactStatus(author.getContactStatus())
                .build();
    }
}
