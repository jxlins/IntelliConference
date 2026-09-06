package com.jxl.ai.intelliconf.service.impl;

import com.jxl.ai.intelliconf.common.convention.exception.ClientException;
import com.jxl.ai.intelliconf.dao.entity.ConfMemberInvitationDO;
import com.jxl.ai.intelliconf.dao.entity.ConferenceDO;
import com.jxl.ai.intelliconf.dto.resp.BasicMailSendRespDTO;
import com.jxl.ai.intelliconf.service.BasicMailSendService;
import com.jxl.ai.intelliconf.dao.mapper.MailTemplateMapper;
import com.jxl.ai.intelliconf.mail.infrastructure.TemplateRenderService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmailServiceImplTest {

    @Test
    void failedDeliveryIsReportedInsteadOfSilentlyMarkedSent() {
        BasicMailSendService mailSendService = mock(BasicMailSendService.class);
        when(mailSendService.sendBatch(any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn(BasicMailSendRespDTO.builder()
                        .totalCount(1)
                        .successCount(0)
                        .failCount(1)
                        .failedDetails(List.of("person@example.com: SMTP rejected"))
                        .build());
        EmailServiceImpl service = new EmailServiceImpl(mailSendService, mock(MailTemplateMapper.class), mock(TemplateRenderService.class));

        assertThrows(ClientException.class, () -> service.sendCommitteeInvitationEmail(
                ConferenceDO.builder().id(1L).title("Test Conference").build(),
                ConfMemberInvitationDO.builder()
                        .inviteeName("Test User")
                        .inviteeEmail("person@example.com")
                        .roleName("会议秘书")
                        .committeeName("会议秘书处")
                        .build(),
                "http://localhost:5173/committee-invite?token=test"
        ));
    }
}
