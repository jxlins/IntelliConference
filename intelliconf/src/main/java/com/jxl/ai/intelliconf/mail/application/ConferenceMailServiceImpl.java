package com.jxl.ai.intelliconf.mail.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jxl.ai.intelliconf.common.biz.user.UserContext;
import com.jxl.ai.intelliconf.common.convention.exception.ClientException;
import com.jxl.ai.intelliconf.dao.entity.ConferenceMailAccountDO;
import com.jxl.ai.intelliconf.dao.entity.MailSendLogDO;
import com.jxl.ai.intelliconf.dao.entity.MailSendTaskDO;
import com.jxl.ai.intelliconf.dao.entity.MailTemplateDO;
import com.jxl.ai.intelliconf.dao.entity.SysMailLogDO;
import com.jxl.ai.intelliconf.dao.mapper.ConferenceMailAccountMapper;
import com.jxl.ai.intelliconf.dao.mapper.MailSendLogMapper;
import com.jxl.ai.intelliconf.dao.mapper.MailSendTaskMapper;
import com.jxl.ai.intelliconf.dao.mapper.MailTemplateMapper;
import com.jxl.ai.intelliconf.dao.mapper.SysMailLogMapper;
import com.jxl.ai.intelliconf.dto.req.BindMailAccountReqDTO;
import com.jxl.ai.intelliconf.dto.req.MailTemplateSaveReqDTO;
import com.jxl.ai.intelliconf.dto.req.SendTemplateMailReqDTO;
import com.jxl.ai.intelliconf.dto.req.TemplateMailRecipientReqDTO;
import com.jxl.ai.intelliconf.dto.resp.BasicMailSendRespDTO;
import com.jxl.ai.intelliconf.dto.resp.ContactDTO;
import com.jxl.ai.intelliconf.dto.resp.MailAccountRespDTO;
import com.jxl.ai.intelliconf.dto.resp.MailCheckRespDTO;
import com.jxl.ai.intelliconf.dto.resp.MailSendLogRespDTO;
import com.jxl.ai.intelliconf.dto.resp.MailSendTaskRespDTO;
import com.jxl.ai.intelliconf.dto.resp.MailTemplateRespDTO;
import com.jxl.ai.intelliconf.mail.infrastructure.PasswordEncryptor;
import com.jxl.ai.intelliconf.mail.infrastructure.TemplateRenderService;
import com.jxl.ai.intelliconf.mail.provider.MailAccountConfig;
import com.jxl.ai.intelliconf.mail.provider.MailCheckResult;
import com.jxl.ai.intelliconf.mail.provider.MailMessage;
import com.jxl.ai.intelliconf.mail.provider.MailProvider;
import com.jxl.ai.intelliconf.mail.provider.MailProviderFactory;
import com.jxl.ai.intelliconf.mail.provider.MailProviderType;
import com.jxl.ai.intelliconf.mail.provider.MailSendResult;
import com.jxl.ai.intelliconf.mail.provider.TencentExmailProvider;
import com.jxl.ai.intelliconf.service.ConferenceTaskAutomationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ConferenceMailServiceImpl implements ConferenceMailService {

    private static final int MAX_RECIPIENTS = 500;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final ConferenceMailAccountMapper accountMapper;
    private final MailTemplateMapper templateMapper;
    private final MailSendTaskMapper taskMapper;
    private final MailSendLogMapper logMapper;
    private final SysMailLogMapper sysMailLogMapper;
    private final PasswordEncryptor passwordEncryptor;
    private final TemplateRenderService templateRenderService;
    private final MailProviderFactory providerFactory;
    private final ConferenceTaskAutomationService taskAutomationService;

    @Override
    @Transactional
    public MailAccountRespDTO bindMailAccount(Long conferenceId, BindMailAccountReqDTO command) {
        if (conferenceId == null || command == null) {
            throw new ClientException("会议ID和邮箱配置不能为空");
        }
        MailProviderType providerType = parseProviderType(command.getProviderType());
        String fromEmail = requiredEmail(command.getFromEmail(), "发件邮箱不能为空");
        if (!StringUtils.hasText(command.getPassword())) throw new ClientException("请填写SMTP授权码或应用专用密码");
        // 授权码/应用专用密码从服务商页面复制时常带有空格或换行（如 Gmail 分组显示 xxxx xxxx xxxx xxxx），
        // 各主流服务商的授权码本身均不含空白字符，统一剔除，避免 SMTP 认证失败。
        String password = command.getPassword().replaceAll("\\s+", "");
        if (!StringUtils.hasText(password)) throw new ClientException("请填写SMTP授权码或应用专用密码");

        ConferenceMailAccountDO existing = selectAccount(conferenceId, false);
        ConferenceMailAccountDO account = existing == null ? new ConferenceMailAccountDO() : existing;
        account.setConferenceId(conferenceId);
        account.setProviderType(providerType.name());
        account.setFromEmail(fromEmail);
        account.setFromName(trimToNull(command.getFromName()));
        account.setReplyTo(trimToNull(command.getReplyTo()));
        account.setPasswordCipher(passwordEncryptor.encrypt(password));
        account.setEnabled(true);
        account.setLastTestStatus("NOT_TESTED");
        account.setLastTestTime(null);
        account.setUpdatedAt(new Date());
        if (account.getCreatedAt() == null) {
            account.setCreatedAt(new Date());
        }

        if (providerType == MailProviderType.TENCENT_EXMAIL) {
            account.setSmtpHost(TencentExmailProvider.HOST);
            account.setSmtpPort(TencentExmailProvider.PORT);
            account.setSslEnabled(true);
            account.setStarttlsEnabled(false);
            account.setUsername(fromEmail);
        } else {
            account.setSmtpHost(required(command.getSmtpHost(), "SMTP服务器地址不能为空"));
            account.setSmtpPort(command.getSmtpPort() == null ? 25 : command.getSmtpPort());
            account.setSslEnabled(Boolean.TRUE.equals(command.getSslEnabled()));
            account.setStarttlsEnabled(Boolean.TRUE.equals(command.getStarttlsEnabled()));
            account.setUsername(StringUtils.hasText(command.getUsername()) ? command.getUsername().trim() : fromEmail);
        }

        MailAccountConfig candidateConfig = toConfig(account);
        MailCheckResult check = providerFactory.getProvider(providerType).checkConnection(candidateConfig);
        if (check == null || !check.isSuccess()) {
            throw new ClientException("新邮箱配置未保存：" + (check == null ? "邮箱连接测试无结果" : check.getErrorMessage()));
        }
        account.setLastTestStatus("SUCCESS");
        account.setLastTestTime(new Date());
        if (existing == null) {
            accountMapper.insert(account);
        } else {
            accountMapper.updateById(account);
        }
        taskAutomationService.markMailAccountConfigured(conferenceId, UserContext.getUsername());
        return toAccountResp(account);
    }

    @Override
    public MailAccountRespDTO getMailAccount(Long conferenceId) {
        if (conferenceId == null) {
            throw new ClientException("会议ID不能为空");
        }
        ConferenceMailAccountDO account = selectAccount(conferenceId, false);
        return account == null ? null : toAccountResp(account);
    }

    @Override
    @Transactional
    public MailCheckRespDTO testMailAccount(Long conferenceId) {
        ConferenceMailAccountDO account = requireEnabledAccount(conferenceId);
        MailAccountConfig config = toConfig(account);
        MailCheckResult result = providerFactory.getProvider(config.getProviderType()).checkConnection(config);
        account.setLastTestStatus(result.isSuccess() ? "SUCCESS" : result.getErrorCode().name());
        account.setLastTestTime(new Date());
        account.setUpdatedAt(new Date());
        accountMapper.updateById(account);
        return MailCheckRespDTO.builder()
                .success(result.isSuccess())
                .errorCode(result.getErrorCode() == null ? null : result.getErrorCode().name())
                .errorMessage(result.getErrorMessage())
                .build();
    }

    @Override
    @Transactional
    public MailTemplateRespDTO saveTemplate(Long conferenceId, Long templateId, MailTemplateSaveReqDTO command) {
        if (conferenceId == null || command == null) {
            throw new ClientException("会议ID和模板内容不能为空");
        }
        if (!StringUtils.hasText(command.getSceneCode())) {
            throw new ClientException("邮件场景不能为空");
        }
        if (!StringUtils.hasText(command.getSubjectTemplate())) {
            throw new ClientException("邮件主题模板不能为空");
        }
        MailTemplateDO template = templateId == null ? null : templateMapper.selectById(templateId);
        if (template == null) {
            template = new MailTemplateDO();
            template.setConferenceId(conferenceId);
            template.setCreatedAt(new Date());
        } else if (!conferenceId.equals(template.getConferenceId())) {
            throw new ClientException("模板不属于当前会议");
        }
        template.setSceneCode(command.getSceneCode().trim());
        template.setTemplateName(StringUtils.hasText(command.getTemplateName()) ? command.getTemplateName().trim() : command.getSceneCode().trim());
        template.setSubjectTemplate(command.getSubjectTemplate());
        template.setHtmlTemplate(command.getHtmlTemplate());
        template.setTextTemplate(command.getTextTemplate());
        template.setEnabled(command.getEnabled() == null || command.getEnabled());
        template.setUpdatedAt(new Date());
        if (template.getId() == null) {
            templateMapper.insert(template);
        } else {
            templateMapper.updateById(template);
        }
        return toTemplateResp(template);
    }

    @Override
    public List<MailTemplateRespDTO> listTemplates(Long conferenceId) {
        if (conferenceId == null) {
            throw new ClientException("会议ID不能为空");
        }
        List<MailTemplateDO> templates = templateMapper.selectList(
                Wrappers.lambdaQuery(MailTemplateDO.class)
                        .eq(MailTemplateDO::getConferenceId, conferenceId)
                        .orderByDesc(MailTemplateDO::getUpdatedAt)
                        .orderByDesc(MailTemplateDO::getCreatedAt)
        );
        return templates.stream().map(this::toTemplateResp).toList();
    }

    @Override
    @Transactional
    public void deleteTemplate(Long conferenceId, Long templateId) {
        if (conferenceId == null || templateId == null) {
            throw new ClientException("会议ID和模板ID不能为空");
        }
        MailTemplateDO template = templateMapper.selectById(templateId);
        if (template == null) {
            throw new ClientException("模板不存在");
        }
        if (!conferenceId.equals(template.getConferenceId())) {
            throw new ClientException("模板不属于当前会议");
        }
        templateMapper.deleteById(templateId);
    }

    @Override
    @Transactional
    public MailSendTaskRespDTO sendTemplateMail(Long conferenceId, SendTemplateMailReqDTO command) {
        if (command == null || !StringUtils.hasText(command.getSceneCode())) {
            throw new ClientException("邮件场景不能为空");
        }
        MailTemplateDO template = templateMapper.selectOne(Wrappers.lambdaQuery(MailTemplateDO.class)
                .eq(MailTemplateDO::getConferenceId, conferenceId)
                .eq(MailTemplateDO::getSceneCode, command.getSceneCode().trim())
                .eq(MailTemplateDO::getEnabled, true)
                .last("limit 1"));
        if (template == null) {
            throw new ClientException("未找到启用的邮件模板");
        }
        return sendWithTemplate(conferenceId, template, command.getRecipients(), command.getVariables(), command.getCreatedBy());
    }

    @Override
    public List<MailSendLogRespDTO> listSendLogs(Long conferenceId) {
        List<MailSendLogDO> logs = logMapper.selectList(Wrappers.lambdaQuery(MailSendLogDO.class)
                .eq(MailSendLogDO::getConferenceId, conferenceId)
                .orderByDesc(MailSendLogDO::getCreatedAt));
        return logs.stream().map(this::toLogResp).toList();
    }

    @Override
    @Transactional
    public BasicMailSendRespDTO sendDirectBatch(Long conferenceId, Long legacyTaskLogId, String subject, String htmlBody,
                                                List<ContactDTO> recipients, boolean persistLegacyMailLogs) {
        MailTemplateDO syntheticTemplate = MailTemplateDO.builder()
                .conferenceId(conferenceId)
                .templateName("Direct Mail")
                .subjectTemplate(convertLegacyPlaceholders(subject))
                .htmlTemplate(convertLegacyPlaceholders(htmlBody))
                .textTemplate(htmlBody == null ? "" : convertLegacyPlaceholders(htmlBody.replaceAll("<[^>]+>", "")))
                .build();

        SendResultCounter counter = sendBatchInternal(
                conferenceId,
                syntheticTemplate,
                recipients == null ? List.of() : recipients.stream().map(this::toRecipient).toList(),
                Map.of(),
                UserContext.getUsername(),
                legacyTaskLogId,
                persistLegacyMailLogs
        );
        return BasicMailSendRespDTO.builder()
                .totalCount(counter.totalCount)
                .successCount(counter.successCount)
                .failCount(counter.failCount)
                .successEmails(limit(counter.successEmails))
                .failedDetails(limit(counter.failedDetails))
                .build();
    }

    private MailSendTaskRespDTO sendWithTemplate(Long conferenceId, MailTemplateDO template,
                                                 List<TemplateMailRecipientReqDTO> recipients,
                                                 Map<String, Object> variables,
                                                 String createdBy) {
        SendResultCounter counter = sendBatchInternal(conferenceId, template, recipients, variables, createdBy, null, false);
        return MailSendTaskRespDTO.builder()
                .taskId(counter.taskId)
                .status(counter.status)
                .totalCount(counter.totalCount)
                .successCount(counter.successCount)
                .failCount(counter.failCount)
                .build();
    }

    private SendResultCounter sendBatchInternal(Long conferenceId, MailTemplateDO template,
                                                List<TemplateMailRecipientReqDTO> recipients,
                                                Map<String, Object> variables,
                                                String createdBy,
                                                Long legacyTaskLogId,
                                                boolean persistLegacyMailLogs) {
        ConferenceMailAccountDO account = requireEnabledAccount(conferenceId);
        validateRecipients(recipients);
        MailAccountConfig config = toConfig(account);
        MailProvider provider = providerFactory.getProvider(config.getProviderType());

        String firstSubject = templateRenderService.render(template.getSubjectTemplate(), mergeVariables(variables, recipients.get(0)));
        MailSendTaskDO task = MailSendTaskDO.builder()
                .conferenceId(conferenceId)
                .templateId(template.getId())
                .subject(firstSubject)
                .status("SENDING")
                .totalCount(recipients.size())
                .successCount(0)
                .failCount(0)
                .createdBy(StringUtils.hasText(createdBy) ? createdBy : UserContext.getUsername())
                .createdAt(new Date())
                .updatedAt(new Date())
                .build();
        taskMapper.insert(task);

        SendResultCounter counter = new SendResultCounter();
        counter.taskId = task.getId();
        counter.totalCount = recipients.size();

        for (TemplateMailRecipientReqDTO recipient : recipients) {
            Map<String, Object> renderedVariables = mergeVariables(variables, recipient);
            String subject = templateRenderService.render(template.getSubjectTemplate(), renderedVariables);
            String htmlBody = templateRenderService.render(template.getHtmlTemplate(), renderedVariables);
            String textBody = templateRenderService.render(template.getTextTemplate(), renderedVariables);
            MailMessage message = MailMessage.builder()
                    .to(List.of(recipient.getEmail().trim()))
                    .subject(subject)
                    .htmlBody(htmlBody)
                    .textBody(textBody)
                    .build();
            MailSendResult result = provider.send(config, message);
            account.setLastTestStatus(result.isSuccess() ? "SUCCESS"
                    : (result.getErrorCode() == null ? "SEND_FAILED" : result.getErrorCode().name()));
            account.setLastTestTime(new Date());
            account.setUpdatedAt(new Date());
            accountMapper.updateById(account);
            recordSendLog(task.getId(), conferenceId, recipient.getEmail(), subject, config.getProviderType(), result);
            if (persistLegacyMailLogs && legacyTaskLogId != null) {
                recordLegacyLog(legacyTaskLogId, recipient.getEmail(), result);
            }
            if (result.isSuccess()) {
                counter.successCount++;
                counter.successEmails.add(recipient.getEmail());
            } else {
                counter.failCount++;
                counter.failedDetails.add(recipient.getEmail() + ": " + result.getErrorMessage());
            }
        }

        counter.status = resolveTaskStatus(counter.successCount, counter.failCount);
        task.setSuccessCount(counter.successCount);
        task.setFailCount(counter.failCount);
        task.setStatus(counter.status);
        task.setUpdatedAt(new Date());
        taskMapper.updateById(task);
        return counter;
    }

    private void recordSendLog(Long taskId, Long conferenceId, String recipientEmail, String subject,
                               MailProviderType providerType, MailSendResult result) {
        logMapper.insert(MailSendLogDO.builder()
                .taskId(taskId)
                .conferenceId(conferenceId)
                .recipientEmail(recipientEmail)
                .subject(subject)
                .status(result.isSuccess() ? "SUCCESS" : "FAILED")
                .errorCode(result.getErrorCode() == null ? null : result.getErrorCode().name())
                .errorMessage(result.getErrorMessage())
                .providerType(providerType.name())
                .sentAt(new Date())
                .createdAt(new Date())
                .build());
    }

    private void recordLegacyLog(Long legacyTaskLogId, String recipientEmail, MailSendResult result) {
        sysMailLogMapper.insert(SysMailLogDO.builder()
                .taskLogId(legacyTaskLogId)
                .recipientEmail(recipientEmail)
                .sendStatus(result.isSuccess() ? 1 : 0)
                .errorMsg(result.getErrorMessage())
                .build());
    }

    private ConferenceMailAccountDO requireEnabledAccount(Long conferenceId) {
        ConferenceMailAccountDO account = selectAccount(conferenceId, true);
        if (account == null) {
            throw new ClientException("当前会议尚未绑定官方邮箱");
        }
        return account;
    }

    private ConferenceMailAccountDO selectAccount(Long conferenceId, boolean enabledOnly) {
        var query = Wrappers.lambdaQuery(ConferenceMailAccountDO.class)
                .eq(ConferenceMailAccountDO::getConferenceId, conferenceId);
        if (enabledOnly) {
            query.eq(ConferenceMailAccountDO::getEnabled, true);
        }
        return accountMapper.selectOne(query.last("limit 1"));
    }

    private MailAccountConfig toConfig(ConferenceMailAccountDO account) {
        return MailAccountConfig.builder()
                .conferenceId(account.getConferenceId())
                .providerType(parseProviderType(account.getProviderType()))
                .fromEmail(account.getFromEmail())
                .fromName(account.getFromName())
                .replyTo(account.getReplyTo())
                .smtpHost(account.getSmtpHost())
                .smtpPort(account.getSmtpPort())
                .username(account.getUsername())
                .password(passwordEncryptor.decrypt(account.getPasswordCipher()))
                .sslEnabled(account.getSslEnabled())
                .starttlsEnabled(account.getStarttlsEnabled())
                .build();
    }

    private void validateRecipients(List<TemplateMailRecipientReqDTO> recipients) {
        if (CollectionUtils.isEmpty(recipients)) {
            throw new ClientException("收件人不能为空");
        }
        if (recipients.size() > MAX_RECIPIENTS) {
            throw new ClientException("单次最多发送 " + MAX_RECIPIENTS + " 封邮件");
        }
        for (TemplateMailRecipientReqDTO recipient : recipients) {
            requiredEmail(recipient == null ? null : recipient.getEmail(), "收件人邮箱格式不正确");
        }
    }

    private Map<String, Object> mergeVariables(Map<String, Object> globalVariables, TemplateMailRecipientReqDTO recipient) {
        Map<String, Object> merged = new HashMap<>();
        if (globalVariables != null) {
            merged.putAll(globalVariables);
        }
        if (recipient != null && recipient.getVariables() != null) {
            merged.putAll(recipient.getVariables());
        }
        if (recipient != null) {
            merged.putIfAbsent("email", recipient.getEmail());
            merged.putIfAbsent("name", StringUtils.hasText(recipient.getName()) ? recipient.getName() : recipient.getEmail());
        }
        return merged;
    }

    private TemplateMailRecipientReqDTO toRecipient(ContactDTO contact) {
        TemplateMailRecipientReqDTO recipient = new TemplateMailRecipientReqDTO();
        recipient.setEmail(contact.getEmail());
        recipient.setName(contact.getName());
        return recipient;
    }

    private String resolveTaskStatus(int successCount, int failCount) {
        if (successCount > 0 && failCount == 0) {
            return "SUCCESS";
        }
        if (successCount > 0) {
            return "PARTIAL_FAILED";
        }
        return "FAILED";
    }

    private MailProviderType parseProviderType(String providerType) {
        if (!StringUtils.hasText(providerType)) {
            throw new ClientException("邮件服务商类型不能为空");
        }
        try {
            return MailProviderType.valueOf(providerType.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ClientException("不支持的邮件服务商类型: " + providerType);
        }
    }

    private String requiredEmail(String value, String message) {
        String email = required(value, message);
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new ClientException(message);
        }
        return email;
    }

    private String required(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new ClientException(message);
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private List<String> limit(List<String> values) {
        return values.size() > 10 ? values.subList(0, 10) : values;
    }

    private String convertLegacyPlaceholders(String template) {
        if (template == null) {
            return null;
        }
        return template.replace("${name}", "{{name}}").replace("${email}", "{{email}}");
    }

    private MailAccountRespDTO toAccountResp(ConferenceMailAccountDO item) {
        return MailAccountRespDTO.builder()
                .id(item.getId())
                .conferenceId(item.getConferenceId())
                .providerType(item.getProviderType())
                .fromEmail(item.getFromEmail())
                .fromName(item.getFromName())
                .replyTo(item.getReplyTo())
                .smtpHost(item.getSmtpHost())
                .smtpPort(item.getSmtpPort())
                .sslEnabled(item.getSslEnabled())
                .starttlsEnabled(item.getStarttlsEnabled())
                .enabled(item.getEnabled())
                .lastTestStatus(item.getLastTestStatus())
                .lastTestTime(item.getLastTestTime())
                .build();
    }

    private MailTemplateRespDTO toTemplateResp(MailTemplateDO item) {
        return MailTemplateRespDTO.builder()
                .id(item.getId())
                .conferenceId(item.getConferenceId())
                .sceneCode(item.getSceneCode())
                .templateName(item.getTemplateName())
                .subjectTemplate(item.getSubjectTemplate())
                .htmlTemplate(item.getHtmlTemplate())
                .textTemplate(item.getTextTemplate())
                .enabled(item.getEnabled())
                .build();
    }

    private MailSendLogRespDTO toLogResp(MailSendLogDO item) {
        return MailSendLogRespDTO.builder()
                .recipientEmail(item.getRecipientEmail())
                .subject(item.getSubject())
                .status(item.getStatus())
                .errorCode(item.getErrorCode())
                .errorMessage(item.getErrorMessage())
                .providerType(item.getProviderType())
                .sentAt(item.getSentAt())
                .build();
    }

    private static class SendResultCounter {
        Long taskId;
        String status;
        int totalCount;
        int successCount;
        int failCount;
        List<String> successEmails = new ArrayList<>();
        List<String> failedDetails = new ArrayList<>();
    }
}
