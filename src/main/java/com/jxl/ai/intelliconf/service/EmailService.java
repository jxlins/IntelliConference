package com.jxl.ai.intelliconf.service;

import com.jxl.ai.intelliconf.dao.entity.ConfMemberInvitationDO;
import com.jxl.ai.intelliconf.dao.entity.ConferenceDO;

public interface EmailService {

    void sendCommitteeInvitationEmail(ConferenceDO conference, ConfMemberInvitationDO invitation, String inviteLink);
}
