package com.jxl.ai.intelliconf.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jxl.ai.intelliconf.common.convention.result.Result;
import com.jxl.ai.intelliconf.common.convention.result.Results;
import com.jxl.ai.intelliconf.dto.req.ConferenceCreateRepDTO;
import com.jxl.ai.intelliconf.dto.req.ConferenceImportantDatesReqDTO;
import com.jxl.ai.intelliconf.dto.req.ConferencePageRepDTO;
import com.jxl.ai.intelliconf.dto.req.ConferenceStageGenerateReqDTO;
import com.jxl.ai.intelliconf.dto.req.ConferenceUpdateRepDTO;
import com.jxl.ai.intelliconf.dto.resp.ConferencePageRespDTO;
import com.jxl.ai.intelliconf.dto.resp.ConferenceRespDTO;
import com.jxl.ai.intelliconf.dto.resp.ConferenceSetupStatusRespDTO;
import com.jxl.ai.intelliconf.dto.resp.ConferenceStageRespDTO;
import com.jxl.ai.intelliconf.service.ConferenceService;
import com.jxl.ai.intelliconf.service.ConferenceStageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 会议控制层
 */
@RestController
@RequiredArgsConstructor
public class ConferenceController {

    private final ConferenceService conferenceService;

    private final ConferenceStageService conferenceStageService;

    /**
     * 创建会议
     */
    @PostMapping("/api/intelli-conf/v1/conference")
    public Result<Void> createConference(@RequestBody ConferenceCreateRepDTO requestParam) {
        conferenceService.createConference(requestParam);
        return Results.success();
    }

    /**
     * 修改会议信息
     */
    @PutMapping("/api/intelli-conf/v1/conference")
    public Result<Void> updateConference(@RequestBody ConferenceUpdateRepDTO requestParam) {
        conferenceService.updateConference(requestParam);
        return Results.success();
    }

    /**
     * 根据会议缩写查询会议详情
     */
    @GetMapping("/api/intelli-conf/v1/conference/{shortName}")
    public Result<ConferenceRespDTO> getConferenceByShortName(@PathVariable String shortName) {
        return Results.success(conferenceService.getConferenceByShortName(shortName));
    }

    @GetMapping("/api/intelli-conf/v1/conference/{conferenceKey}/setup-status")
    public Result<ConferenceSetupStatusRespDTO> getConferenceSetupStatus(@PathVariable String conferenceKey) {
        return Results.success(conferenceService.getConferenceSetupStatus(conferenceKey));
    }

    @PutMapping("/api/intelli-conf/v1/conference/{conferenceKey}/important-dates")
    public Result<ConferenceRespDTO> updateImportantDates(@PathVariable String conferenceKey,
                                                          @RequestBody ConferenceImportantDatesReqDTO requestParam) {
        return Results.success(conferenceService.updateImportantDates(conferenceKey, requestParam));
    }

    @GetMapping("/api/intelli-conf/v1/conference/{conferenceKey}/stages/preview")
    public Result<List<ConferenceStageRespDTO>> generateStagePreview(@PathVariable String conferenceKey) {
        return Results.success(conferenceStageService.generateStagePreview(conferenceKey));
    }

    @PostMapping("/api/intelli-conf/v1/conference/{conferenceKey}/stages/generate")
    public Result<List<ConferenceStageRespDTO>> confirmGenerateStages(@PathVariable String conferenceKey,
                                                                      @RequestBody List<ConferenceStageGenerateReqDTO> requestParam) {
        return Results.success(conferenceStageService.confirmGenerateStages(conferenceKey, requestParam));
    }

    @GetMapping("/api/intelli-conf/v1/conference/{conferenceKey}/stages")
    public Result<List<ConferenceStageRespDTO>> listStages(@PathVariable String conferenceKey) {
        return Results.success(conferenceStageService.listStages(conferenceKey));
    }

    /**
     * 分页查询会议列表
     */
    @GetMapping("/api/intelli-conf/v1/conference/page")
    public Result<IPage<ConferencePageRespDTO>> pageConference(ConferencePageRepDTO requestParam) {
        return Results.success(conferenceService.pageConference(requestParam));
    }

    /**
        * 删除会议（物理删除，含关联数据）
     */
    @DeleteMapping("/api/intelli-conf/v1/conference/{shortName}")
    public Result<Void> deleteConference(@PathVariable String shortName) {
        conferenceService.deleteConference(shortName);
        return Results.success();
    }
}
