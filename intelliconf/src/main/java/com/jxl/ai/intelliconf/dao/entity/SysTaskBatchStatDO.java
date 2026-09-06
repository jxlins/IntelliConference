package com.jxl.ai.intelliconf.dao.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 批量任务执行统计
 */
@TableName("sys_task_batch_stat")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SysTaskBatchStatDO {

    /**
     * 关联 sys_task_log.id
     */
    @TableId(value = "task_log_id")
    private Long taskLogId;

    /**
     * 总处理数
     */
    private Integer totalCount;

    /**
     * 成功数
     */
    private Integer successCount;

    /**
     * 失败数
     */
    private Integer failCount;

    /**
     * 处理状态：0=待处理,1=处理中,2=已完成
     */
    private Integer processStatus;

    /**
     * 最近更新时间
     */
    private Date lastUpdateTime;
}
