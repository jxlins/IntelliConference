package com.jxl.ai.intelliconf.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@TableName("conf_committee_role_def")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfCommitteeRoleDefDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String roleCode;

    private String roleName;

    private String committeeType;

    private String committeeName;

    private String roleDesc;

    private Integer isRequired;

    private Integer canAssignTask;

    private Integer sortOrder;

    private Integer status;

    private Date createdAt;

    private Date updatedAt;
}
