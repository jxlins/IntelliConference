package com.jxl.ai.intelliconf.dto.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 任务日志响应DTO（包含任务定义信息）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskLogRespDTO {

    /**
     * 日志ID
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
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date startTime;

    /**
     * 任务结束时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date endTime;

    /**
     * 记录创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    // ===== 从任务定义中获取的字段 =====

    /**
     * 任务名称
     */
    private String taskName;

    /**
     * 任务参数（JSON字符串）
     */
    private String params;

    /**
     * 执行优先级
     */
    private Integer priority;
}
