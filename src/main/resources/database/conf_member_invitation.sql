CREATE TABLE conf_member_invitation (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '委员会成员邀请ID',

    conference_id BIGINT NOT NULL COMMENT '会议ID',
    source_task_id BIGINT DEFAULT NULL COMMENT '来源任务ID，通常为确立会议委员会任务',

    invitee_name VARCHAR(100) DEFAULT NULL COMMENT '被邀请人姓名',
    invitee_email VARCHAR(255) NOT NULL COMMENT '被邀请人邮箱',
    invitee_affiliation VARCHAR(255) DEFAULT NULL COMMENT '被邀请人单位或机构',

    role_def_id BIGINT DEFAULT NULL COMMENT '角色定义ID',

    role_code VARCHAR(100) NOT NULL COMMENT '邀请角色编码快照',
    role_name VARCHAR(100) NOT NULL COMMENT '邀请角色名称快照',

    committee_type VARCHAR(100) NOT NULL COMMENT '委员会类型编码快照',
    committee_name VARCHAR(100) NOT NULL COMMENT '委员会名称快照',

    invitation_token VARCHAR(255) NOT NULL COMMENT '邀请令牌',

    invitation_status VARCHAR(50) NOT NULL DEFAULT 'DRAFT' COMMENT '邀请状态：DRAFT草稿，SENT已发送，ACCEPTED已接受，DECLINED已拒绝，EXPIRED已过期，CANCELLED已取消',

    invited_by BIGINT DEFAULT NULL COMMENT '邀请人用户ID',
    invited_by_name VARCHAR(100) DEFAULT NULL COMMENT '邀请人姓名',

    sent_at DATETIME DEFAULT NULL COMMENT '邀请邮件发送时间',
    accepted_user_id BIGINT DEFAULT NULL COMMENT '接受邀请后绑定的用户ID',
    accepted_at DATETIME DEFAULT NULL COMMENT '接受时间',

    declined_at DATETIME DEFAULT NULL COMMENT '拒绝时间',
    declined_reason TEXT DEFAULT NULL COMMENT '拒绝原因',

    expired_at DATETIME DEFAULT NULL COMMENT '邀请过期时间',
    cancelled_at DATETIME DEFAULT NULL COMMENT '取消时间',

    last_sent_at DATETIME DEFAULT NULL COMMENT '最近一次发送时间',
    send_count INT NOT NULL DEFAULT 0 COMMENT '发送次数',

    remark TEXT DEFAULT NULL COMMENT '备注',

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (id),

    UNIQUE KEY uk_invitation_token (invitation_token),
    UNIQUE KEY uk_conf_email_role (conference_id, invitee_email, role_code),

    KEY idx_conference_id (conference_id),
    KEY idx_source_task_id (source_task_id),
    KEY idx_invitee_email (invitee_email),
    KEY idx_role_code (role_code),
    KEY idx_committee_type (committee_type),
    KEY idx_invitation_status (invitation_status),
    KEY idx_accepted_user_id (accepted_user_id),
    KEY idx_expired_at (expired_at),

    CONSTRAINT fk_member_invitation_conference
        FOREIGN KEY (conference_id)
            REFERENCES conference_info(id),

    CONSTRAINT fk_member_invitation_task
        FOREIGN KEY (source_task_id)
            REFERENCES conf_task(id),

    CONSTRAINT fk_member_invitation_role_def
        FOREIGN KEY (role_def_id)
            REFERENCES conf_committee_role_def(id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='会议委员会成员邀请表';