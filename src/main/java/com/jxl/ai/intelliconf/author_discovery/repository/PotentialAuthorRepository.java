package com.jxl.ai.intelliconf.author_discovery.repository;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jxl.ai.intelliconf.author_discovery.dto.EmailEvidence;
import com.jxl.ai.intelliconf.author_discovery.dto.PotentialAuthorQueryDTO;
import com.jxl.ai.intelliconf.author_discovery.entity.AuthorEmailSourceDO;
import com.jxl.ai.intelliconf.author_discovery.entity.EmailSuppressionDO;
import com.jxl.ai.intelliconf.author_discovery.entity.PotentialAuthorDO;
import com.jxl.ai.intelliconf.author_discovery.enums.ContactStatus;
import com.jxl.ai.intelliconf.author_discovery.enums.ReviewStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Locale;

@Repository
@RequiredArgsConstructor
public class PotentialAuthorRepository {

    private final PotentialAuthorMapper potentialAuthorMapper;
    private final AuthorEmailSourceMapper emailSourceMapper;
    private final EmailSuppressionMapper suppressionMapper;

    public PotentialAuthorDO saveOrUpdateCandidate(PotentialAuthorDO candidate, List<EmailEvidence> evidences) {
        Date now = new Date();
        candidate.setUpdatedAt(now);
        if (candidate.getCreatedAt() == null) {
            candidate.setCreatedAt(now);
        }
        if (StrUtil.isNotBlank(candidate.getEmail()) && existsSuppressedEmail(candidate.getConferenceId(), candidate.getEmail())) {
            candidate.setEmail(null);
            candidate.setContactStatus(ContactStatus.BLOCKED.name());
        }

        PotentialAuthorDO existing = findExisting(candidate);
        if (existing == null) {
            potentialAuthorMapper.insert(candidate);
            existing = candidate;
        } else {
            candidate.setId(existing.getId());
            candidate.setCreatedAt(existing.getCreatedAt());
            if (ReviewStatus.APPROVED.name().equals(existing.getReviewStatus())
                    || ReviewStatus.REJECTED.name().equals(existing.getReviewStatus())) {
                candidate.setReviewStatus(existing.getReviewStatus());
            }
            if (existing.getEmailConfidence() != null && candidate.getEmailConfidence() != null
                    && existing.getEmailConfidence().compareTo(candidate.getEmailConfidence()) > 0) {
                candidate.setEmail(existing.getEmail());
                candidate.setEmailConfidence(existing.getEmailConfidence());
            }
            potentialAuthorMapper.updateById(candidate);
            existing = candidate;
        }
        saveEmailSources(existing.getId(), evidences);
        return existing;
    }

    public List<PotentialAuthorDO> listCandidates(Long conferenceId, PotentialAuthorQueryDTO query) {
        LambdaQueryWrapper<PotentialAuthorDO> wrapper = Wrappers.lambdaQuery(PotentialAuthorDO.class)
                .eq(PotentialAuthorDO::getConferenceId, conferenceId)
                .orderByDesc(PotentialAuthorDO::getOverallScore)
                .orderByDesc(PotentialAuthorDO::getId);
        if (query != null) {
            wrapper.eq(StrUtil.isNotBlank(query.getReviewStatus()), PotentialAuthorDO::getReviewStatus, query.getReviewStatus());
            wrapper.eq(StrUtil.isNotBlank(query.getContactStatus()), PotentialAuthorDO::getContactStatus, query.getContactStatus());
            wrapper.eq(StrUtil.isNotBlank(query.getSourcePlatform()), PotentialAuthorDO::getSourcePlatform, query.getSourcePlatform());
            wrapper.ge(query.getMinScore() != null, PotentialAuthorDO::getOverallScore, query.getMinScore());
            if (Boolean.TRUE.equals(query.getHasEmail())) {
                wrapper.isNotNull(PotentialAuthorDO::getEmail).ne(PotentialAuthorDO::getEmail, "");
            } else if (Boolean.FALSE.equals(query.getHasEmail())) {
                wrapper.and(item -> item.isNull(PotentialAuthorDO::getEmail).or().eq(PotentialAuthorDO::getEmail, ""));
            }
        }
        return potentialAuthorMapper.selectList(wrapper);
    }

