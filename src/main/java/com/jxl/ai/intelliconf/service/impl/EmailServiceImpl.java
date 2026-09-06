package com.jxl.ai.intelliconf.service.impl;

import com.jxl.ai.intelliconf.dao.entity.ConfMemberInvitationDO;
import com.jxl.ai.intelliconf.dao.entity.ConferenceDO;
import com.jxl.ai.intelliconf.dto.resp.BasicMailSendRespDTO;
import com.jxl.ai.intelliconf.dto.resp.ContactDTO;
import com.jxl.ai.intelliconf.service.BasicMailSendService;
import com.jxl.ai.intelliconf.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final BasicMailSendService basicMailSendService;

    @Value("${web.base-url:http://localhost:5173}")
    private String webBaseUrl;

    @Override
    public void sendCommitteeInvitationEmail(ConferenceDO conference, ConfMemberInvitationDO invitation, String inviteLink) {
        String conferenceName = conference == null
                ? "Conference"
                : (StringUtils.hasText(conference.getTitle()) ? conference.getTitle() : conference.getShortName());
        String subject = "Invitation to Join the Committee of " + conferenceName;
        String expireText = invitation.getExpiredAt() == null
                ? "N/A"
                : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(invitation.getExpiredAt());
        String body = """
                <p>Dear %s,</p>
                <p>You are invited to join the committee of <strong>%s</strong>.</p>
                <p>Role: %s</p>
                <p>Committee: %s</p>
                <p>Accept or decline here:</p>
                <p><a href="%s">%s</a></p>
                <p>Invitation expires at: %s</p>
                <p>Conference Secretariat</p>
                """.formatted(
                StringUtils.hasText(invitation.getInviteeName()) ? invitation.getInviteeName() : invitation.getInviteeEmail(),
                conferenceName,
                invitation.getRoleName(),
                invitation.getCommitteeName(),
                inviteLink,
                inviteLink,
                expireText
        );
        try {
            BasicMailSendRespDTO result = basicMailSendService.sendBatch(
                    conference == null ? null : conference.getId(),
                    null,
                    subject,
                    body,
                    List.of(ContactDTO.builder()
                            .name(invitation.getInviteeName())
                            .email(invitation.getInviteeEmail())
                            .build()),
                    false
            );
            if (result.getSuccessCount() == null || result.getSuccessCount() < 1) {
                logInviteFallback(subject, inviteLink, invitation.getInviteeEmail());
            }
        } catch (Exception ex) {
            log.warn("Send committee invitation email failed, fallback to log. conferenceId={}, email={}",
                    conference == null ? null : conference.getId(), invitation.getInviteeEmail(), ex);
            logInviteFallback(subject, inviteLink, invitation.getInviteeEmail());
        }
    }

    private void logInviteFallback(String subject, String inviteLink, String email) {
        log.info("[CommitteeInvitationEmailMock] to={}, subject={}, inviteLink={}, webBaseUrl={}",
                email, subject, inviteLink, webBaseUrl);
    }
}
