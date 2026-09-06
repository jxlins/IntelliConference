package com.jxl.ai.intelliconf.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jxl.ai.intelliconf.common.biz.user.UserContext;
import com.jxl.ai.intelliconf.common.constant.ConferenceTaskConstant;
import com.jxl.ai.intelliconf.common.convention.exception.ClientException;
import com.jxl.ai.intelliconf.dao.entity.ConfMemberRoleDO;
import com.jxl.ai.intelliconf.dao.entity.ConferenceDO;
import com.jxl.ai.intelliconf.dao.mapper.ConfMemberRoleMapper;
import com.jxl.ai.intelliconf.dao.mapper.ConferenceMapper;
import com.jxl.ai.intelliconf.service.ConferenceMemberRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ConferenceMemberRoleServiceImpl implements ConferenceMemberRoleService {

    private final ConferenceMapper conferenceMapper;
    private final ConfMemberRoleMapper memberRoleMapper;

    @Override
    public Set<String> getRoleCodes(Long conferenceId, String userId) {
        Set<String> roles = new HashSet<>();
        if (isOrganizer(conferenceId, userId)) {
            roles.add("ORGANIZER");
            return roles;
        }
        Long userIdValue = parseLongSafely(userId);
        if (userIdValue == null) {
            return roles;
        }
        memberRoleMapper.selectList(Wrappers.lambdaQuery(ConfMemberRoleDO.class)
                        .eq(ConfMemberRoleDO::getConferenceId, conferenceId)
                        .eq(ConfMemberRoleDO::getUserId, userIdValue)
                        .eq(ConfMemberRoleDO::getMemberStatus, ConferenceTaskConstant.MEMBER_STATUS_ACTIVE))
                .forEach(item -> {
                    if (StringUtils.hasText(item.getRoleCode())) {
                        roles.add(item.getRoleCode().trim().toUpperCase());
                    }
                });
        return roles;
    }

    @Override
    public boolean isConferenceMember(Long conferenceId, String userId) {
        return isOrganizer(conferenceId, userId) || !getRoleCodes(conferenceId, userId).isEmpty();
    }

    @Override
    public boolean hasRole(Long conferenceId, String userId, String roleCode) {
        return getRoleCodes(conferenceId, userId).contains(roleCode);
    }

    @Override
    public boolean isOrganizer(Long conferenceId, String userId) {
        if (!StringUtils.hasText(userId)) {
            return false;
        }
        ConferenceDO conference = conferenceMapper.selectById(conferenceId);
        if (conference == null || !StringUtils.hasText(conference.getCreateUser())) {
            return false;
        }
        if (userId.equals(conference.getCreateUser())) {
            return true;
        }
        String username = UserContext.getUsername();
        return StringUtils.hasText(username) && username.equals(conference.getCreateUser());
    }

    @Override
    public void requireMember(Long conferenceId, String userId) {
        if (!isConferenceMember(conferenceId, userId)) {
            throw new ClientException("No permission to access this conference");
        }
    }

    @Override
    public void requireOrganizer(Long conferenceId, String userId) {
        if (!isOrganizer(conferenceId, userId)) {
            throw new ClientException("Only conference organizer can perform this operation");
        }
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
