package com.jxl.ai.intelliconf.service;

import java.util.Set;

public interface ConferenceMemberRoleService {
    Set<String> getRoleCodes(Long conferenceId, String userId);
    boolean isConferenceMember(Long conferenceId, String userId);
    boolean hasRole(Long conferenceId, String userId, String roleCode);
    boolean isOrganizer(Long conferenceId, String userId);
    void requireMember(Long conferenceId, String userId);
    void requireOrganizer(Long conferenceId, String userId);
}
