package com.jxl.ai.intelliconf.dto.req;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jxl.ai.intelliconf.dao.entity.ConfLocalDO;
import com.jxl.ai.intelliconf.dao.entity.ConferenceDO;
import lombok.Data;

/**
 * 会议分页查询请求参数
 */
@Data
public class ConferencePageRepDTO extends Page<ConferenceDO> {

    /**
     * 创建者
     */
    private String createUser;

}
