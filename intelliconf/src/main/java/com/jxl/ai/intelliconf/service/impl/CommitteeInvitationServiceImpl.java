package com.jxl.ai.intelliconf.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jxl.ai.intelliconf.common.biz.user.UserContext;
import com.jxl.ai.intelliconf.common.constant.ConferenceTaskConstant;
import com.jxl.ai.intelliconf.common.convention.exception.ClientException;
import com.jxl.ai.intelliconf.dao.entity.ConfCommitteeRoleDefDO;
import com.jxl.ai.intelliconf.dao.entity.ConfMemberInvitationDO;
import com.jxl.ai.intelliconf.dao.entity.ConfMemberRoleDO;
import com.jxl.ai.intelliconf.dao.entity.ConfTaskDO;
import com.jxl.ai.intelliconf.dao.entity.ConferenceDO;
import com.jxl.ai.intelliconf.dao.entity.UserDO;
import com.jxl.ai.intelliconf.dao.mapper.ConfCommitteeRoleDefMapper;
import com.jxl.ai.intelliconf.dao.mapper.ConfMemberInvitationMapper;
import com.jxl.ai.intelliconf.dao.mapper.ConfMemberRoleMapper;
import com.jxl.ai.intelliconf.dao.mapper.ConfTaskMapper;
import com.jxl.ai.intelliconf.dao.mapper.ConferenceMapper;
import com.jxl.ai.intelliconf.dao.mapper.UserMapper;
import com.jxl.ai.intelliconf.dto.req.CommitteeInvitationDeclineReqDTO;
import com.jxl.ai.intelliconf.dto.req.CommitteeInvitationSaveReqDTO;
import com.jxl.ai.intelliconf.dto.resp.CommitteeInvitationRespDTO;
import com.jxl.ai.intelliconf.dto.resp.CommitteeInvitationTokenRespDTO;
import com.jxl.ai.intelliconf.dto.resp.CommitteeRoleRespDTO;
import com.jxl.ai.intelliconf.dto.resp.CommitteeSetupSummaryRespDTO;
import com.jxl.ai.intelliconf.dto.resp.TaskSystemCheckRespDTO;
import com.jxl.ai.intelliconf.service.CommitteeInvitationService;
import com.jxl.ai.intelliconf.service.ConferenceMemberRoleService;
import com.jxl.ai.intelliconf.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommitteeInvitationServiceImpl implements CommitteeInvitationService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final ConferenceMapper conferenceMapper;
    private final ConfTaskMapper taskMapper;
    private final ConfCommitteeRoleDefMapper roleDefMapper;
    private final ConfMemberInvitationMapper invitationMapper;
    private final ConfMemberRoleMapper memberRoleMapper;
    private final UserMapper userMapper;
    private final ConferenceMemberRoleService memberRoleService;
    private final EmailService emailService;

    @Value("${web.base-url:http://localhost:5173}")
    private String webBaseUrl;

    @Override
    public List<CommitteeRoleRespDTO> listCommitteeRoles(Long conferenceId, String currentUserId) {
        requireOrganizer(conferenceId, currentUserId);
        return roleDefMapper.selectList(Wrappers.lambdaQuery(ConfCommitteeRoleDefDO.class)
                        .eq(ConfCommitteeRoleDefDO::getStatus, 1)
                        .orderByAsc(ConfCommitteeRoleDefDO::getSortOrder)
                        .orderByAsc(ConfCommitteeRoleDefDO::getId))
                .stream()
                .map(this::toRoleResp)
                .toList();
    }

    @Override
    @Transactional
    public CommitteeInvitationRespDTO saveInvitation(Long conferenceId, CommitteeInvitationSaveReqDTO request, String currentUserId) {
        requireOrganizer(conferenceId, currentUserId);
        requireConference(conferenceId);
        ConfTaskDO task = requireCommitteeTask(conferenceId, request.getSourceTaskId());
        ConfCommitteeRoleDefDO roleDef = requireRoleDef(request.getRoleDefId());
        String email = normalizeEmail(request.getInviteeEmail());
        if (!StringUtils.hasText(email)) {
            throw new ClientException("Invitee email is required");
        }
        Date expiredAt = daysFromNow(14);
        ConfMemberInvitationDO existing = invitationMapper.selectOne(Wrappers.lambdaQuery(ConfMemberInvitationDO.class)
                .eq(ConfMemberInvitationDO::getConferenceId, conferenceId)
                .eq(ConfMemberInvitationDO::getInviteeEmail, email)
                .eq(ConfMemberInvitationDO::getRoleCode, roleDef.getRoleCode())
                .last("limit 1"));
        Date now = new Date();
        if (existing != null) {
            if ("ACCEPTED".equals(existing.getInvitationStatus())) {
                throw new ClientException("该邮箱已经是“" + roleDef.getRoleName() + "”成员，无需重复邀请");
            }
            existing.setSourceTaskId(task.getId());
            existing.setRoleDefId(roleDef.getId());
            existing.setRoleCode(roleDef.getRoleCode());
            existing.setRoleName(roleDef.getRoleName());
            existing.setCommitteeType(roleDef.getCommitteeType());
            existing.setCommitteeName(roleDef.getCommitteeName());
            existing.setInviteeName(trimToNull(request.getInviteeName()));
            existing.setInviteeEmail(email);
            existing.setInviteeAffiliation(trimToNull(request.getInviteeAffiliation()));
            existing.setInvitationToken(generateInvitationToken());
            existing.setInvitationStatus("DRAFT");
            existing.setExpiredAt(expiredAt);
            existing.setAcceptedUserId(null);
            existing.setAcceptedAt(null);
            existing.setDeclinedAt(null);
            existing.setDeclinedReason(null);
            existing.setCancelledAt(null);
            existing.setUpdatedAt(now);
            invitationMapper.updateById(existing);
            return toInvitationResp(existing);
        }
        ConfMemberInvitationDO invitation = ConfMemberInvitationDO.builder()
                .conferenceId(conferenceId)
                .sourceTaskId(task.getId())
                .roleDefId(roleDef.getId())
                .roleCode(roleDef.getRoleCode())
                .roleName(roleDef.getRoleName())
                .committeeType(roleDef.getCommitteeType())
                .committeeName(roleDef.getCommitteeName())
                .inviteeName(trimToNull(request.getInviteeName()))
                .inviteeEmail(email)
                .inviteeAffiliation(trimToNull(request.getInviteeAffiliation()))
                .invitationToken(generateInvitationToken())
                .invitationStatus("DRAFT")
                .sendCount(0)
                .expiredAt(expiredAt)
                .createdAt(now)
                .updatedAt(now)
                .build();
        invitationMapper.insert(invitation);
        return toInvitationResp(invitation);
    }

    @Override
    @Transactional
    public CommitteeInvitationRespDTO sendInvitation(Long conferenceId, Long invitationId, String currentUserId) {
        requireOrganizer(conferenceId, currentUserId);
        ConferenceDO conference = requireConference(conferenceId);
        ConfMemberInvitationDO invitation = requireInvitation(conferenceId, invitationId);
        if (!Set.of("DRAFT", "SENT", "EXPIRED").contains(invitation.getInvitationStatus())) {
            throw new ClientException("Current invitation status does not allow sending");
        }
        Date now = new Date();
        invitation.setInvitationToken(generateInvitationToken());
        invitation.setInvitationStatus("SENT");
        invitation.setExpiredAt(daysFromNow(14));
        invitation.setSentAt(invitation.getSentAt() == null ? now : invitation.getSentAt());
        invitation.setLastSentAt(now);
        invitation.setSendCount((invitation.getSendCount() == null ? 0 : invitation.getSendCount()) + 1);
        invitation.setUpdatedAt(now);
        invitationMapper.updateById(invitation);
        emailService.sendCommitteeInvitationEmail(conference, invitation, buildInviteLink(invitation.getInvitationToken()));
        return toInvitationResp(invitation);
    }

    @Override
    public List<CommitteeInvitationRespDTO> listInvitations(Long conferenceId, Long sourceTaskId, String invitationStatus, String roleCode, String currentUserId) {
        requireOrganizer(conferenceId, currentUserId);
        return invitationMapper.selectList(Wrappers.lambdaQuery(ConfMemberInvitationDO.class)
                        .eq(ConfMemberInvitationDO::getConferenceId, conferenceId)
                        .eq(sourceTaskId != null, ConfMemberInvitationDO::getSourceTaskId, sourceTaskId)
                        .eq(StringUtils.hasText(invitationStatus), ConfMemberInvitationDO::getInvitationStatus, invitationStatus)
                        .eq(StringUtils.hasText(roleCode), ConfMemberInvitationDO::getRoleCode, roleCode)
                        .orderByDesc(ConfMemberInvitationDO::getUpdatedAt)
                        .orderByDesc(ConfMemberInvitationDO::getId))
                .stream()
                .map(this::toInvitationResp)
                .toList();
    }

    @Override
    @Transactional
    public void cancelInvitation(Long conferenceId, Long invitationId, String currentUserId) {
        requireOrganizer(conferenceId, currentUserId);
        ConfMemberInvitationDO invitation = requireInvitation(conferenceId, invitationId);
        if (!Set.of("DRAFT", "SENT", "EXPIRED").contains(invitation.getInvitationStatus())) {
            throw new ClientException("Current invitation status does not allow cancellation");
        }
        invitation.setInvitationStatus("CANCELLED");
        invitation.setCancelledAt(new Date());
        invitation.setUpdatedAt(new Date());
        invitationMapper.updateById(invitation);
    }

    @Override
    @Transactional
    public void deleteInvitationAndMember(Long conferenceId, Long invitationId, String currentUserId) {
        requireOrganizer(conferenceId, currentUserId);
        ConfMemberInvitationDO invitation = requireInvitation(conferenceId, invitationId);
        if (invitation.getAcceptedUserId() != null) {
            memberRoleMapper.delete(Wrappers.lambdaQuery(ConfMemberRoleDO.class)
                    .eq(ConfMemberRoleDO::getConferenceId, conferenceId)
                    .eq(ConfMemberRoleDO::getSourceInvitationId, invitationId));
            ConfTaskDO clearAssignee = new ConfTaskDO();
            clearAssignee.setPrincipalUserId(null);
            clearAssignee.setPrincipalName(null);
            clearAssignee.setUpdateTime(new Date());
            taskMapper.update(clearAssignee, Wrappers.lambdaUpdate(ConfTaskDO.class)
                    .eq(ConfTaskDO::getConferenceId, conferenceId)
                    .eq(ConfTaskDO::getPrincipalUserId, invitation.getAcceptedUserId())
                    .eq(ConfTaskDO::getPrincipalRole, invitation.getRoleCode())
                    .set(ConfTaskDO::getPrincipalUserId, null)
                    .set(ConfTaskDO::getPrincipalName, null));
        }
        invitationMapper.deleteById(invitationId);
    }

    @Override
    public CommitteeInvitationTokenRespDTO getInvitationByToken(String token) {
        if (!StringUtils.hasText(token)) {
            throw new ClientException("Invitation token is required");
        }
        ConfMemberInvitationDO invitation = invitationMapper.selectOne(Wrappers.lambdaQuery(ConfMemberInvitationDO.class)
                .eq(ConfMemberInvitationDO::getInvitationToken, token)
                .last("limit 1"));
        if (invitation == null) {
            throw new ClientException("Invitation not found");
        }
        if ("CANCELLED".equals(invitation.getInvitationStatus())) {
            throw new ClientException("Invitation has been cancelled");
        }
        ConferenceDO conference = requireConference(invitation.getConferenceId());
        boolean expired = isExpired(invitation);
        if (expired && Set.of("SENT", "DRAFT").contains(invitation.getInvitationStatus())) {
            invitation.setInvitationStatus("EXPIRED");
            invitation.setUpdatedAt(new Date());
            invitationMapper.updateById(invitation);
        }
        return toTokenResp(conference, invitation, expired);
    }

    @Override
    @Transactional
    public CommitteeInvitationTokenRespDTO acceptInvitation(String token, String currentUserId) {
        ConfMemberInvitationDO invitation = requireInvitationByToken(token);
        if (!"SENT".equals(invitation.getInvitationStatus())) {
            throw new ClientException("Invitation is not available for acceptance");
        }
        if (isExpired(invitation)) {
            invitation.setInvitationStatus("EXPIRED");
            invitation.setUpdatedAt(new Date());
            invitationMapper.updateById(invitation);
            throw new ClientException("Invitation has expired");
        }
        UserDO currentUser = requireCurrentUser(currentUserId);
        if (!normalizeEmail(invitation.getInviteeEmail()).equals(normalizeEmail(currentUser.getEmail()))) {
            throw new ClientException("请使用被邀请邮箱登录。");
        }
        Date now = new Date();
        invitation.setInvitationStatus("ACCEPTED");
        invitation.setAcceptedUserId(currentUser.getId());
        invitation.setAcceptedAt(now);
        invitation.setUpdatedAt(now);
        invitationMapper.updateById(invitation);

        ConfMemberRoleDO existingRole = memberRoleMapper.selectOne(Wrappers.lambdaQuery(ConfMemberRoleDO.class)
                .eq(ConfMemberRoleDO::getConferenceId, invitation.getConferenceId())
                .eq(ConfMemberRoleDO::getUserId, currentUser.getId())
                .eq(ConfMemberRoleDO::getRoleCode, invitation.getRoleCode())
                .last("limit 1"));
        if (existingRole == null) {
            existingRole = ConfMemberRoleDO.builder()
                    .conferenceId(invitation.getConferenceId())
                    .userId(currentUser.getId())
                    .sourceInvitationId(invitation.getId())
                    .roleDefId(invitation.getRoleDefId())
                    .roleCode(invitation.getRoleCode())
                    .roleName(invitation.getRoleName())
                    .committeeType(invitation.getCommitteeType())
                    .committeeName(invitation.getCommitteeName())
                    .memberName(StringUtils.hasText(currentUser.getRealName()) ? currentUser.getRealName() : currentUser.getUsername())
                    .memberEmail(currentUser.getEmail())
                    .affiliation(invitation.getInviteeAffiliation())
                    .memberStatus(ConferenceTaskConstant.MEMBER_STATUS_ACTIVE)
                    .joinedAt(now)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            memberRoleMapper.insert(existingRole);
        } else {
            existingRole.setSourceInvitationId(invitation.getId());
            existingRole.setRoleDefId(invitation.getRoleDefId());
            existingRole.setRoleName(invitation.getRoleName());
            existingRole.setCommitteeType(invitation.getCommitteeType());
            existingRole.setCommitteeName(invitation.getCommitteeName());
            existingRole.setMemberName(StringUtils.hasText(currentUser.getRealName()) ? currentUser.getRealName() : currentUser.getUsername());
            existingRole.setMemberEmail(currentUser.getEmail());
            existingRole.setAffiliation(invitation.getInviteeAffiliation());
            existingRole.setMemberStatus(ConferenceTaskConstant.MEMBER_STATUS_ACTIVE);
            existingRole.setJoinedAt(now);
            existingRole.setUpdatedAt(now);
            memberRoleMapper.updateById(existingRole);
        }
        ConferenceDO conference = requireConference(invitation.getConferenceId());
        return toTokenResp(conference, invitation, false);
    }

    @Override
    @Transactional
    public void declineInvitation(String token, CommitteeInvitationDeclineReqDTO request) {
        ConfMemberInvitationDO invitation = requireInvitationByToken(token);
        if (!"SENT".equals(invitation.getInvitationStatus())) {
            throw new ClientException("Invitation is not available for decline");
        }
        if (isExpired(invitation)) {
            invitation.setInvitationStatus("EXPIRED");
            invitation.setUpdatedAt(new Date());
            invitationMapper.updateById(invitation);
            throw new ClientException("Invitation has expired");
        }
        invitation.setInvitationStatus("DECLINED");
        invitation.setDeclinedAt(new Date());
        invitation.setDeclinedReason(request == null ? null : trimToNull(request.getDeclinedReason()));
        invitation.setUpdatedAt(new Date());
        invitationMapper.updateById(invitation);
    }

    @Override
    public CommitteeSetupSummaryRespDTO getCommitteeSetupSummary(Long conferenceId, String currentUserId) {
        requireOrganizer(conferenceId, currentUserId);
        List<ConfCommitteeRoleDefDO> requiredRoles = roleDefMapper.selectList(Wrappers.lambdaQuery(ConfCommitteeRoleDefDO.class)
                .eq(ConfCommitteeRoleDefDO::getStatus, 1)
                .eq(ConfCommitteeRoleDefDO::getIsRequired, 1)
                .orderByAsc(ConfCommitteeRoleDefDO::getSortOrder)
                .orderByAsc(ConfCommitteeRoleDefDO::getId));
        Set<String> activeRoleCodes = memberRoleMapper.selectList(Wrappers.lambdaQuery(ConfMemberRoleDO.class)
                        .select(ConfMemberRoleDO::getRoleCode)
                        .eq(ConfMemberRoleDO::getConferenceId, conferenceId)
                        .eq(ConfMemberRoleDO::getMemberStatus, ConferenceTaskConstant.MEMBER_STATUS_ACTIVE))
                .stream()
                .map(ConfMemberRoleDO::getRoleCode)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(HashSet::new));
        List<TaskSystemCheckRespDTO.MissingRequiredRoleRespDTO> missingRequiredRoles = requiredRoles.stream()
                .filter(item -> !activeRoleCodes.contains(item.getRoleCode()))
                .map(item -> TaskSystemCheckRespDTO.MissingRequiredRoleRespDTO.builder()
                        .roleCode(item.getRoleCode())
                        .roleName(item.getRoleName())
                        .build())
                .toList();
        List<ConfMemberInvitationDO> invitations = invitationMapper.selectList(Wrappers.lambdaQuery(ConfMemberInvitationDO.class)
                .eq(ConfMemberInvitationDO::getConferenceId, conferenceId));
        return CommitteeSetupSummaryRespDTO.builder()
                .requiredRoleCount(requiredRoles.size())
                .acceptedRequiredRoleCount(requiredRoles.size() - missingRequiredRoles.size())
                .missingRequiredRoles(missingRequiredRoles)
                .totalInvitationCount(invitations.size())
                .sentCount((int) invitations.stream().filter(item -> "SENT".equals(item.getInvitationStatus())).count())
                .acceptedCount((int) invitations.stream().filter(item -> "ACCEPTED".equals(item.getInvitationStatus())).count())
                .declinedCount((int) invitations.stream().filter(item -> "DECLINED".equals(item.getInvitationStatus())).count())
                .expiredCount((int) invitations.stream().filter(item -> "EXPIRED".equals(item.getInvitationStatus())).count())
                .canCompleteCommitteeTask(missingRequiredRoles.isEmpty())
                .build();
    }

    private void requireOrganizer(Long conferenceId, String currentUserId) {
        requireConference(conferenceId);
        memberRoleService.requireOrganizer(conferenceId, currentUserId);
    }

    private ConferenceDO requireConference(Long conferenceId) {
        ConferenceDO conference = conferenceMapper.selectOne(Wrappers.lambdaQuery(ConferenceDO.class)
                .eq(ConferenceDO::getId, conferenceId)
                .eq(ConferenceDO::getDelFlag, 0)
                .last("limit 1"));
        if (conference == null) {
            throw new ClientException("Conference not found");
        }
        return conference;
    }

    private ConfTaskDO requireCommitteeTask(Long conferenceId, Long taskId) {
        ConfTaskDO task = taskMapper.selectOne(Wrappers.lambdaQuery(ConfTaskDO.class)
                .eq(ConfTaskDO::getId, taskId)
                .eq(ConfTaskDO::getConferenceId, conferenceId)
                .last("limit 1"));
        if (task == null) {
            throw new ClientException("Task not found");
        }
        if (!ConferenceTaskConstant.TASK_CODE_CONFIRM_CONFERENCE_COMMITTEE.equals(task.getTaskCode())) {
            throw new ClientException("Source task is not CONFIRM_CONFERENCE_COMMITTEE");
        }
        return task;
    }

    private ConfCommitteeRoleDefDO requireRoleDef(Long roleDefId) {
        ConfCommitteeRoleDefDO roleDef = roleDefMapper.selectOne(Wrappers.lambdaQuery(ConfCommitteeRoleDefDO.class)
                .eq(ConfCommitteeRoleDefDO::getId, roleDefId)
                .eq(ConfCommitteeRoleDefDO::getStatus, 1)
                .last("limit 1"));
        if (roleDef == null) {
            throw new ClientException("Committee role does not exist");
        }
        return roleDef;
    }

    private ConfMemberInvitationDO requireInvitation(Long conferenceId, Long invitationId) {
        ConfMemberInvitationDO invitation = invitationMapper.selectOne(Wrappers.lambdaQuery(ConfMemberInvitationDO.class)
                .eq(ConfMemberInvitationDO::getId, invitationId)
                .eq(ConfMemberInvitationDO::getConferenceId, conferenceId)
                .last("limit 1"));
        if (invitation == null) {
            throw new ClientException("Invitation not found");
        }
        return invitation;
    }

    private ConfMemberInvitationDO requireInvitationByToken(String token) {
        if (!StringUtils.hasText(token)) {
            throw new ClientException("Invitation token is required");
        }
        ConfMemberInvitationDO invitation = invitationMapper.selectOne(Wrappers.lambdaQuery(ConfMemberInvitationDO.class)
                .eq(ConfMemberInvitationDO::getInvitationToken, token)
                .last("limit 1"));
        if (invitation == null) {
            throw new ClientException("Invitation not found");
        }
        if ("CANCELLED".equals(invitation.getInvitationStatus())) {
            throw new ClientException("Invitation has been cancelled");
        }
        if ("ACCEPTED".equals(invitation.getInvitationStatus())) {
            throw new ClientException("Invitation has already been accepted");
        }
        if ("DECLINED".equals(invitation.getInvitationStatus())) {
            throw new ClientException("Invitation has already been declined");
        }
        return invitation;
    }

    private UserDO requireCurrentUser(String currentUserId) {
        Long userId = parseLongSafely(currentUserId);
        UserDO user = userId == null ? null : userMapper.selectById(userId);
        if (user == null && StringUtils.hasText(UserContext.getUsername())) {
            user = userMapper.selectOne(Wrappers.lambdaQuery(UserDO.class)
                    .eq(UserDO::getUsername, UserContext.getUsername())
                    .eq(UserDO::getDelFlag, 0)
                    .last("limit 1"));
        }
        if (user == null) {
            throw new ClientException("Current user not found");
        }
        return user;
    }

    private CommitteeRoleRespDTO toRoleResp(ConfCommitteeRoleDefDO item) {
        return CommitteeRoleRespDTO.builder()
                .roleDefId(item.getId())
                .roleCode(item.getRoleCode())
                .roleName(item.getRoleName())
                .committeeType(item.getCommitteeType())
                .committeeName(item.getCommitteeName())
                .roleDesc(item.getRoleDesc())
                .isRequired(item.getIsRequired())
                .canAssignTask(item.getCanAssignTask())
                .build();
    }

    private CommitteeInvitationRespDTO toInvitationResp(ConfMemberInvitationDO item) {
        return CommitteeInvitationRespDTO.builder()
                .invitationId(item.getId())
                .sourceTaskId(item.getSourceTaskId())
                .roleDefId(item.getRoleDefId())
                .inviteeName(item.getInviteeName())
                .inviteeEmail(item.getInviteeEmail())
                .inviteeAffiliation(item.getInviteeAffiliation())
                .roleCode(item.getRoleCode())
                .roleName(item.getRoleName())
                .committeeType(item.getCommitteeType())
                .committeeName(item.getCommitteeName())
                .invitationStatus(item.getInvitationStatus())
                .sendCount(item.getSendCount())
                .sentAt(item.getSentAt())
                .lastSentAt(item.getLastSentAt())
                .acceptedAt(item.getAcceptedAt())
                .declinedAt(item.getDeclinedAt())
                .expiredAt(item.getExpiredAt())
                .cancelledAt(item.getCancelledAt())
                .declinedReason(item.getDeclinedReason())
                .build();
    }

    private CommitteeInvitationTokenRespDTO toTokenResp(ConferenceDO conference, ConfMemberInvitationDO invitation, boolean expired) {
        return CommitteeInvitationTokenRespDTO.builder()
                .invitationId(invitation.getId())
                .conferenceId(invitation.getConferenceId())
                .conferenceName(StringUtils.hasText(conference.getTitle()) ? conference.getTitle() : conference.getShortName())
                .conferenceShortName(conference.getShortName())
                .inviteeEmail(invitation.getInviteeEmail())
                .inviteeName(invitation.getInviteeName())
                .roleName(invitation.getRoleName())
                .committeeName(invitation.getCommitteeName())
                .invitationStatus(invitation.getInvitationStatus())
                .expired(expired)
                .expiredAt(invitation.getExpiredAt())
                .build();
    }

    private String buildInviteLink(String token) {
        return webBaseUrl + "/committee-invite?token=" + token;
    }

    private String generateInvitationToken() {
        byte[] bytes = new byte[24];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private boolean isExpired(ConfMemberInvitationDO invitation) {
        return invitation.getExpiredAt() != null && invitation.getExpiredAt().before(new Date());
    }

    private Date daysFromNow(int days) {
        return Date.from(LocalDateTime.now().plusDays(days).atZone(ZoneId.systemDefault()).toInstant());
    }

    private String normalizeEmail(String email) {
        return StringUtils.hasText(email) ? email.trim().toLowerCase() : null;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Long parseLongSafely(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
