package com.jxl.ai.intelliconf.controller;

import com.jxl.ai.intelliconf.common.convention.result.Result;
import com.jxl.ai.intelliconf.common.convention.result.Results;
import com.jxl.ai.intelliconf.dto.req.BindMailAccountReqDTO;
import com.jxl.ai.intelliconf.dto.req.MailTemplateSaveReqDTO;
import com.jxl.ai.intelliconf.dto.req.SendTemplateMailReqDTO;
import com.jxl.ai.intelliconf.dto.resp.MailAccountRespDTO;
import com.jxl.ai.intelliconf.dto.resp.MailCheckRespDTO;
import com.jxl.ai.intelliconf.dto.resp.MailSendLogRespDTO;
import com.jxl.ai.intelliconf.dto.resp.MailSendTaskRespDTO;
import com.jxl.ai.intelliconf.dto.resp.MailTemplateRespDTO;
import com.jxl.ai.intelliconf.enums.ConferenceRole;
import com.jxl.ai.intelliconf.mail.application.ConferenceMailService;
import com.jxl.ai.intelliconf.service.ConferencePermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ConferenceMailController {

    private final ConferenceMailService conferenceMailService;
    private final ConferencePermissionService conferencePermissionService;

    @PostMapping("/api/conferences/{conferenceId}/mail/account")
    public Result<MailAccountRespDTO> bindMailAccount(@PathVariable Long conferenceId,
                                                       @RequestBody BindMailAccountReqDTO reqDTO) {
        conferencePermissionService.requireAtLeastRole(conferenceId, ConferenceRole.COMMITTEE);
        return Results.success(conferenceMailService.bindMailAccount(conferenceId, reqDTO));
    }

    @GetMapping("/api/conferences/{conferenceId}/mail/account")
    public Result<MailAccountRespDTO> getMailAccount(@PathVariable Long conferenceId) {
        conferencePermissionService.requireAtLeastRole(conferenceId, ConferenceRole.VIEWER);
        return Results.success(conferenceMailService.getMailAccount(conferenceId));
    }

    @PostMapping("/api/conferences/{conferenceId}/mail/account/test")
    public Result<MailCheckRespDTO> testMailAccount(@PathVariable Long conferenceId) {
        conferencePermissionService.requireAtLeastRole(conferenceId, ConferenceRole.COMMITTEE);
        return Results.success(conferenceMailService.testMailAccount(conferenceId));
    }

    @PostMapping("/api/conferences/{conferenceId}/mail/templates")
    public Result<MailTemplateRespDTO> createTemplate(@PathVariable Long conferenceId,
                                                       @RequestBody MailTemplateSaveReqDTO reqDTO) {
        conferencePermissionService.requireAtLeastRole(conferenceId, ConferenceRole.OPERATOR);
        return Results.success(conferenceMailService.saveTemplate(conferenceId, null, reqDTO));
    }

    @PutMapping("/api/conferences/{conferenceId}/mail/templates/{templateId}")
    public Result<MailTemplateRespDTO> updateTemplate(@PathVariable Long conferenceId,
                                                       @PathVariable Long templateId,
                                                       @RequestBody MailTemplateSaveReqDTO reqDTO) {
        conferencePermissionService.requireAtLeastRole(conferenceId, ConferenceRole.OPERATOR);
        return Results.success(conferenceMailService.saveTemplate(conferenceId, templateId, reqDTO));
    }

    @GetMapping("/api/conferences/{conferenceId}/mail/templates")
    public Result<List<MailTemplateRespDTO>> listTemplates(@PathVariable Long conferenceId) {
        conferencePermissionService.requireAtLeastRole(conferenceId, ConferenceRole.OPERATOR);
        return Results.success(conferenceMailService.listTemplates(conferenceId));
    }

    @DeleteMapping("/api/conferences/{conferenceId}/mail/templates/{templateId}")
    public Result<Void> deleteTemplate(@PathVariable Long conferenceId, @PathVariable Long templateId) {
        conferencePermissionService.requireAtLeastRole(conferenceId, ConferenceRole.OPERATOR);
        conferenceMailService.deleteTemplate(conferenceId, templateId);
        return Results.success();
    }

    @PostMapping("/api/conferences/{conferenceId}/mail/send-template")
    public Result<MailSendTaskRespDTO> sendTemplateMail(@PathVariable Long conferenceId,
                                                         @RequestBody SendTemplateMailReqDTO reqDTO) {
        conferencePermissionService.requireAtLeastRole(conferenceId, ConferenceRole.OPERATOR);
        return Results.success(conferenceMailService.sendTemplateMail(conferenceId, reqDTO));
    }

    @GetMapping("/api/conferences/{conferenceId}/mail/logs")
    public Result<List<MailSendLogRespDTO>> listSendLogs(@PathVariable Long conferenceId) {
        conferencePermissionService.requireAtLeastRole(conferenceId, ConferenceRole.VIEWER);
        return Results.success(conferenceMailService.listSendLogs(conferenceId));
    }
}