    public boolean approveCandidate(Long authorId) {
        PotentialAuthorDO update = PotentialAuthorDO.builder()
                .id(authorId)
                .reviewStatus(ReviewStatus.APPROVED.name())
                .updatedAt(new Date())
                .build();
        return potentialAuthorMapper.updateById(update) > 0;
    }

    public boolean rejectCandidate(Long authorId) {
        PotentialAuthorDO update = PotentialAuthorDO.builder()
                .id(authorId)
                .reviewStatus(ReviewStatus.REJECTED.name())
                .updatedAt(new Date())
                .build();
        return potentialAuthorMapper.updateById(update) > 0;
    }

    public boolean existsSuppressedEmail(Long conferenceId, String email) {
        if (StrUtil.isBlank(email)) {
            return false;
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        Long count = suppressionMapper.selectCount(Wrappers.lambdaQuery(EmailSuppressionDO.class)
                .eq(EmailSuppressionDO::getEmail, normalized)
                .and(item -> item.isNull(EmailSuppressionDO::getConferenceId)
                        .or()
                        .eq(conferenceId != null, EmailSuppressionDO::getConferenceId, conferenceId)));
        return count != null && count > 0;
    }

    private PotentialAuthorDO findExisting(PotentialAuthorDO candidate) {
        if (StrUtil.isNotBlank(candidate.getEmail())) {
            PotentialAuthorDO byEmail = potentialAuthorMapper.selectOne(Wrappers.lambdaQuery(PotentialAuthorDO.class)
                    .eq(PotentialAuthorDO::getConferenceId, candidate.getConferenceId())
                    .eq(PotentialAuthorDO::getEmail, candidate.getEmail())
                    .last("limit 1"));
            if (byEmail != null) {
                return byEmail;
            }
        }
        return potentialAuthorMapper.selectOne(Wrappers.lambdaQuery(PotentialAuthorDO.class)
                .eq(PotentialAuthorDO::getConferenceId, candidate.getConferenceId())
                .eq(PotentialAuthorDO::getNormalizedName, candidate.getNormalizedName())
                .eq(StrUtil.isNotBlank(candidate.getOrganization()), PotentialAuthorDO::getOrganization, candidate.getOrganization())
                .last("limit 1"));
    }

    private void saveEmailSources(Long potentialAuthorId, List<EmailEvidence> evidences) {
        if (potentialAuthorId == null || evidences == null || evidences.isEmpty()) {
            return;
        }
        for (EmailEvidence evidence : evidences) {
            String email = normalizeEmail(evidence.getEmail());
            if (existsEmailSource(potentialAuthorId, email, evidence.getSourceUrl())) {
                continue;
            }
            emailSourceMapper.insert(AuthorEmailSourceDO.builder()
                    .potentialAuthorId(potentialAuthorId)
                    .email(email)
                    .sourceType(evidence.getSourceType())
                    .sourceUrl(evidence.getSourceUrl())
                    .evidenceText(StrUtil.maxLength(evidence.getEvidenceText(), 500))
                    .confidence(BigDecimal.valueOf(evidence.getConfidence()))
                    .collectedAt(new Date())
                    .build());
        }
    }

    private boolean existsEmailSource(Long potentialAuthorId, String email, String sourceUrl) {
        if (potentialAuthorId == null || StrUtil.isBlank(email) || StrUtil.isBlank(sourceUrl)) {
            return false;
        }
        Long count = emailSourceMapper.selectCount(Wrappers.lambdaQuery(AuthorEmailSourceDO.class)
                .eq(AuthorEmailSourceDO::getPotentialAuthorId, potentialAuthorId)
                .eq(AuthorEmailSourceDO::getEmail, email)
                .eq(AuthorEmailSourceDO::getSourceUrl, sourceUrl));
        return count != null && count > 0;
    }

    private String normalizeEmail(String email) {
        return StrUtil.isBlank(email) ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
