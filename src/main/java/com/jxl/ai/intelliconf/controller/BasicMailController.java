package com.jxl.ai.intelliconf.controller;

import com.jxl.ai.intelliconf.common.convention.exception.ClientException;
import com.jxl.ai.intelliconf.common.convention.result.Result;
import com.jxl.ai.intelliconf.common.convention.result.Results;
import com.jxl.ai.intelliconf.dto.req.BasicMailRecipientReqDTO;
import com.jxl.ai.intelliconf.dto.req.BasicMailSendReqDTO;
import com.jxl.ai.intelliconf.dto.resp.BasicMailSendRespDTO;
import com.jxl.ai.intelliconf.dto.resp.ContactDTO;
import com.jxl.ai.intelliconf.service.BasicMailSendService;
import com.jxl.ai.intelliconf.enums.ConferenceRole;
import com.jxl.ai.intelliconf.service.ConferencePermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class BasicMailController {

    private final BasicMailSendService basicMailSendService;
    private final ConferencePermissionService conferencePermissionService;

    @PostMapping("/api/mail/basic-send")
    public Result<BasicMailSendRespDTO> sendBasicMail(@RequestBody BasicMailSendReqDTO reqDTO) {
        if (reqDTO == null) {
            throw new ClientException("请求参数不能为空");
        }
        if (!StringUtils.hasText(reqDTO.getSubject())) {
            throw new ClientException("邮件主题不能为空");
        }
        if (!StringUtils.hasText(reqDTO.getContentBody())) {
            throw new ClientException("邮件正文不能为空");
        }
        if (reqDTO.getConfId() == null) {
            throw new ClientException("会议ID不能为空");
        }
        if (CollectionUtils.isEmpty(reqDTO.getRecipients())) {
            throw new ClientException("请选择至少一个发送对象");
        }

        conferencePermissionService.requireAtLeastRole(reqDTO.getConfId(), ConferenceRole.OPERATOR);

        List<ContactDTO> recipients = reqDTO.getRecipients().stream()
                .filter(item -> item != null && StringUtils.hasText(item.getEmail()))
                .map(this::toContact)
                .collect(Collectors.toList());

        if (recipients.isEmpty()) {
            throw new ClientException("收件人邮箱不能为空");
        }

        BasicMailSendRespDTO result = basicMailSendService.sendBatch(
            reqDTO.getConfId(),
                null,
                reqDTO.getSubject().trim(),
                reqDTO.getContentBody().trim(),
                recipients,
                false
        );
        return Results.success(result);
    }

    private ContactDTO toContact(BasicMailRecipientReqDTO req) {
        return ContactDTO.builder()
                .name(req.getName())
                .email(req.getEmail().trim())
                .build();
    }
}
