package com.jxl.ai.intelliconf.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@TableName("conf_stage_def")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfStageDefDO {

    private Long id;

    private String stageCode;

    private String stageName;

    private Integer stageOrder;

    private String stageDesc;

    private Integer status;

    private Date createTime;

    private Date updateTime;
}
