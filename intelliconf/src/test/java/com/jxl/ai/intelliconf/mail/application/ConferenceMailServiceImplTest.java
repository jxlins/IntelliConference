package com.jxl.ai.intelliconf.mail.application;

import com.jxl.ai.intelliconf.common.convention.exception.ClientException;
import com.jxl.ai.intelliconf.dao.entity.ConferenceMailAccountDO;
import com.jxl.ai.intelliconf.dao.entity.MailSendTaskDO;
import com.jxl.ai.intelliconf.dao.entity.MailTemplateDO;
import com.jxl.ai.intelliconf.dao.mapper.ConferenceMailAccountMapper;
import com.jxl.ai.intelliconf.dao.mapper.MailSendLogMapper;
import com.jxl.ai.intelliconf.dao.mapper.MailSendTaskMapper;
import com.jxl.ai.intelliconf.dao.mapper.MailTemplateMapper;
import com.jxl.ai.intelliconf.dao.mapper.SysMailLogMapper;
import com.jxl.ai.intelliconf.dto.req.BindMailAccountReqDTO;
import com.jxl.ai.intelliconf.dto.req.SendTemplateMailReqDTO;
import com.jxl.ai.intelliconf.dto.req.TemplateMailRecipientReqDTO;
import com.jxl.ai.intelliconf.dto.resp.MailAccountRespDTO;
import com.jxl.ai.intelliconf.mail.infrastructure.PasswordEncryptor;
import com.jxl.ai.intelliconf.mail.infrastructure.TemplateRenderService;
import com.jxl.ai.intelliconf.mail.provider.MailAccountConfig;
import com.jxl.ai.intelliconf.mail.provider.MailMessage;
import com.jxl.ai.intelliconf.mail.provider.MailProvider;
import com.jxl.ai.intelliconf.mail.provider.MailProviderFactory;
import com.jxl.ai.intelliconf.mail.provider.MailProviderType;
import com.jxl.ai.intelliconf.mail.provider.MailSendResult;
import com.jxl.ai.intelliconf.service.ConferenceTaskAutomationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConferenceMailServiceImplTest {

    @Test
    void sendTemplateMailCreatesOneLogForEachRecipient() {
        ConferenceMailAccountMapper accountMapper = mock(ConferenceMailAccountMapper.class);
        MailTemplateMapper templateMapper = mock(MailTemplateMapper.class);
        MailSendTaskMapper taskMapper = mock(MailSendTaskMapper.class);
        MailSendLogMapper logMapper = mock(MailSendLogMapper.class);
        SysMailLogMapper sysMailLogMapper = mock(SysMailLogMapper.class);
        MailProvider provider = mock(MailProvider.class);
        when(provider.type()).thenReturn(MailProviderType.SMTP);
        when(provider.checkConnection(any())).thenReturn(com.jxl.ai.intelliconf.mail.provider.MailCheckResult.ok());
        MailProvider tencent = mock(MailProvider.class);
        when(tencent.type()).thenReturn(MailProviderType.TENCENT_EXMAIL);
        when(tencent.checkConnection(any())).thenReturn(com.jxl.ai.intelliconf.mail.provider.MailCheckResult.ok());
        when(provider.send(any(MailAccountConfig.class), any(MailMessage.class))).thenReturn(MailSendResult.ok());

        ConferenceMailServiceImpl service = new ConferenceMailServiceImpl(
                accountMapper,
                templateMapper,
                taskMapper,
                logMapper,
                sysMailLogMapper,
                new PasswordEncryptor(),
                new TemplateRenderService(),
                new MailProviderFactory(List.of(provider, tencent)),
                mock(ConferenceTaskAutomationService.class)
        );

        when(accountMapper.selectOne(any())).thenReturn(account());
        when(templateMapper.selectOne(any())).thenReturn(template());
        when(taskMapper.insert(any())).thenAnswer(invocation -> {
            MailSendTaskDO task = invocation.getArgument(0);
            task.setId(99L);
            return 1;
        });

        SendTemplateMailReqDTO req = new SendTemplateMailReqDTO();
        req.setSceneCode("GENERAL_ANNOUNCEMENT");
        req.setVariables(Map.of("conferenceName", "CSE 2026"));
        req.setRecipients(List.of(recipient("a@example.com"), recipient("b@example.com")));

        service.sendTemplateMail(1L, req);

        verify(logMapper, times(2)).insert(any());
        verify(provider, times(2)).send(any(MailAccountConfig.class), any(MailMessage.class));
    }

    @Test
    void bindTencentExmailIgnoresFrontendHostAndPort() {
        ConferenceMailAccountMapper accountMapper = mock(ConferenceMailAccountMapper.class);
        ConferenceMailServiceImpl service = serviceForBind(accountMapper);
        when(accountMapper.selectOne(any())).thenReturn(null);
        ArgumentCaptor<ConferenceMailAccountDO> captor = ArgumentCaptor.forClass(ConferenceMailAccountDO.class);

        BindMailAccountReqDTO req = new BindMailAccountReqDTO();
        req.setProviderType("TENCENT_EXMAIL");
        req.setFromEmail("conf@example.com");
        req.setPassword("secret");
        req.setSmtpHost("evil.example.com");
        req.setSmtpPort(25);

        service.bindMailAccount(1L, req);

        verify(accountMapper).insert(captor.capture());
        ConferenceMailAccountDO saved = captor.getValue();
        assertEquals("smtp.exmail.qq.com", saved.getSmtpHost());
        assertEquals(465, saved.getSmtpPort());
        assertEquals("conf@example.com", saved.getUsername());
    }

    @Test
    void mailAccountResponseDoesNotExposePasswordFields() {
        ConferenceMailAccountMapper accountMapper = mock(ConferenceMailAccountMapper.class);
        ConferenceMailServiceImpl service = serviceForBind(accountMapper);
        when(accountMapper.selectOne(any())).thenReturn(null);

        BindMailAccountReqDTO req = new BindMailAccountReqDTO();
        req.setProviderType("SMTP");
        req.setFromEmail("conf@example.com");
        req.setUsername("conf@example.com");
        req.setPassword("secret");
        req.setSmtpHost("smtp.example.com");
        req.setSmtpPort(465);

        MailAccountRespDTO resp = service.bindMailAccount(1L, req);

        for (Method method : resp.getClass().getMethods()) {
            assertFalse(method.getName().toLowerCase().contains("password"));
            assertFalse(method.getName().toLowerCase().contains("cipher"));
        }
    }

    @Test
    void invalidProviderTypeThrowsClearError() {
        ConferenceMailServiceImpl service = serviceForBind(mock(ConferenceMailAccountMapper.class));
        BindMailAccountReqDTO req = new BindMailAccountReqDTO();
        req.setProviderType("NOPE");

        assertThrows(ClientException.class, () -> service.bindMailAccount(1L, req));
    }

    @Test
    void bindStripsWhitespaceFromAppPassword() {
        ConferenceMailAccountMapper accountMapper = mock(ConferenceMailAccountMapper.class);
        ConferenceMailServiceImpl service = serviceForBind(accountMapper);
        when(accountMapper.selectOne(any())).thenReturn(null);
        ArgumentCaptor<ConferenceMailAccountDO> captor = ArgumentCaptor.forClass(ConferenceMailAccountDO.class);

        BindMailAccountReqDTO req = new BindMailAccountReqDTO();
        req.setProviderType("SMTP");
        req.setFromEmail("conf@example.com");
        req.setPassword("  abcd efgh ijkl mnop\n");
        req.setSmtpHost("smtp.example.com");
        req.setSmtpPort(465);

        service.bindMailAccount(1L, req);

        verify(accountMapper).insert(captor.capture());
        String decrypted = new PasswordEncryptor().decrypt(captor.getValue().getPasswordCipher());
        assertEquals("abcdefghijklmnop", decrypted);
    }

    private ConferenceMailServiceImpl serviceForBind(ConferenceMailAccountMapper accountMapper) {
        MailProvider provider = mock(MailProvider.class);
        when(provider.type()).thenReturn(MailProviderType.SMTP);
        when(provider.checkConnection(any())).thenReturn(com.jxl.ai.intelliconf.mail.provider.MailCheckResult.ok());
        MailProvider tencent = mock(MailProvider.class);
        when(tencent.type()).thenReturn(MailProviderType.TENCENT_EXMAIL);
        when(tencent.checkConnection(any())).thenReturn(com.jxl.ai.intelliconf.mail.provider.MailCheckResult.ok());
        return new ConferenceMailServiceImpl(
                accountMapper,
                mock(MailTemplateMapper.class),
                mock(MailSendTaskMapper.class),
                mock(MailSendLogMapper.class),
                mock(SysMailLogMapper.class),
                new PasswordEncryptor(),
                new TemplateRenderService(),
                new MailProviderFactory(List.of(provider, tencent)),
                mock(ConferenceTaskAutomationService.class)
        );
    }

    private ConferenceMailAccountDO account() {
        return ConferenceMailAccountDO.builder()
                .conferenceId(1L)
                .providerType("SMTP")
                .fromEmail("conf@example.com")
                .smtpHost("smtp.example.com")
                .smtpPort(465)
                .username("conf@example.com")
                .passwordCipher(new PasswordEncryptor().encrypt("secret"))
                .sslEnabled(true)
                .starttlsEnabled(false)
                .enabled(true)
                .build();
    }

    private MailTemplateDO template() {
        return MailTemplateDO.builder()
                .id(7L)
                .conferenceId(1L)
                .sceneCode("GENERAL_ANNOUNCEMENT")
                .subjectTemplate("Notice {{conferenceName}}")
                .htmlTemplate("<p>Hello {{name}}</p>")
                .textTemplate("Hello {{name}}")
                .enabled(true)
                .build();
    }

    private TemplateMailRecipientReqDTO recipient(String email) {
        TemplateMailRecipientReqDTO recipient = new TemplateMailRecipientReqDTO();
        recipient.setEmail(email);
        recipient.setName(email.substring(0, 1).toUpperCase());
        return recipient;
    }
}
