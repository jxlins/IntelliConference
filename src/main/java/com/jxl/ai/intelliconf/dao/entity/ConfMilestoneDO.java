package com.jxl.ai.intelliconf.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 会议时间持久层实体
 */
@TableName("milestone")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ConfMilestoneDO {

    /**
     * ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联会议ID
     */
    private Long confereId;

    /**
     * 节点编码
     */
    private String nodeCode;

    /**
     * 节点名称（如论文提交、论文评审等）
     */
    private String nodeName;

    /**
     * 前置节点编码（为空则无依赖，非空则需前置节点 status=2 才可开启本节点）
     */
    private String preNodeCode;

    /**
     * 开始时间
     */
    private Date startDate;

    /**
     * 计划结束时间
     */
    private Date targetEndDate;

    /**
     * 实际完成时间
     */
    private Date actualEndDate;

    private String triggerEvent;

    /**
     * 状态 0:等待中， 1:处理中， 2:已达成， 3:已跳过
     */
    private Integer status;

    /**
     * 备注/调整原因
     */
    private String remark;

    /**
     * 是否已确认 0:未确认, 1:已确认
     */
    private Integer isConfirmed;

    private Integer sortOrder;

    private Integer isRequired;

    private Integer autoGenerateTasks;

    private Date createdAt;

    private Date updatedAt;

}
