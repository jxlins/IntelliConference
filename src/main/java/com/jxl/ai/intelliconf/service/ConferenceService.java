package com.jxl.ai.intelliconf.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.jxl.ai.intelliconf.dao.entity.ConferenceDO;
import com.jxl.ai.intelliconf.dto.req.ConferenceCreateRepDTO;
import com.jxl.ai.intelliconf.dto.req.ConferenceImportantDatesReqDTO;
import com.jxl.ai.intelliconf.dto.req.ConferencePageRepDTO;
import com.jxl.ai.intelliconf.dto.req.ConferenceUpdateRepDTO;
import com.jxl.ai.intelliconf.dto.resp.ConferenceSetupStatusRespDTO;
import com.jxl.ai.intelliconf.dto.resp.ConferencePageRespDTO;
import com.jxl.ai.intelliconf.dto.resp.ConferenceRespDTO;
import com.jxl.ai.intelliconf.enums.ConferenceStatus;

/**
 * 会议接口层
 */
public interface ConferenceService extends IService<ConferenceDO> {

    /**
     * 创建会议
     *
     * @param requestParam 创建请求参数
     */
    void createConference(ConferenceCreateRepDTO requestParam);

    /**
     * 修改会议信息
     *
     * @param requestParam 修改请求参数
     */
    void updateConference(ConferenceUpdateRepDTO requestParam);

    ConferenceDO resolveConference(String conferenceKey);

    ConferenceSetupStatusRespDTO getConferenceSetupStatus(String conferenceKey);

    ConferenceRespDTO updateImportantDates(String conferenceKey, ConferenceImportantDatesReqDTO requestParam);

    /**
     * 根据会议缩写查询会议详情
     *
     * @param shortName 会议缩写
     * @return 会议详情
     */
    ConferenceRespDTO getConferenceByShortName(String shortName);

    /**
     * 分页查询会议列表
     *
     * @param requestParam 分页查询请求参数
     * @return 分页会议列表
     */
    IPage<ConferencePageRespDTO> pageConference(ConferencePageRepDTO requestParam);

    /**
        * 删除会议（物理删除，含关联数据）
     *
     * @param shortName 会议缩写
     */
    void deleteConference(String shortName);

    /**
     * 切换会议状态（由自动化任务触发）
     *
     * @param confId      会议ID
     * @param targetState 目标状态，参考 ConferenceStateConstant
     */
    void switchState(Long confId, String targetState);

    /**
     * 更新会议状态（基于枚举）
     *
     * @param confId 会议ID
     * @param status 目标状态枚举
     */
    void updateStatus(Long confId, ConferenceStatus status);

}
