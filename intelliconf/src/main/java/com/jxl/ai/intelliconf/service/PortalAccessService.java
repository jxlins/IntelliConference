package com.jxl.ai.intelliconf.service;

import com.jxl.ai.intelliconf.common.biz.portal.PortalCommitteePrincipal;
import com.jxl.ai.intelliconf.dao.entity.ConfCommitteeDO;
import com.jxl.ai.intelliconf.dto.req.CommitteeMemberInputReqDTO;

import java.util.Date;
import java.util.List;

public interface PortalAccessService {

    String generateToken(String email, String role);

    String generateToken(Long confId, String email, String name, String role, Date tokenExpireTime);

    PortalCommitteePrincipal validateToken(String token);

    void decideByToken(String token, boolean agree);

    List<ConfCommitteeDO> listByConferenceId(Long confId);

    List<ConfCommitteeDO> listAllByConferenceId(Long confId);

    int importMembersByChairToken(String token, List<CommitteeMemberInputReqDTO> members);
}
