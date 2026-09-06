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

    @PostMapping("/api/conferences/{conferenceId}/author-discovery/jobs")
    public Result<AuthorDiscoveryJobRespDTO> createJob(@PathVariable Long conferenceId,
                                                       @RequestBody CreateAuthorDiscoveryJobReqDTO requestParam) {
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
        // TODO: Switch author discovery execution to an async job runner after the task framework is ready.
        boolean enableCrossref = requestParam == null || !Boolean.FALSE.equals(requestParam.getEnableCrossref());
        boolean enableEmailExtraction = requestParam == null || !Boolean.FALSE.equals(requestParam.getEnableEmailExtraction());
        return Results.success(discoveryTool.runJob(jobId, enableCrossref, enableEmailExtraction));
    }

    @GetMapping("/api/author-discovery/jobs/{jobId}")
    public Result<AuthorDiscoveryJobRespDTO> getJob(@PathVariable Long jobId) {
        return Results.success(authorDiscoveryService.getJob(jobId));
    }

    @GetMapping("/api/conferences/{conferenceId}/potential-authors")
    public Result<List<PotentialAuthorRespDTO>> listCandidates(@PathVariable Long conferenceId,
                                                               PotentialAuthorQueryDTO query) {
        return Results.success(authorDiscoveryService.listCandidates(conferenceId, query));
    }

    @PostMapping("/api/potential-authors/{authorId}/approve")
    public Result<Void> approve(@PathVariable Long authorId) {
        authorDiscoveryService.approveCandidate(authorId);
        return Results.success();
    }

    @PostMapping("/api/potential-authors/{authorId}/reject")
    public Result<Void> reject(@PathVariable Long authorId) {
        authorDiscoveryService.rejectCandidate(authorId);
        return Results.success();
    }
}
