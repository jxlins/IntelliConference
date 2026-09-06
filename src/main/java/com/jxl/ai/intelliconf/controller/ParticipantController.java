package com.jxl.ai.intelliconf.controller;

import cn.hutool.core.util.StrUtil;
import com.jxl.ai.intelliconf.common.convention.result.Result;
import com.jxl.ai.intelliconf.common.convention.result.Results;
import com.jxl.ai.intelliconf.dto.req.ParticipantSaveReqDTO;
import com.jxl.ai.intelliconf.dto.req.ParticipantUpdateReqDTO;
import com.jxl.ai.intelliconf.dto.resp.ParticipantImportRespDTO;
import com.jxl.ai.intelliconf.dto.resp.ParticipantPageRespDTO;
import com.jxl.ai.intelliconf.service.ParticipantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 参会者控制层
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class ParticipantController {

    private final ParticipantService participantService;

    /**
     * 批量导入参会者
     * 
     * @param file   Excel 文件（必填）
     * @param confId 会议ID（必填）
     * @return 导入结果统计
     */
    @PostMapping("/api/intelli-conf/v1/participant")
    public Result<ParticipantImportRespDTO> importParticipants(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "confId", required = false) Long confId,
            @RequestParam(value = "conferenceId", required = false) Long conferenceId) {
        Long resolvedConfId = confId != null ? confId : conferenceId;
        
        log.info("开始导入参会者: confId={}, role=PROSPECT, fileName={}",
                resolvedConfId, file.getOriginalFilename());
        
        ParticipantImportRespDTO result = participantService.importParticipants(file, resolvedConfId);
        
        log.info("参会者导入完成: confId={}, 总数={}, 成功={}, 跳过={}", 
                resolvedConfId, result.getTotalCount(), result.getSuccessCount(), result.getSkippedCount());
        
        return Results.success(result);
    }

    /**
     * 分页查询会议参会者列表
     * 
     * @param confId  会议ID（必填）
     * @param keyword 搜索关键词（可选）
     * @param current 当前页码（默认1）
     * @param size    每页大小（默认10，最大100）
     * @return 分页结果
     */
    @GetMapping("/api/intelli-conf/v1/participant/list")
    public Result<ParticipantPageRespDTO> getParticipantList(
            @RequestParam("confId") Long confId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "current", defaultValue = "1") Long current,
            @RequestParam(value = "size", defaultValue = "10") Long size) {
        
        log.info("查询参会者列表: confId={}, keyword={}, current={}, size={}", 
                confId, keyword, current, size);
        
        ParticipantPageRespDTO result = participantService.getParticipantList(confId, keyword, current, size);
        
        log.info("查询参会者列表完成: confId={}, 结果数={}, 总数={}", 
                confId, result.getRecords().size(), result.getTotal());
        
        return Results.success(result);
    }

    /**
     * 添加单个参会者
     * 
     * @param reqDTO 参会者信息（必填）
     * @return 添加结果
     */
    @PostMapping("/api/intelli-conf/v1/participant/add")
    public Result<Void> addParticipant(@RequestBody ParticipantSaveReqDTO reqDTO) {
        
        log.info("添加参会者: confId={}, name={}, email={}", 
                reqDTO.getConfId(), reqDTO.getName(), reqDTO.getEmail());
        
        participantService.addParticipant(reqDTO);
        
        log.info("添加参会者成功: confId={}, email={}", reqDTO.getConfId(), reqDTO.getEmail());
        
        return Results.success();
    }

    /**
     * 修改参会者信息
     * 
     * @param reqDTO 参会者信息（必填）
     * @return 修改结果
     */
    @PutMapping("/api/intelli-conf/v1/participant/update")
    public Result<Void> updateParticipant(@RequestBody ParticipantUpdateReqDTO reqDTO) {
        
        log.info("修改参会者: memberId={}", reqDTO.getMemberId());
        
        participantService.updateParticipant(reqDTO);
        
        log.info("修改参会者成功: memberId={}", reqDTO.getMemberId());
        
        return Results.success();
    }

    /**
     * 删除参会者
     * 
     * @param memberId conf_member 表的 ID（必填）
     * @return 删除结果
     */
    @DeleteMapping("/api/intelli-conf/v1/participant/{memberId}")
    public Result<Void> removeParticipant(@PathVariable("memberId") Long memberId) {
        
        log.info("删除参会者: memberId={}", memberId);
        
        participantService.removeParticipant(memberId);
        
        log.info("删除参会者成功: memberId={}", memberId);
        
        return Results.success();
    }
}
