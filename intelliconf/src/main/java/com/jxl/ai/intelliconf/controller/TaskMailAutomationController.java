package com.jxl.ai.intelliconf.controller;

import com.jxl.ai.intelliconf.common.convention.result.Result;
import com.jxl.ai.intelliconf.common.convention.result.Results;
import com.jxl.ai.intelliconf.dto.req.TaskMailPlanApproveReqDTO;
import com.jxl.ai.intelliconf.dto.resp.TaskMailPlanRespDTO;
import com.jxl.ai.intelliconf.service.TaskMailAutomationService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class TaskMailAutomationController {
    private final TaskMailAutomationService service;

    @GetMapping("/api/conferences/{conferenceId}/task-mail-plans")
    public Result<List<TaskMailPlanRespDTO>> list(@PathVariable Long conferenceId) {
        return Results.success(service.listPlans(conferenceId));
    }

    /** 首页审核中心徽标：返回各类型待审核数量（目前仅邮件；潜在投稿者审核不计入） */
    @GetMapping("/api/conferences/{conferenceId}/review/pending-count")
    public Result<Map<String, Integer>> pendingReviewCount(@PathVariable Long conferenceId) {
        return Results.success(Map.of("mailReviewCount", service.countPendingReview(conferenceId)));
    }

    @PutMapping("/api/conferences/{conferenceId}/task-mail-plans/{planId}/approve")
    public Result<TaskMailPlanRespDTO> approve(@PathVariable Long conferenceId, @PathVariable Long planId,
                                               @RequestBody TaskMailPlanApproveReqDTO request) {
        return Results.success(service.approve(conferenceId, planId, request));
    }

    @PostMapping("/api/conferences/{conferenceId}/task-mail-plans/{planId}/reject")
    public Result<TaskMailPlanRespDTO> reject(@PathVariable Long conferenceId, @PathVariable Long planId,
                                               @RequestBody(required = false) Map<String, String> body) {
        String reason = body == null ? null : body.get("reason");
        return Results.success(service.reject(conferenceId, planId, StringUtils.hasText(reason) ? reason : null));
    }

    @PostMapping("/api/conferences/{conferenceId}/task-mail-plans/{planId}/retry")
    public Result<TaskMailPlanRespDTO> retry(@PathVariable Long conferenceId, @PathVariable Long planId) {
        return Results.success(service.retry(conferenceId, planId));
    }

    /** 重新生成邮件主题与正文（走 DeepSeek/模板/兜底，需组织者再次审核通过才会发送） */
    @PostMapping("/api/conferences/{conferenceId}/task-mail-plans/{planId}/regenerate")
    public Result<TaskMailPlanRespDTO> regenerate(@PathVariable Long conferenceId, @PathVariable Long planId) {
        return Results.success(service.regenerate(conferenceId, planId));
    }
}
