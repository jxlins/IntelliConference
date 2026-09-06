package com.jxl.ai.intelliconf.author_discovery.tool;

import com.jxl.ai.intelliconf.author_discovery.dto.AuthorDiscoveryCommand;
import com.jxl.ai.intelliconf.author_discovery.dto.AuthorDiscoveryJobRespDTO;
import com.jxl.ai.intelliconf.author_discovery.dto.RunAuthorDiscoveryResultDTO;
import com.jxl.ai.intelliconf.author_discovery.entity.AuthorDiscoveryJobDO;
import com.jxl.ai.intelliconf.author_discovery.service.AuthorDiscoveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PotentialAuthorDiscoveryTool {

    public static final String TOOL_CODE = "POTENTIAL_AUTHOR_DISCOVERY";

    private final AuthorDiscoveryService authorDiscoveryService;

    public AuthorDiscoveryJobRespDTO createJob(AuthorDiscoveryCommand command) {
        AuthorDiscoveryJobDO job = authorDiscoveryService.createJob(command);
        return AuthorDiscoveryJobRespDTO.builder()
                .jobId(job.getId())
                .status(job.getStatus())
                .totalPapers(job.getTotalPapers())
                .totalCandidates(job.getTotalCandidates())
                .highConfidenceEmails(job.getHighConfidenceEmails())
                .build();
    }

    public RunAuthorDiscoveryResultDTO runJob(Long jobId, boolean enableCrossref, boolean enableEmailExtraction) {
        return authorDiscoveryService.runJob(jobId, enableCrossref, enableEmailExtraction);
    }
}
