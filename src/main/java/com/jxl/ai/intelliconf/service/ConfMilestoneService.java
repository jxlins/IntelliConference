package com.jxl.ai.intelliconf.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jxl.ai.intelliconf.dao.entity.ConfMilestoneDO;
import com.jxl.ai.intelliconf.dto.req.MilestoneUpdateReqDTO;
import com.jxl.ai.intelliconf.dto.resp.MilestoneListRespDTO;
import com.jxl.ai.intelliconf.dto.resp.MilestoneRespDTO;

import java.util.List;

public interface ConfMilestoneService extends IService<ConfMilestoneDO> {

    /**
     * 根据会议ID查询所有里程碑，按 start_date 升序排序（供内部调用）
     *
     * @param confereId 会议ID
     * @return 里程碑列表
     */
    List<ConfMilestoneDO> listByConferenceId(Long confereId);

    /**
     * 根据 confere_id 和 node_code 获取单个节点信息（供内部调用）
     *
     * @param confereId 会议ID
     * @param nodeCode  节点编码
     * @return 里程碑节点
     */
    ConfMilestoneDO getByConferenceIdAndNodeCode(Long confereId, String nodeCode);

    /**
     * 获取某会议所有里程碑列表（含全局确认状态 allConfirmed）
     *
     * @param confereId 会议ID
     * @return 里程碑列表响应
     */
    MilestoneListRespDTO getMilestoneList(Long confereId);

    /**
     * 根据会议简称获取里程碑列表（含全局确认状态 allConfirmed）
     *
     * @param shortName 会议简称
     * @return 里程碑列表响应
     */
    MilestoneListRespDTO getMilestoneListByShortName(String shortName);

    /**
     * 确认并发布时间轴：校验所有节点时间完整性，统一标记 is_confirmed=1。
     * 整个过程在同一事务中完成。
     *
     * @param conferenceId 会议ID
     */
    void confirmAndPublish(Long conferenceId);

    /**
     * 根据会议简称确认并发布时间轴
     *
     * @param shortName 会议简称
     */
    void confirmAndPublishByShortName(String shortName);

    /**
     * 受限更新里程碑：若 is_confirmed=1 且请求包含计划时间字段则抛出异常；
     * 确认后仍允许更新执行数据（actual_end_date, status, remark）。
     *
     * @param id           里程碑ID（从路径参数传入）
     * @param requestParam 更新请求体
     */
    void updateMilestone(Long id, MilestoneUpdateReqDTO requestParam);

    /**
     * 获取会议当前活跃里程碑节点，按以下优先级查找：
     * 1. status = 1（处理中）的节点；
     * 2. 若无，取 status = 0（等待中）且 start_date 最早的节点；
     * 3. 若全部已达成/跳过，返回 target_end_date 最晚的最后一个节点。
     *
     * @param confId 会议ID
     * @return 当前活跃里程碑
     */
    MilestoneRespDTO getActiveMilestone(Long confId);
}
