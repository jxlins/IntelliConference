package com.jxl.ai.intelliconf.controller;

import com.jxl.ai.intelliconf.common.biz.user.UserContext;
import com.jxl.ai.intelliconf.common.convention.result.Result;
import com.jxl.ai.intelliconf.common.convention.result.Results;
import com.jxl.ai.intelliconf.dao.entity.ConfMilestoneDO;
import com.jxl.ai.intelliconf.dto.req.ConferenceTaskQueryReqDTO;
import com.jxl.ai.intelliconf.dto.req.TaskAssignReqDTO;
import com.jxl.ai.intelliconf.dto.req.TaskCompleteReqDTO;
import com.jxl.ai.intelliconf.dto.req.TaskActionReqDTO;
import com.jxl.ai.intelliconf.dto.resp.ConferenceTaskRespDTO;
import com.jxl.ai.intelliconf.dto.resp.TaskAttachmentRespDTO;
import com.jxl.ai.intelliconf.dto.resp.TaskAssigneeCandidateRespDTO;
import com.jxl.ai.intelliconf.dto.resp.TaskCompleteRespDTO;
import com.jxl.ai.intelliconf.dto.resp.TaskActionLogRespDTO;
import com.jxl.ai.intelliconf.dto.resp.TaskSystemCheckRespDTO;
import com.jxl.ai.intelliconf.dto.resp.TimelineOverviewRespDTO;
import com.jxl.ai.intelliconf.service.ConferenceTaskAttachmentService;
import com.jxl.ai.intelliconf.service.ConferenceTaskService;
import com.jxl.ai.intelliconf.service.ConferenceTimelineService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ConferenceProcessController {

    private final ConferenceTimelineService conferenceTimelineService;
    private final ConferenceTaskService conferenceTaskService;
    private final ConferenceTaskAttachmentService conferenceTaskAttachmentService;

    @GetMapping("/api/conferences/{conferenceId}/timeline")
    public Result<TimelineOverviewRespDTO> getTimeline(@PathVariable Long conferenceId) {
        return Results.success(conferenceTimelineService.getTimeline(conferenceId));
    }

    @GetMapping("/api/conferences/{conferenceId}/timeline/current")
    public Result<ConfMilestoneDO> getCurrentMilestone(@PathVariable Long conferenceId) {
        return Results.success(conferenceTimelineService.getCurrentMilestone(conferenceId));
    }

    @PostMapping("/api/conferences/{conferenceId}/timeline/{nodeCode}/start")
    public Result<ConfMilestoneDO> startMilestone(@PathVariable Long conferenceId, @PathVariable String nodeCode) {
        return Results.success(conferenceTimelineService.startMilestone(conferenceId, nodeCode, currentUserId()));
    }

    @PostMapping("/api/conferences/{conferenceId}/timeline/{nodeCode}/complete")
    public Result<Void> completeMilestone(@PathVariable Long conferenceId, @PathVariable String nodeCode) {
        conferenceTimelineService.completeMilestone(conferenceId, nodeCode, currentUserId());
        return Results.success();
    }

    @PostMapping("/api/conferences/{conferenceId}/timeline/{nodeCode}/skip")
    public Result<Void> skipMilestone(@PathVariable Long conferenceId,
                                      @PathVariable String nodeCode,
                                      @RequestBody(required = false) TaskActionReqDTO reqDTO) {
        conferenceTimelineService.skipMilestone(conferenceId, nodeCode, currentUserId(), reqDTO == null ? null : reqDTO.getComment());
        return Results.success();
    }

    @GetMapping("/api/conferences/{conferenceId}/tasks/my")
    public Result<List<ConferenceTaskRespDTO>> listMyTasks(@PathVariable Long conferenceId,
                                                           @ModelAttribute ConferenceTaskQueryReqDTO query) {
        return Results.success(conferenceTaskService.listMyTasks(conferenceId, currentUserId(), query));
    }

    @GetMapping("/api/conferences/{conferenceId}/tasks/generated")
    public Result<List<ConferenceTaskRespDTO>> listGeneratedTasks(@PathVariable Long conferenceId) {
        return Results.success(conferenceTaskService.listGeneratedTasksForConference(conferenceId, currentUserId()));
    }

    @GetMapping("/api/conferences/{conferenceId}/tasks/preview")
    public Result<List<ConferenceTaskRespDTO>> previewTasks(@PathVariable Long conferenceId) {
        return Results.success(conferenceTaskService.previewTasksForConference(conferenceId, currentUserId()));
    }

    @PostMapping("/api/conferences/{conferenceId}/tasks/generate")
    public Result<List<ConferenceTaskRespDTO>> generateTasks(@PathVariable Long conferenceId) {
        return Results.success(conferenceTaskService.generateTasksForConference(conferenceId, currentUserId()));
    }

    @GetMapping("/api/conferences/{conferenceId}/tasks/all")
    public Result<List<ConferenceTaskRespDTO>> listAllTasks(@PathVariable Long conferenceId,
                                                            @ModelAttribute ConferenceTaskQueryReqDTO query) {
        return Results.success(conferenceTaskService.listAllTasksForOrganizer(conferenceId, currentUserId(), query));
    }

    @GetMapping("/api/conferences/{conferenceId}/tasks/{taskInstanceId}")
    public Result<ConferenceTaskRespDTO> getTaskDetail(@PathVariable Long conferenceId,
                                                       @PathVariable Long taskInstanceId) {
        return Results.success(conferenceTaskService.getTaskDetail(conferenceId, taskInstanceId, currentUserId()));
    }

    @GetMapping("/api/conferences/{conferenceId}/tasks/{taskInstanceId}/system-check")
    public Result<TaskSystemCheckRespDTO> getTaskSystemCheck(@PathVariable Long conferenceId,
                                                             @PathVariable Long taskInstanceId) {
        return Results.success(conferenceTaskService.getTaskSystemCheck(conferenceId, taskInstanceId, currentUserId()));
    }

    @GetMapping("/api/conferences/{conferenceId}/tasks/{taskInstanceId}/assignee-candidates")
    public Result<List<TaskAssigneeCandidateRespDTO>> listTaskAssigneeCandidates(@PathVariable Long conferenceId,
                                                                                 @PathVariable Long taskInstanceId) {
        return Results.success(conferenceTaskService.listTaskAssigneeCandidates(conferenceId, taskInstanceId, currentUserId()));
    }

    @PostMapping("/api/conferences/{conferenceId}/tasks/{taskInstanceId}/assign")
    public Result<Void> assignTask(@PathVariable Long conferenceId,
                                   @PathVariable Long taskInstanceId,
                                   @RequestBody TaskAssignReqDTO request) {
        conferenceTaskService.assignTask(conferenceId, taskInstanceId, currentUserId(), request);
        return Results.success();
    }

    @PostMapping("/api/conferences/{conferenceId}/tasks/{taskInstanceId}/attachments")
    public Result<TaskAttachmentRespDTO> uploadAttachment(@PathVariable Long conferenceId,
                                                          @PathVariable Long taskInstanceId,
                                                          @RequestParam("file") MultipartFile file) {
        return Results.success(conferenceTaskAttachmentService.uploadAttachment(conferenceId, taskInstanceId, file, currentUserId()));
    }

    @GetMapping("/api/conferences/{conferenceId}/tasks/{taskInstanceId}/attachments")
    public Result<List<TaskAttachmentRespDTO>> listAttachments(@PathVariable Long conferenceId,
                                                               @PathVariable Long taskInstanceId) {
        return Results.success(conferenceTaskAttachmentService.listAttachments(conferenceId, taskInstanceId, currentUserId()));
    }

    @DeleteMapping("/api/conferences/{conferenceId}/tasks/{taskInstanceId}/attachments/{attachmentId}")
    public Result<Void> deleteAttachment(@PathVariable Long conferenceId,
                                         @PathVariable Long taskInstanceId,
                                         @PathVariable Long attachmentId) {
        conferenceTaskAttachmentService.deleteAttachment(conferenceId, taskInstanceId, attachmentId, currentUserId());
        return Results.success();
    }

    @PostMapping("/api/conferences/{conferenceId}/tasks/{taskInstanceId}/start")
    public Result<Void> startTask(@PathVariable Long conferenceId,
                                  @PathVariable Long taskInstanceId,
                                  @RequestBody(required = false) TaskActionReqDTO reqDTO) {
        conferenceTaskService.startTask(conferenceId, taskInstanceId, currentUserId(), reqDTO);
        return Results.success();
    }

    @PostMapping("/api/conferences/{conferenceId}/tasks/{taskInstanceId}/complete")
    public Result<TaskCompleteRespDTO> completeTask(@PathVariable Long conferenceId,
                                                    @PathVariable Long taskInstanceId,
                                                    @RequestBody(required = false) TaskCompleteReqDTO reqDTO) {
        return Results.success(conferenceTaskService.completeTask(conferenceId, taskInstanceId, currentUserId(), reqDTO));
    }

    @PostMapping("/api/conferences/{conferenceId}/tasks/{taskInstanceId}/transfer")
    public Result<Void> transferTask(@PathVariable Long conferenceId,
                                     @PathVariable Long taskInstanceId,
                                     @RequestBody TaskActionReqDTO reqDTO) {
        conferenceTaskService.transferTask(conferenceId, taskInstanceId, currentUserId(), reqDTO);
        return Results.success();
    }

    @PostMapping("/api/conferences/{conferenceId}/tasks/{taskInstanceId}/reject")
    public Result<Void> rejectTask(@PathVariable Long conferenceId,
                                   @PathVariable Long taskInstanceId,
                                   @RequestBody(required = false) TaskActionReqDTO reqDTO) {
        conferenceTaskService.rejectTask(conferenceId, taskInstanceId, currentUserId(), reqDTO);
        return Results.success();
    }

    @PostMapping("/api/conferences/{conferenceId}/tasks/{taskInstanceId}/cancel")
    public Result<Void> cancelTask(@PathVariable Long conferenceId,
                                   @PathVariable Long taskInstanceId,
                                   @RequestBody(required = false) TaskActionReqDTO reqDTO) {
        conferenceTaskService.cancelTask(conferenceId, taskInstanceId, currentUserId(), reqDTO);
        return Results.success();
    }

    @PostMapping("/api/conferences/{conferenceId}/tasks/{taskInstanceId}/comments")
    public Result<Void> addComment(@PathVariable Long conferenceId,
                                   @PathVariable Long taskInstanceId,
                                   @RequestBody(required = false) TaskActionReqDTO reqDTO) {
        conferenceTaskService.addComment(conferenceId, taskInstanceId, currentUserId(), reqDTO);
        return Results.success();
    }

    @GetMapping("/api/conferences/{conferenceId}/tasks/{taskInstanceId}/logs")
    public Result<List<TaskActionLogRespDTO>> listTaskLogs(@PathVariable Long conferenceId,
                                                           @PathVariable Long taskInstanceId) {
        return Results.success(conferenceTaskService.listTaskLogs(conferenceId, taskInstanceId, currentUserId()));
    }

    private String currentUserId() {
        String userId = UserContext.getUserId();
        return userId == null ? UserContext.getUsername() : userId;
    }
}
