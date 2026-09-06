package com.jxl.ai.intelliconf.controller;

import com.jxl.ai.intelliconf.common.biz.user.UserContext;
import com.jxl.ai.intelliconf.common.convention.result.Result;
import com.jxl.ai.intelliconf.common.convention.result.Results;
import com.jxl.ai.intelliconf.dto.req.CommitteeInvitationDeclineReqDTO;
import com.jxl.ai.intelliconf.dto.req.CommitteeInvitationSaveReqDTO;
import com.jxl.ai.intelliconf.dto.resp.CommitteeInvitationRespDTO;
import com.jxl.ai.intelliconf.dto.resp.CommitteeInvitationTokenRespDTO;
import com.jxl.ai.intelliconf.dto.resp.CommitteeRoleRespDTO;
import com.jxl.ai.intelliconf.dto.resp.CommitteeSetupSummaryRespDTO;
import com.jxl.ai.intelliconf.service.CommitteeInvitationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class CommitteeInvitationController {

    private final CommitteeInvitationService committeeInvitationService;

    @GetMapping("/api/conferences/{conferenceId}/committee/roles")
    public Result<List<CommitteeRoleRespDTO>> listRoles(@PathVariable Long conferenceId) {
        return Results.success(committeeInvitationService.listCommitteeRoles(conferenceId, currentUserId()));
    }

    @PostMapping("/api/conferences/{conferenceId}/committee/invitations")
    public Result<CommitteeInvitationRespDTO> saveInvitation(@PathVariable Long conferenceId,
                                                             @RequestBody CommitteeInvitationSaveReqDTO request) {
        return Results.success(committeeInvitationService.saveInvitation(conferenceId, request, currentUserId()));
    }

    @PostMapping("/api/conferences/{conferenceId}/committee/invitations/{invitationId}/send")
    public Result<CommitteeInvitationRespDTO> sendInvitation(@PathVariable Long conferenceId,
                                                             @PathVariable Long invitationId) {
        return Results.success(committeeInvitationService.sendInvitation(conferenceId, invitationId, currentUserId()));
    }

    @GetMapping("/api/conferences/{conferenceId}/committee/invitations")
    public Result<List<CommitteeInvitationRespDTO>> listInvitations(@PathVariable Long conferenceId,
                                                                    @RequestParam(required = false) Long sourceTaskId,
                                                                    @RequestParam(required = false) String invitationStatus,
                                                                    @RequestParam(required = false) String roleCode) {
        return Results.success(committeeInvitationService.listInvitations(conferenceId, sourceTaskId, invitationStatus, roleCode, currentUserId()));
    }

    @PutMapping("/api/conferences/{conferenceId}/committee/invitations/{invitationId}/cancel")
    public Result<Void> cancelInvitation(@PathVariable Long conferenceId,
                                         @PathVariable Long invitationId) {
        committeeInvitationService.cancelInvitation(conferenceId, invitationId, currentUserId());
        return Results.success();
    }

    @DeleteMapping("/api/conferences/{conferenceId}/committee/invitations/{invitationId}")
    public Result<Void> deleteInvitationAndMember(@PathVariable Long conferenceId,
                                                   @PathVariable Long invitationId) {
        committeeInvitationService.deleteInvitationAndMember(conferenceId, invitationId, currentUserId());
        return Results.success();
    }

    @GetMapping("/api/conferences/{conferenceId}/committee/setup-summary")
    public Result<CommitteeSetupSummaryRespDTO> getCommitteeSummary(@PathVariable Long conferenceId) {
        return Results.success(committeeInvitationService.getCommitteeSetupSummary(conferenceId, currentUserId()));
    }

    @GetMapping("/api/committee-invitations/token/{token}")
    public Result<CommitteeInvitationTokenRespDTO> getInvitationByToken(@PathVariable String token) {
        return Results.success(committeeInvitationService.getInvitationByToken(token));
    }

    @PostMapping("/api/committee-invitations/token/{token}/accept")
    public Result<CommitteeInvitationTokenRespDTO> acceptInvitation(@PathVariable String token) {
        return Results.success(committeeInvitationService.acceptInvitation(token, currentUserId()));
    }

    @PostMapping("/api/committee-invitations/token/{token}/decline")
    public Result<Void> declineInvitation(@PathVariable String token,
                                          @RequestBody(required = false) CommitteeInvitationDeclineReqDTO request) {
        committeeInvitationService.declineInvitation(token, request);
        return Results.success();
    }

    private String currentUserId() {
        String userId = UserContext.getUserId();
        return userId == null ? UserContext.getUsername() : userId;
    }
}
