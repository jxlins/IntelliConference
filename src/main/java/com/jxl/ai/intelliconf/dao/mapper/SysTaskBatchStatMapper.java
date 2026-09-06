package com.jxl.ai.intelliconf.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jxl.ai.intelliconf.dao.entity.SysTaskBatchStatDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 批量任务统计 Mapper
 */
@Mapper
public interface SysTaskBatchStatMapper extends BaseMapper<SysTaskBatchStatDO> {

    @Update("UPDATE sys_task_batch_stat SET success_count = success_count + #{delta}, process_status = 1 WHERE task_log_id = #{taskLogId}")
    int addSuccessCount(@Param("taskLogId") Long taskLogId, @Param("delta") int delta);

    @Update("UPDATE sys_task_batch_stat SET fail_count = fail_count + #{delta}, process_status = 1 WHERE task_log_id = #{taskLogId}")
    int addFailCount(@Param("taskLogId") Long taskLogId, @Param("delta") int delta);

    @Update("UPDATE sys_task_batch_stat SET process_status = 2 WHERE task_log_id = #{taskLogId}")
    int markCompleted(@Param("taskLogId") Long taskLogId);
}
