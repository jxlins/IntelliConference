package com.jxl.ai.intelliconf.service.impl;

import com.jxl.ai.intelliconf.dao.entity.ConfMemberInvitationDO;
import com.jxl.ai.intelliconf.dao.entity.ConferenceDO;
import com.jxl.ai.intelliconf.dao.entity.MailTemplateDO;
import com.jxl.ai.intelliconf.dao.mapper.MailTemplateMapper;
import com.jxl.ai.intelliconf.common.convention.exception.ClientException;
import com.jxl.ai.intelliconf.dto.resp.BasicMailSendRespDTO;
import com.jxl.ai.intelliconf.dto.resp.ContactDTO;
import com.jxl.ai.intelliconf.service.BasicMailSendService;
import com.jxl.ai.intelliconf.service.EmailService;
import com.jxl.ai.intelliconf.mail.infrastructure.TemplateRenderService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final BasicMailSendService basicMailSendService;
    private final MailTemplateMapper mailTemplateMapper;
    private final TemplateRenderService templateRenderService;

    @Override
    public void sendCommitteeInvitationEmail(ConferenceDO conference, ConfMemberInvitationDO invitation, String inviteLink) {
        String conferenceName = conference == null
                ? "Conference"
                : (StringUtils.hasText(conference.getTitle()) ? conference.getTitle() : conference.getShortName());
        String subject;
        String expireText = invitation.getExpiredAt() == null
                ? "N/A"
                : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(invitation.getExpiredAt());
        String recipientName = StringUtils.hasText(invitation.getInviteeName()) ? invitation.getInviteeName() : invitation.getInviteeEmail();
        Map<String, Object> variables = new HashMap<>();
        variables.put("name", recipientName);
        variables.put("conferenceName", conferenceName);
        variables.put("conferenceShortName", conference == null ? "" : conference.getShortName());
        variables.put("role", invitation.getRoleName());
        variables.put("committee", invitation.getCommitteeName());
        variables.put("inviteLink", inviteLink);
        variables.put("expireTime", expireText);
        MailTemplateDO template = conference == null ? null : mailTemplateMapper.selectOne(
                Wrappers.lambdaQuery(MailTemplateDO.class)
                        .eq(MailTemplateDO::getConferenceId, conference.getId())
                        .eq(MailTemplateDO::getSceneCode, "COMMITTEE_INVITATION")
                        .eq(MailTemplateDO::getEnabled, true)
                        .orderByDesc(MailTemplateDO::getUpdatedAt)
                        .last("limit 1"));
        if (template != null) {
            subject = templateRenderService.render(template.getSubjectTemplate(), variables);
        } else {
            subject = "【" + conferenceName + "】会议委员会任职邀请 / Committee Invitation";
        }
        String bodyTemplate = template == null ? null
                : (StringUtils.hasText(template.getHtmlTemplate()) ? template.getHtmlTemplate() : template.getTextTemplate());
        String body = StringUtils.hasText(bodyTemplate)
                ? templateRenderService.render(bodyTemplate, variables)
                : buildDefaultInvitationBody(recipientName, conferenceName, invitation, inviteLink, expireText);
        BasicMailSendRespDTO result;
        try {
            result = basicMailSendService.sendBatch(
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
        } catch (Exception ex) {
            log.error("Send committee invitation email failed, conferenceId={}, email={}",
                    conference == null ? null : conference.getId(), invitation.getInviteeEmail(), ex);
            throw new ClientException("邀请邮件发送失败，请检查会议邮箱配置或稍后重试：" + safeMessage(ex));
        }

        if (result == null || result.getSuccessCount() == null || result.getSuccessCount() < 1) {
            String detail = result == null || result.getFailedDetails() == null || result.getFailedDetails().isEmpty()
                    ? "邮件服务未返回成功结果"
                    : result.getFailedDetails().get(0);
            throw new ClientException("邀请邮件发送失败：" + detail);
        }
        log.info("Committee invitation email sent, conferenceId={}, email={}",
                conference == null ? null : conference.getId(), invitation.getInviteeEmail());
    }

    private String safeMessage(Exception ex) {
        if (ex == null || !StringUtils.hasText(ex.getMessage())) {
            return "未知邮件服务错误";
        }
        String message = ex.getMessage().replaceAll("[\\r\\n\\t]+", " ").trim();
        return message.length() > 180 ? message.substring(0, 180) : message;
    }

    private String buildDefaultInvitationBody(String name, String conferenceName,
                                              ConfMemberInvitationDO invitation,
                                              String inviteLink, String expireText) {
        return """
                <div style="font-family:Arial,'Microsoft YaHei',sans-serif;color:#243047;line-height:1.7;max-width:680px;margin:auto">
                  <h2 style="color:#244fba">会议委员会任职邀请</h2>
                  <p>尊敬的 %s：</p>
                  <p>诚挚邀请您加入 <strong>%s</strong>，担任 <strong>%s</strong>（%s）。</p>
                  <p>接受邀请后，您将可以登录会议系统查看并处理该角色负责的会议事务。</p>
                  <p style="margin:28px 0"><a href="%s" style="padding:12px 22px;background:#315fce;color:#fff;text-decoration:none;border-radius:8px">查看并确认邀请</a></p>
                  <p style="color:#68748a;font-size:13px">邀请有效期至：%s<br>若按钮无法打开，请复制以下地址：<br>%s</p>
                  <hr style="border:none;border-top:1px solid #e6eaf0;margin:24px 0">
                  <p>Dear %s, you are invited to serve as <strong>%s</strong> for <strong>%s</strong>. Please use the link above to accept or decline.</p>
                  <p>%s 组委会</p>
                </div>
                """.formatted(name, conferenceName, invitation.getRoleName(), invitation.getCommitteeName(),
                inviteLink, expireText, inviteLink, name, invitation.getRoleName(), conferenceName, conferenceName);
    }
}
