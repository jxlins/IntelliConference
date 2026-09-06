package com.jxl.ai.intelliconf.controller;

import com.jxl.ai.intelliconf.common.convention.result.Result;
import com.jxl.ai.intelliconf.common.convention.result.Results;
import com.jxl.ai.intelliconf.dto.req.MilestoneUpdateReqDTO;
import com.jxl.ai.intelliconf.dto.resp.MilestoneListRespDTO;
import com.jxl.ai.intelliconf.dto.resp.MilestoneRespDTO;
import com.jxl.ai.intelliconf.service.ConfMilestoneService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 会议里程碑时间控制层
 */
@RestController
@RequiredArgsConstructor
public class ConfMilestoneController {

    private final ConfMilestoneService confMilestoneService;

    /**
     * 查询某个会议的全部里程碑列表（含全局确认状态）
     */
    @GetMapping("/api/intelli-conf/v1/milestone/list")
    public Result<MilestoneListRespDTO> getMilestoneList(@RequestParam("confereId") String conferenceShortName) {
        return Results.success(confMilestoneService.getMilestoneListByShortName(conferenceShortName));
    }

    /**
     * 确认并发布时间轴（校验完整性 → 锁定所有节点）
     */
    @PostMapping("/api/intelli-conf/v1/milestone/confirm/{conferenceId}")
    public Result<Void> confirmAndPublish(@PathVariable String conferenceId) {
        confMilestoneService.confirmAndPublishByShortName(conferenceId);
        return Results.success();
    }

    /**
     * 修改单个里程碑（受 is_confirmed 保护：已确认后计划时间不可修改）
     */
    @PutMapping("/api/intelli-conf/v1/milestone/{id}")
    public Result<Void> updateMilestone(@PathVariable Long id, @RequestBody MilestoneUpdateReqDTO requestParam) {
        confMilestoneService.updateMilestone(id, requestParam);
        return Results.success();
    }

    /**
     * 获取会议当前活跃里程碑（处理中 > 最近待开始 > 最后已完成节点）
     */
    @GetMapping("/api/intelli-conf/v1/milestone/active")
    public Result<MilestoneRespDTO> getActiveMilestone(@RequestParam("confereId") Long confereId) {
        return Results.success(confMilestoneService.getActiveMilestone(confereId));
    }
}
