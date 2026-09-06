package com.jxl.ai.intelliconf.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@TableName("conf_member_invitation")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfMemberInvitationDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long conferenceId;

    private Long sourceTaskId;

    private Long roleDefId;

    private String roleCode;

    private String roleName;

    private String committeeType;

    private String committeeName;

    private String inviteeName;

    private String inviteeEmail;

    private String inviteeAffiliation;

    private String invitationToken;

    private String invitationStatus;

    private Integer sendCount;

    private Date sentAt;

    private Date lastSentAt;

    private Date expiredAt;

    private Long acceptedUserId;

    private Date acceptedAt;

    private Date declinedAt;

    private String declinedReason;

    private Date cancelledAt;

    private Date createdAt;

    private Date updatedAt;
}
