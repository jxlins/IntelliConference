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
    private final SemanticScholarClient semanticScholarClient;
    private final CrossrefClient crossrefClient;
    private final AuthorCandidateBuilder candidateBuilder;
    private final PublicEmailExtractor emailExtractor;
    private final CandidateScoringService scoringService;
    private final AuthorIdentityMatcher identityMatcher;
    private final PotentialAuthorRepository potentialAuthorRepository;
    private final com.jxl.ai.intelliconf.author_discovery.config.AuthorDiscoveryProperties properties;
    private final DiscoveryJobControl jobControl;

    public AuthorDiscoveryJobDO createJob(AuthorDiscoveryCommand command) {
        List<String> keywords = resolveKeywords(command.getConferenceId(), command.getTopicKeywords());
        if (keywords.isEmpty()) {
            throw new IllegalArgumentException("topicKeywords is required when conference topic fields are empty");
        }
        int target = command.getMaxAuthors() == null ? 200 : command.getMaxAuthors();
        int from = command.getYearFrom() == null ? Year.now().getValue() - 5 : command.getYearFrom();
        int to = command.getYearTo() == null ? Year.now().getValue() : command.getYearTo();
        if (target < 1 || target > 500 || from < 1900 || from > to || to > Year.now().getValue())
            throw new ClientException("目标人数须为1至500，年份范围须有效");
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
        int claimed = jobMapper.update(null, Wrappers.lambdaUpdate(AuthorDiscoveryJobDO.class)
                .eq(AuthorDiscoveryJobDO::getId, jobId)
                .in(AuthorDiscoveryJobDO::getStatus, List.of("PENDING", "FAILED", "PARTIAL"))
                .set(AuthorDiscoveryJobDO::getStatus, "RUNNING").set(AuthorDiscoveryJobDO::getStartedAt, new Date()));
        if (claimed == 0) throw new ClientException("任务已在运行或已完成");

        try {
            List<String> keywords = JSON.parseArray(job.getTopicKeywords(), String.class);
            List<OpenAlexPaper> papers = searchAllSources(keywords, job);
            int candidatePoolSize = Math.min(job.getMaxAuthors() * 30, 15000);
            List<AuthorCandidate> candidates = candidateBuilder.buildCandidates(papers, candidatePoolSize);
            // Rank relevance before spending time on public full text.
            for (AuthorCandidate candidate : candidates) scoringService.score(candidate, keywords);
            candidates = candidates.stream()
                    .sorted(java.util.Comparator.comparingDouble(AuthorCandidate::getTopicSimilarity).reversed())
                    .toList();
            jobMapper.updateById(AuthorDiscoveryJobDO.builder().id(jobId).totalPapers(papers.size())
                    .errorMessage("论文检索完成（" + papers.size() + " 篇），开始检查 " + candidates.size() + " 位作者的公开邮箱").build());
            // 累加历史已入库邮箱：重复执行任务时在既有成果上继续补充，而不是从零开始
            Set<String> savedEmails = new LinkedHashSet<>(loadExistingEmails(job.getConferenceId()));
            Set<String> highEmails = new LinkedHashSet<>();
            java.util.Map<String, List<EmailEvidence>> evidenceByPaper = new java.util.concurrent.ConcurrentHashMap<>();
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MINUTES.toNanos(
                    Math.max(10, properties.getJobDeadlineMinutes()));
            int workerCount = Math.max(2, properties.getEmailExtractionWorkers());
            int processed = 0;
            java.util.concurrent.ExecutorService workers = java.util.concurrent.Executors.newFixedThreadPool(workerCount);
            // 按完成顺序收割结果，避免个别慢请求阻塞整批进度
            java.util.concurrent.ExecutorCompletionService<AuthorCandidate> completion =
                    new java.util.concurrent.ExecutorCompletionService<>(workers);
            int submitted = 0;
            int completedCount = 0;
            int consecutiveEmptyPolls = 0;
            try {
                while ((submitted < candidates.size() || completedCount < submitted)
                        && savedEmails.size() < job.getMaxAuthors()) {
                    if (Thread.currentThread().isInterrupted() || System.nanoTime() > deadline
                            || jobControl.isCancelRequested(jobId)) break;
                    while (submitted < candidates.size() && submitted - completedCount < workerCount * 2
                            && System.nanoTime() < deadline) {
                        AuthorCandidate candidate = candidates.get(submitted++);
                        completion.submit(() -> {
                            try {
                                if (enableEmailExtraction) attachEmailEvidence(candidate, enableCrossref, evidenceByPaper);
                                scoringService.score(candidate, keywords);
                            } catch (Exception ex) {
                                log.warn("Candidate email check failed, author={}, errorType={}",
                                        candidate.getAuthorName(), ex.getClass().getSimpleName());
                            }
                            return candidate;
                        });
                    }
                    long remaining = deadline - System.nanoTime();
                    if (remaining <= 0) break;
                    java.util.concurrent.Future<AuthorCandidate> future;
                    try {
                        future = completion.poll(
                                Math.min(remaining, java.util.concurrent.TimeUnit.SECONDS.toNanos(60)),
                                java.util.concurrent.TimeUnit.NANOSECONDS);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("发现任务已中断，可重新创建任务；已保存结果保留", ex);
                    }
                    if (future == null) {
                        if (submitted >= candidates.size() && completedCount >= submitted) break;
                        // 停滞看门狗：连续约10分钟没有任何候选完成（通常是抓取线程卡在异常PDF或慢主机上），
                        // 主动收尾并保留已入库结果，避免任务长时间假死
                        if (++consecutiveEmptyPolls >= 10) {
                            log.warn("No candidate completed for about ten minutes; finishing with saved results, jobId={}", jobId);
                            break;
                        }
                        continue;
                    }
                    consecutiveEmptyPolls = 0;
                    completedCount++;
                    processed++;
                    try {
                        AuthorCandidate candidate = future.get();
                        PotentialAuthorDO value = toPotentialAuthor(job, candidate);
                        if (savedEmails.size() < job.getMaxAuthors() && StrUtil.isNotBlank(value.getEmail())
                                && !savedEmails.contains(value.getEmail())) {
                        PotentialAuthorDO saved = potentialAuthorRepository.saveOrUpdateCandidate(value, candidate.getEmailEvidences());
                        if (StrUtil.isNotBlank(saved.getEmail()) && !ContactStatus.BLOCKED.name().equals(saved.getContactStatus())
                                && !ReviewStatus.REJECTED.name().equals(saved.getReviewStatus())) {
                            savedEmails.add(saved.getEmail());
                            if (candidate.getEmailConfidence() >= 0.8) highEmails.add(saved.getEmail());
                        }
                        }
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("发现任务已中断，可重新创建任务；已保存结果保留", ex);
                    } catch (Exception ex) {
                        log.warn("Candidate processing failed: {}", ex.getClass().getSimpleName());
                    }
                    if (processed % 5 == 0 || savedEmails.size() >= job.getMaxAuthors()) {
                        jobMapper.updateById(AuthorDiscoveryJobDO.builder().id(jobId)
                                .totalPapers(papers.size()).totalCandidates(savedEmails.size())
                                .highConfidenceEmails(highEmails.size())
                                .errorMessage("已检查 " + processed + "/" + candidates.size() + " 位作者，可信邮箱 "
                                        + savedEmails.size() + "/" + job.getMaxAuthors()).build());
                    }
                }
            } finally {
                workers.shutdownNow();
            }
            int savedCount = savedEmails.size();
            int highConfidenceEmails = highEmails.size();
            // 手动停止：保留已入库结果，标记为 CANCELLED（可再次发起任务继续补充）
            if (jobControl.consumeCancel(jobId)) {
                jobMapper.updateById(AuthorDiscoveryJobDO.builder().id(jobId)
                        .status(DiscoveryJobStatus.CANCELLED.name())
                        .totalPapers(papers.size()).totalCandidates(savedCount)
                        .highConfidenceEmails(highConfidenceEmails)
                        .finishedAt(new Date())
                        .errorMessage("任务已手动停止：本轮检查 " + processed + " 位作者，已保存可信邮箱 "
                                + savedCount + "/" + job.getMaxAuthors()
                                + "；可再次发起任务继续补充（系统会自动跳过已有结果）").build());
                return RunAuthorDiscoveryResultDTO.builder()
                        .jobId(jobId)
                        .status(DiscoveryJobStatus.CANCELLED.name())
                        .totalCandidates(savedCount)
                        .highConfidenceEmails(highConfidenceEmails)
                        .build();
            }
            markCompleted(jobId, papers.size(), savedCount, highConfidenceEmails);
            if (savedCount < job.getMaxAuthors()) {
                jobMapper.updateById(AuthorDiscoveryJobDO.builder().id(jobId)
                        .status("PARTIAL")
                        .errorMessage("本轮检查 " + processed + " 位作者，累计获得 " + savedCount + "/" + job.getMaxAuthors()
                                + " 个去重可信邮箱；其余未找到可公开访问且能匹配身份的邮箱。可再次发起任务继续补充（系统会自动跳过已有结果）。").build());
            }
            return RunAuthorDiscoveryResultDTO.builder()
                    .jobId(jobId)
                    .status(savedCount >= job.getMaxAuthors() ? "COMPLETED" : "PARTIAL")
                    .totalCandidates(savedCount)
                    .highConfidenceEmails(highConfidenceEmails)
                    .build();
        } catch (Exception ex) {
            // 手动取消引发的中断不算失败：落库为 CANCELLED 并保留结果
            if (jobControl.consumeCancel(jobId)) {
                jobMapper.updateById(AuthorDiscoveryJobDO.builder().id(jobId)
                        .status(DiscoveryJobStatus.CANCELLED.name())
                        .finishedAt(new Date())
                        .errorMessage("任务已手动停止，已保存结果保留；可再次发起任务继续补充").build());
                return RunAuthorDiscoveryResultDTO.builder()
                        .jobId(jobId)
                        .status(DiscoveryJobStatus.CANCELLED.name())
                        .build();
            }
            markFailed(jobId, StrUtil.maxLength(ex.getMessage(), 2000));
            throw ex;
        }
    }

    /**
     * 请求停止一个发现任务：
     * - 任务由当前进程执行：设置取消标记并中断执行线程，由 runJob 负责收尾落库；
     * - 任务不在当前进程（服务重启后的遗留僵尸任务）：直接落库置为 CANCELLED。
     */
    public void cancelJob(Long jobId) {
        AuthorDiscoveryJobDO job = jobMapper.selectById(jobId);
        if (job == null) {
            throw new ClientException("发现任务不存在");
        }
        if (!"RUNNING".equals(job.getStatus()) && !"PENDING".equals(job.getStatus())) {
            throw new ClientException("任务当前不在运行中，无需停止");
        }
        if (jobControl.isRegistered(jobId)) {
            jobControl.requestCancel(jobId);
            return;
        }
        jobMapper.update(null, Wrappers.lambdaUpdate(AuthorDiscoveryJobDO.class)
                .eq(AuthorDiscoveryJobDO::getId, jobId)
                .in(AuthorDiscoveryJobDO::getStatus, List.of("RUNNING", "PENDING"))
                .set(AuthorDiscoveryJobDO::getStatus, DiscoveryJobStatus.CANCELLED.name())
                .set(AuthorDiscoveryJobDO::getFinishedAt, new Date())
                .set(AuthorDiscoveryJobDO::getErrorMessage,
                        "任务已手动停止，已入库的结果保留；可再次发起任务继续补充"));
    }

    /**
     * OpenAlex + Semantic Scholar 双源检索，按 DOI/标题去重合并。
     */
    private List<OpenAlexPaper> searchAllSources(List<String> keywords, AuthorDiscoveryJobDO job) {
        int perSource = properties.getMaxTotalPapers();
        List<OpenAlexPaper> openAlexPapers = List.of();
        try {
            openAlexPapers = openAlexClient.searchWorks(keywords, job.getYearFrom(), job.getYearTo(), job.getMaxAuthors());
        } catch (RuntimeException ex) {
            log.warn("OpenAlex unavailable, trying the second source: {}", ex.getClass().getSimpleName());
        }
        java.util.Map<String, OpenAlexPaper> paperMap = new java.util.LinkedHashMap<>();
        for (OpenAlexPaper paper : openAlexPapers) {
            paperMap.putIfAbsent(dedupeKey(paper), paper);
        }
        jobMapper.updateById(AuthorDiscoveryJobDO.builder().id(job.getId()).totalPapers(paperMap.size())
                .errorMessage("OpenAlex已获取 " + paperMap.size() + " 篇论文，正在补充第二数据源").build());
        if (properties.isSemanticScholarEnabled()) {
            try {
                List<OpenAlexPaper> s2Papers = semanticScholarClient.searchWorks(keywords, job.getYearFrom(), job.getYearTo(), perSource);
                for (OpenAlexPaper paper : s2Papers) {
                    OpenAlexPaper existing = paperMap.putIfAbsent(dedupeKey(paper), paper);
                    if (existing != null && StrUtil.isNotBlank(paper.getPdfUrl())
                            && !existing.getAdditionalUrls().contains(paper.getPdfUrl())) {
                        existing.getAdditionalUrls().add(paper.getPdfUrl());
                    }
                }
                log.info("[AuthorDiscovery] merged papers: openalex={}, s2={}, total={}",
                        openAlexPapers.size(), s2Papers.size(), paperMap.size());
            } catch (Exception ex) {
                log.warn("Semantic Scholar search skipped, errorType={}", ex.getClass().getSimpleName());
            }
        }
        return paperMap.values().stream().limit(perSource).toList();
    }

    private String dedupeKey(OpenAlexPaper paper) {
        if (StrUtil.isNotBlank(paper.getDoi())) {
            return "doi:" + paper.getDoi().trim().toLowerCase();
        }
        if (StrUtil.isNotBlank(paper.getId())) {
            return "id:" + paper.getId();
        }
        return "title:" + StrUtil.blankToDefault(paper.getTitle(), "").toLowerCase().replaceAll("\\s+", " ").trim();
    }

    private List<String> loadExistingEmails(Long conferenceId) {
        if (conferenceId == null) {
            return List.of();
        }
        return potentialAuthorMapper.selectList(Wrappers.lambdaQuery(PotentialAuthorDO.class)
                        .eq(PotentialAuthorDO::getConferenceId, conferenceId)
                        .isNotNull(PotentialAuthorDO::getEmail)
                        .ne(PotentialAuthorDO::getEmail, "")
                        .ne(PotentialAuthorDO::getReviewStatus, ReviewStatus.REJECTED.name())
                        .eq(PotentialAuthorDO::getContactStatus, ContactStatus.NOT_CONTACTED.name())
                        .select(PotentialAuthorDO::getEmail))
                .stream()
                .map(PotentialAuthorDO::getEmail)
                .filter(StrUtil::isNotBlank)
                .map(email -> email.trim().toLowerCase())
                .toList();
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

    private void attachEmailEvidence(AuthorCandidate candidate, boolean enableCrossref,
                                     java.util.Map<String, List<EmailEvidence>> evidenceByPaper) {
        // 直链 PDF 最容易解析出作者邮箱，优先抓取；其次开放获取页、其他落地页
        for (OpenAlexPaper paper : candidate.getPapers().stream()
                .sorted((left, right) -> {
                    int pdf = Boolean.compare(StrUtil.isNotBlank(right.getPdfUrl()), StrUtil.isNotBlank(left.getPdfUrl()));
                    if (pdf != 0) return pdf;
                    return Boolean.compare(right.isOpenAccess(), left.isOpenAccess());
                }).limit(8).toList()) {
            if (Thread.currentThread().isInterrupted()) return;
            String key = StrUtil.blankToDefault(paper.getId(), StrUtil.blankToDefault(paper.getDoi(), paper.getTitle()));
            List<EmailEvidence> raw = evidenceByPaper.computeIfAbsent(key, ignored -> {
                List<String> urls = new ArrayList<>();
                addIfPresent(urls, paper.getPdfUrl());
                addIfPresent(urls, paper.getOpenAccessLandingPageUrl());
                urls.addAll(paper.getAdditionalUrls());
                addIfPresent(urls, paper.getLandingPageUrl());
                List<EmailEvidence> found = emailExtractor.extractFromUrls(urls.stream().distinct().limit(8).toList());
                if (found.isEmpty() && enableCrossref) {
                    found = emailExtractor.extractFromUrls(crossrefClient.findMetadataUrls(paper.getDoi()));
                }
                return found;
            });
            for (EmailEvidence source : raw) {
                // Evidence is shared across coauthors; identity scores are candidate-specific.
                EmailEvidence evidence = EmailEvidence.builder().email(source.getEmail())
                        .sourceType(source.getSourceType()).sourceUrl(source.getSourceUrl())
                        .evidenceText(source.getEvidenceText()).pageText(source.getPageText())
                        .generic(source.isGeneric()).build();
                evidence.setIdentityScore(identityMatcher.score(candidate, evidence));
                evidence.setConfidence(scoringService.evidenceConfidence(evidence, candidate.isCorresponding()));
                candidate.getEmailEvidences().add(evidence);
            }
            if (candidate.getEmailEvidences().stream().anyMatch(e -> e.getConfidence() >= 0.8)) break;
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
        return DiscoveryKeywords.normalize(values);
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
