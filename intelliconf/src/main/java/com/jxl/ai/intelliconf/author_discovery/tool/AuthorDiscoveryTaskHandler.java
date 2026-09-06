package com.jxl.ai.intelliconf.author_discovery.tool;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.jxl.ai.intelliconf.author_discovery.dto.AuthorDiscoveryCommand;
import com.jxl.ai.intelliconf.common.constant.TaskConstants;
import com.jxl.ai.intelliconf.handler.TaskHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component(TaskConstants.AUTHOR_DISCOVERY_TASK_HANDLER)
@RequiredArgsConstructor
public class AuthorDiscoveryTaskHandler implements TaskHandler {

    private final PotentialAuthorDiscoveryTool discoveryTool;

    @Override
    public void execute(Long confId, Long taskLogId, String params) {
        JSONObject json = JSON.parseObject(params == null ? "{}" : params);
        AuthorDiscoveryCommand command = AuthorDiscoveryCommand.builder()
                .conferenceId(confId)
                .topicKeywords(json.getJSONArray("topicKeywords") == null
                        ? null
                        : json.getJSONArray("topicKeywords").toJavaList(String.class))
                .yearFrom(json.getInteger("yearFrom"))
                .yearTo(json.getInteger("yearTo"))
                .maxAuthors(json.getInteger("maxAuthors"))
                .enableCrossref(json.getBoolean("enableCrossref"))
                .enableEmailExtraction(json.getBoolean("enableEmailExtraction"))
                .build();
        Long jobId = discoveryTool.createJob(command).getJobId();
        boolean enableCrossref = !Boolean.FALSE.equals(command.getEnableCrossref());
        boolean enableEmailExtraction = !Boolean.FALSE.equals(command.getEnableEmailExtraction());
        discoveryTool.runJob(jobId, enableCrossref, enableEmailExtraction);
        log.info("[AuthorDiscoveryTaskHandler] Potential author discovery completed, confId={}, jobId={}, taskLogId={}",
                confId, jobId, taskLogId);
    }
}
