package com.jxl.ai.intelliconf.author_discovery.controller;

import com.jxl.ai.intelliconf.author_discovery.dto.AuthorDiscoveryCommand;
import com.jxl.ai.intelliconf.author_discovery.dto.AuthorDiscoveryJobRespDTO;
import com.jxl.ai.intelliconf.author_discovery.dto.CreateAuthorDiscoveryJobReqDTO;
import com.jxl.ai.intelliconf.author_discovery.dto.PotentialAuthorQueryDTO;
import com.jxl.ai.intelliconf.author_discovery.dto.PotentialAuthorRespDTO;
import com.jxl.ai.intelliconf.author_discovery.dto.RunAuthorDiscoveryResultDTO;
import com.jxl.ai.intelliconf.author_discovery.service.AuthorDiscoveryService;
import com.jxl.ai.intelliconf.author_discovery.tool.PotentialAuthorDiscoveryTool;
import com.jxl.ai.intelliconf.common.convention.result.Result;
import com.jxl.ai.intelliconf.common.convention.result.Results;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class AuthorDiscoveryController {

    private final PotentialAuthorDiscoveryTool discoveryTool;
    private final AuthorDiscoveryService authorDiscoveryService;
    private final com.jxl.ai.intelliconf.author_discovery.service.DiscoveryJobRunner runner;
    private final com.jxl.ai.intelliconf.service.ConferenceMemberRoleService permissions;
    private final com.jxl.ai.intelliconf.author_discovery.repository.AuthorDiscoveryJobMapper jobs;
    private final com.jxl.ai.intelliconf.author_discovery.repository.PotentialAuthorMapper candidates;

    private void authorize(Long conferenceId) {
        String id = com.jxl.ai.intelliconf.common.biz.user.UserContext.getUserId();
        if (id == null) id = com.jxl.ai.intelliconf.common.biz.user.UserContext.getUsername();
        permissions.requireOrganizer(conferenceId, id);
    }

    private void authorizeJob(Long id) {
        var job = jobs.selectById(id);
        if (job == null) throw new com.jxl.ai.intelliconf.common.convention.exception.ClientException("发现任务不存在");
        authorize(job.getConferenceId());
    }

    private void authorizeCandidate(Long id) {
        var author = candidates.selectById(id);
        if (author == null) throw new com.jxl.ai.intelliconf.common.convention.exception.ClientException("候选作者不存在");
        authorize(author.getConferenceId());
    }

    @PostMapping("/api/author-discovery/jobs/{jobId}/start")
    public Result<AuthorDiscoveryJobRespDTO> start(@PathVariable Long jobId,
            @RequestBody(required = false) CreateAuthorDiscoveryJobReqDTO request) {
        authorizeJob(jobId);
        runner.start(jobId, request == null || !Boolean.FALSE.equals(request.getEnableCrossref()),
                request == null || !Boolean.FALSE.equals(request.getEnableEmailExtraction()));
        return Results.success(authorDiscoveryService.getJob(jobId));
    }

    @PostMapping("/api/conferences/{conferenceId}/author-discovery/jobs")
    public Result<AuthorDiscoveryJobRespDTO> createJob(@PathVariable Long conferenceId,
                                                       @RequestBody CreateAuthorDiscoveryJobReqDTO requestParam) {
        authorize(conferenceId);
        AuthorDiscoveryCommand command = AuthorDiscoveryCommand.builder()
                .conferenceId(conferenceId)
                .topicKeywords(requestParam.getTopicKeywords())
                .yearFrom(requestParam.getYearFrom())
                .yearTo(requestParam.getYearTo())
                .maxAuthors(requestParam.getMaxAuthors())
                .enableCrossref(requestParam.getEnableCrossref())
                .enableEmailExtraction(requestParam.getEnableEmailExtraction())
                .build();
        return Results.success(discoveryTool.createJob(command));
    }

    @PostMapping("/api/author-discovery/jobs/{jobId}/run")
    public Result<RunAuthorDiscoveryResultDTO> runJob(@PathVariable Long jobId,
                                                      @RequestBody(required = false) CreateAuthorDiscoveryJobReqDTO requestParam) {
        authorizeJob(jobId);
        // TODO: Switch author discovery execution to an async job runner after the task framework is ready.
        boolean enableCrossref = requestParam == null || !Boolean.FALSE.equals(requestParam.getEnableCrossref());
        boolean enableEmailExtraction = requestParam == null || !Boolean.FALSE.equals(requestParam.getEnableEmailExtraction());
        return Results.success(discoveryTool.runJob(jobId, enableCrossref, enableEmailExtraction));
    }

    @PostMapping("/api/author-discovery/jobs/{jobId}/cancel")
    public Result<AuthorDiscoveryJobRespDTO> cancel(@PathVariable Long jobId) {
        authorizeJob(jobId);
        authorDiscoveryService.cancelJob(jobId);
        return Results.success(authorDiscoveryService.getJob(jobId));
    }

    @GetMapping("/api/author-discovery/jobs/{jobId}")
    public Result<AuthorDiscoveryJobRespDTO> getJob(@PathVariable Long jobId) {
        authorizeJob(jobId);
        return Results.success(authorDiscoveryService.getJob(jobId));
    }

    @GetMapping("/api/conferences/{conferenceId}/author-discovery/latest")
    public Result<AuthorDiscoveryJobRespDTO> latest(@PathVariable Long conferenceId) {
        authorize(conferenceId);
        var job = jobs.selectOne(com.baomidou.mybatisplus.core.toolkit.Wrappers.lambdaQuery(
                com.jxl.ai.intelliconf.author_discovery.entity.AuthorDiscoveryJobDO.class)
                .eq(com.jxl.ai.intelliconf.author_discovery.entity.AuthorDiscoveryJobDO::getConferenceId, conferenceId)
                .orderByDesc(com.jxl.ai.intelliconf.author_discovery.entity.AuthorDiscoveryJobDO::getCreatedAt).last("limit 1"));
        return Results.success(job == null ? null : authorDiscoveryService.getJob(job.getId()));
    }

    @GetMapping("/api/conferences/{conferenceId}/potential-authors")
    public Result<List<PotentialAuthorRespDTO>> listCandidates(@PathVariable Long conferenceId,
                                                               PotentialAuthorQueryDTO query) {
        authorize(conferenceId);
        return Results.success(authorDiscoveryService.listCandidates(conferenceId, query));
    }

    @PostMapping("/api/potential-authors/{authorId}/approve")
    public Result<Void> approve(@PathVariable Long authorId) {
        authorizeCandidate(authorId);
        authorDiscoveryService.approveCandidate(authorId);
        return Results.success();
    }

    @PostMapping("/api/potential-authors/{authorId}/reject")
    public Result<Void> reject(@PathVariable Long authorId) {
        authorizeCandidate(authorId);
        authorDiscoveryService.rejectCandidate(authorId);
        return Results.success();
    }
}
