package com.jxl.ai.intelliconf.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 任务执行日志持久层实体
 */
@TableName("sys_task_log")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SysTaskLogDO {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 关联会议ID
     */
    private Long confereId;

    /**
     * 关联里程碑ID
     */
    private Long milestoneId;

    /**
     * 关联任务定义ID
     */
    private Long taskDefId;

    /**
     * 执行器的 Bean 名称
     */
    private String handlerBean;

    /**
     * 执行状态：0=待处理，1=执行中，2=成功，3=失败
     */
    private Integer executionStatus;

    /**
     * 重试次数
     */
    private Integer retryCount;

    /**
     * 失败时的异常堆栈信息
     */
    private String errorMsg;

    /**
     * 任务开始时间
     */
    private Date startTime;

    /**
     * 任务结束时间
     */
    private Date endTime;

    /**
     * 记录创建时间
     */
    private Date createTime;
}
