package com.jxl.ai.intelliconf.service;

import com.jxl.ai.intelliconf.dto.req.ConferenceStageGenerateReqDTO;
import com.jxl.ai.intelliconf.dto.resp.ConferenceStageRespDTO;

import java.util.List;

public interface ConferenceStageService {

    List<ConferenceStageRespDTO> generateStagePreview(String conferenceKey);

    List<ConferenceStageRespDTO> confirmGenerateStages(String conferenceKey, List<ConferenceStageGenerateReqDTO> requestParam);

    List<ConferenceStageRespDTO> listStages(String conferenceKey);
}
