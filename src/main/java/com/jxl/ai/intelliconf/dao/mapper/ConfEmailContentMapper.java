package com.jxl.ai.intelliconf.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jxl.ai.intelliconf.dao.entity.ConfEmailContentDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ConfEmailContentMapper extends BaseMapper<ConfEmailContentDO> {

    @Update("UPDATE conf_email_content SET content_status = 3 WHERE task_log_id = #{taskLogId} AND content_status = 1 AND (send_time IS NULL OR send_time <= NOW())")
    int markProcessingIfDue(@Param("taskLogId") Long taskLogId);

    @Update("UPDATE conf_email_content SET content_status = 2 WHERE task_log_id = #{taskLogId}")
    int markSent(@Param("taskLogId") Long taskLogId);

    @Update("UPDATE conf_email_content SET content_status = 0 WHERE task_log_id = #{taskLogId}")
    int markDraft(@Param("taskLogId") Long taskLogId);
}
