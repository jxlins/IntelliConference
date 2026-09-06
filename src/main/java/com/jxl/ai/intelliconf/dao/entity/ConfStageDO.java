package com.jxl.ai.intelliconf.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

@TableName("conf_stage")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfStageDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long conferenceId;

    private Long stageDefId;

    private String stageCode;

    private String stageName;

    private Integer stageOrder;

    private Date plannedStartTime;

    private Date plannedEndTime;

    private Date actualStartTime;

    private Date actualEndTime;

    private String stageStatus;

    private BigDecimal progress;

    private Integer isCurrent;

    private String remark;

    private Date createTime;

    private Date updateTime;
}
