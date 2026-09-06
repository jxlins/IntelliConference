package com.jxl.ai.intelliconf.service;

import com.jxl.ai.intelliconf.dto.req.CommitteeInvitationDeclineReqDTO;
import com.jxl.ai.intelliconf.dto.req.CommitteeInvitationSaveReqDTO;
import com.jxl.ai.intelliconf.dto.resp.CommitteeInvitationRespDTO;
import com.jxl.ai.intelliconf.dto.resp.CommitteeInvitationTokenRespDTO;
import com.jxl.ai.intelliconf.dto.resp.CommitteeRoleRespDTO;
import com.jxl.ai.intelliconf.dto.resp.CommitteeSetupSummaryRespDTO;

import java.util.List;

public interface CommitteeInvitationService {

    List<CommitteeRoleRespDTO> listCommitteeRoles(Long conferenceId, String currentUserId);

    CommitteeInvitationRespDTO saveInvitation(Long conferenceId, CommitteeInvitationSaveReqDTO request, String currentUserId);

    CommitteeInvitationRespDTO sendInvitation(Long conferenceId, Long invitationId, String currentUserId);

    List<CommitteeInvitationRespDTO> listInvitations(Long conferenceId, Long sourceTaskId, String invitationStatus, String roleCode, String currentUserId);

    void cancelInvitation(Long conferenceId, Long invitationId, String currentUserId);

    CommitteeInvitationTokenRespDTO getInvitationByToken(String token);

    CommitteeInvitationTokenRespDTO acceptInvitation(String token, String currentUserId);

    void declineInvitation(String token, CommitteeInvitationDeclineReqDTO request);

    CommitteeSetupSummaryRespDTO getCommitteeSetupSummary(Long conferenceId, String currentUserId);
}
