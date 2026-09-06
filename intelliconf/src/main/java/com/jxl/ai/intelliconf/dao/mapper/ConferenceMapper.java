package com.jxl.ai.intelliconf.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jxl.ai.intelliconf.dao.entity.ConferenceDO;
import com.jxl.ai.intelliconf.dto.req.ConferencePageRepDTO;
import com.jxl.ai.intelliconf.dto.resp.ConferencePageRespDTO;

/**
 * 会议基本信息持久层
 */
public interface ConferenceMapper extends BaseMapper<ConferenceDO> {

    /**
     * 分页查询会议
     *
     * @param requestParam 会议创建者
     * @return
     */
    IPage<ConferencePageRespDTO> pageConference(ConferencePageRepDTO requestParam);
}
