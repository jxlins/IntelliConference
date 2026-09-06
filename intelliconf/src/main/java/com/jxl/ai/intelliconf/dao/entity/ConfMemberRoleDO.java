package com.jxl.ai.intelliconf.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@TableName("conf_member_role")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfMemberRoleDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long conferenceId;

    private Long userId;

    private Long sourceInvitationId;

    private Long roleDefId;

    private String roleCode;

    private String roleName;

    private String committeeType;

    private String committeeName;

    private String memberName;

    private String memberEmail;

    private String affiliation;

    private Integer isPrimary;

    private String memberStatus;

    private Date joinedAt;

    private Date removedAt;

    private String remark;

    private Date createdAt;

    private Date updatedAt;
}
