package com.jxl.ai.intelliconf.service.impl;

import com.jxl.ai.intelliconf.dto.resp.BasicMailSendRespDTO;
import com.jxl.ai.intelliconf.dto.resp.ContactDTO;
import com.jxl.ai.intelliconf.mail.application.ConferenceMailService;
import com.jxl.ai.intelliconf.service.BasicMailSendService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BasicMailSendServiceImpl implements BasicMailSendService {

    private final ConferenceMailService conferenceMailService;

    @Override
    public BasicMailSendRespDTO sendBatch(Long confId, Long taskLogId, String subject, String contentBody,
                                          List<ContactDTO> recipients, boolean persistMailLogs) {
        return conferenceMailService.sendDirectBatch(confId, taskLogId, subject, contentBody, recipients, persistMailLogs);
    }
}
