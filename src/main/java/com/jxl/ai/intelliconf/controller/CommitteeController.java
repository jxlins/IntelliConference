package com.jxl.ai.intelliconf.controller;

import com.jxl.ai.intelliconf.common.convention.exception.ClientException;
import com.jxl.ai.intelliconf.common.convention.result.Result;
import com.jxl.ai.intelliconf.common.convention.result.Results;
import com.jxl.ai.intelliconf.dao.entity.ConfCommitteeDO;
import com.jxl.ai.intelliconf.dao.entity.ConferenceDO;
import com.jxl.ai.intelliconf.dao.entity.MailTemplateDO;
import com.jxl.ai.intelliconf.dao.mapper.ConferenceMapper;
import com.jxl.ai.intelliconf.dao.mapper.MailTemplateMapper;
import com.jxl.ai.intelliconf.dto.req.CommitteeInviteReqDTO;
import com.jxl.ai.intelliconf.dto.resp.CommitteeInviteRespDTO;
import com.jxl.ai.intelliconf.dto.resp.BasicMailSendRespDTO;
import com.jxl.ai.intelliconf.dto.resp.ContactDTO;
import com.jxl.ai.intelliconf.enums.ConferenceRole;
import com.jxl.ai.intelliconf.mail.infrastructure.TemplateRenderService;
import com.jxl.ai.intelliconf.service.BasicMailSendService;
import com.jxl.ai.intelliconf.service.ConferencePermissionService;
import com.jxl.ai.intelliconf.service.PortalAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class CommitteeController {

    private final ConferencePermissionService conferencePermissionService;
    private final PortalAccessService portalAccessService;
    private final BasicMailSendService basicMailSendService;
    private final MailTemplateMapper mailTemplateMapper;
    private final TemplateRenderService templateRenderService;
    private final ConferenceMapper conferenceMapper;

    @PostMapping("/api/intelli-conf/v1/conference/{confId}/committee/invite")
    public Result<CommitteeInviteRespDTO> invite(@PathVariable Long confId, @RequestBody CommitteeInviteReqDTO reqDTO) {
        conferencePermissionService.requireAtLeastRole(confId, ConferenceRole.COMMITTEE);

        if (reqDTO == null || !StringUtils.hasText(reqDTO.getEmail())) {
            throw new ClientException("邮箱不能为空");
        }
        if (!StringUtils.hasText(reqDTO.getRole())) {
            throw new ClientException("委员会角色不能为空");
        }
        if (reqDTO.getTemplateId() == null) {
            throw new ClientException("请选择邮件模板");
        }

        String token = portalAccessService.generateToken(
                confId,
                reqDTO.getEmail(),
                reqDTO.getName(),
                reqDTO.getRole(),
                reqDTO.getTokenExpireTime()
        );

        String inviteLink = ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/api/portal/committee/invite")
            .queryParam("token", token)
            .toUriString();

        sendInviteEmail(confId, reqDTO, inviteLink);

        return Results.success(CommitteeInviteRespDTO.builder()
                .confId(confId)
                .email(reqDTO.getEmail().trim().toLowerCase())
                .role(reqDTO.getRole().trim().toUpperCase())
            .inviteStatus("INVITED")
            .inviteLink(inviteLink)
                .accessToken(token)
                .tokenExpireTime(reqDTO.getTokenExpireTime() == null
                        ? new Date(System.currentTimeMillis() + 7L * 24L * 60L * 60L * 1000L)
                        : reqDTO.getTokenExpireTime())
                .build());
    }

    @GetMapping("/api/intelli-conf/v1/conference/{confId}/committee/list")
    public Result<List<CommitteeInviteRespDTO>> list(@PathVariable Long confId) {
        conferencePermissionService.requireAtLeastRole(confId, ConferenceRole.VIEWER);

        List<CommitteeInviteRespDTO> result = portalAccessService.listAllByConferenceId(confId)
                .stream()
                .map(this::toResp)
                .collect(Collectors.toList());
        return Results.success(result);
    }

    private CommitteeInviteRespDTO toResp(ConfCommitteeDO item) {
        return CommitteeInviteRespDTO.builder()
                .confId(item.getConfId())
                .email(item.getEmail())
                .name(item.getName())
                .institution(item.getInstitution())
                .role(item.getRole())
                .inviteStatus(item.getInviteStatus())
                .accessToken(item.getAccessToken())
                .tokenExpireTime(item.getTokenExpireTime())
                .build();
    }

    private void sendInviteEmail(Long confId, CommitteeInviteReqDTO reqDTO, String inviteLink) {
        String email = reqDTO.getEmail().trim().toLowerCase();
        String safeName = StringUtils.hasText(reqDTO.getName()) ? reqDTO.getName().trim() : "老师";
        String roleCode = reqDTO.getRole() == null ? "REVIEWER" : reqDTO.getRole().trim().toUpperCase();

        MailTemplateDO template = mailTemplateMapper.selectById(reqDTO.getTemplateId());
        if (template == null) {
            throw new ClientException("邮件模板不存在");
        }
        if (!confId.equals(template.getConferenceId())) {
            throw new ClientException("邮件模板不属于当前会议");
        }
        if (Boolean.FALSE.equals(template.getEnabled())) {
            throw new ClientException("邮件模板已停用");
        }
        if (!StringUtils.hasText(template.getSubjectTemplate())) {
            throw new ClientException("邮件模板主题为空");
        }

        String bodyTemplate = StringUtils.hasText(template.getHtmlTemplate())
                ? template.getHtmlTemplate()
                : template.getTextTemplate();
        if (!StringUtils.hasText(bodyTemplate)) {
            throw new ClientException("邮件模板正文为空");
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("name", safeName);
        variables.put("email", email);
        variables.put("role", roleCode);
        variables.put("inviteLink", inviteLink);
        variables.put("conferenceId", confId);
        ConferenceDO conference = conferenceMapper.selectById(confId);
        if (conference != null) {
            variables.put("conferenceName", conference.getTitle());
            variables.put("conferenceShortName", conference.getShortName());
        }
        if (reqDTO.getTokenExpireTime() != null) {
            variables.put("tokenExpireTime", reqDTO.getTokenExpireTime());
        }

        String subject = templateRenderService.render(template.getSubjectTemplate(), variables);
        String body = templateRenderService.render(bodyTemplate, variables);

        BasicMailSendRespDTO sendResp = basicMailSendService.sendBatch(
            confId,
                null,
                subject,
                body,
                List.of(ContactDTO.builder().name(safeName).email(email).build()),
                false
        );
        if (sendResp.getSuccessCount() == null || sendResp.getSuccessCount() < 1) {
            throw new ClientException("邀请邮件发送失败，请检查邮箱地址后重试");
        }
    }
}
