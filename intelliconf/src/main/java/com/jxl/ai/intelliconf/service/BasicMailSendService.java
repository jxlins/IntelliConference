package com.jxl.ai.intelliconf.service;

import com.jxl.ai.intelliconf.dto.resp.BasicMailSendRespDTO;
import com.jxl.ai.intelliconf.dto.resp.ContactDTO;

import java.util.List;

public interface BasicMailSendService {

    BasicMailSendRespDTO sendBatch(Long confId, Long taskLogId, String subject, String contentBody, List<ContactDTO> recipients, boolean persistMailLogs);
}
