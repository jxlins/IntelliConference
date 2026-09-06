package com.jxl.ai.intelliconf.mail.application;

import com.jxl.ai.intelliconf.dto.req.BindMailAccountReqDTO;
import com.jxl.ai.intelliconf.dto.req.MailTemplateSaveReqDTO;
import com.jxl.ai.intelliconf.dto.req.SendTemplateMailReqDTO;
import com.jxl.ai.intelliconf.dto.resp.BasicMailSendRespDTO;
import com.jxl.ai.intelliconf.dto.resp.ContactDTO;
import com.jxl.ai.intelliconf.dto.resp.MailAccountRespDTO;
import com.jxl.ai.intelliconf.dto.resp.MailCheckRespDTO;
import com.jxl.ai.intelliconf.dto.resp.MailSendLogRespDTO;
import com.jxl.ai.intelliconf.dto.resp.MailSendTaskRespDTO;
import com.jxl.ai.intelliconf.dto.resp.MailTemplateRespDTO;

import java.util.List;

public interface ConferenceMailService {

    MailAccountRespDTO bindMailAccount(Long conferenceId, BindMailAccountReqDTO command);

    MailAccountRespDTO getMailAccount(Long conferenceId);

    MailCheckRespDTO testMailAccount(Long conferenceId);

    MailTemplateRespDTO saveTemplate(Long conferenceId, Long templateId, MailTemplateSaveReqDTO command);

    List<MailTemplateRespDTO> listTemplates(Long conferenceId);

    void deleteTemplate(Long conferenceId, Long templateId);

    MailSendTaskRespDTO sendTemplateMail(Long conferenceId, SendTemplateMailReqDTO command);

    List<MailSendLogRespDTO> listSendLogs(Long conferenceId);

    BasicMailSendRespDTO sendDirectBatch(Long conferenceId, Long legacyTaskLogId, String subject, String htmlBody,
                                         List<ContactDTO> recipients, boolean persistLegacyMailLogs);
}
