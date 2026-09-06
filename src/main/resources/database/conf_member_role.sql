CREATE TABLE conf_member_role (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '会议成员角色ID',

    conference_id BIGINT NOT NULL COMMENT '会议ID',

    user_id BIGINT NOT NULL COMMENT '系统用户ID',

    source_invitation_id BIGINT DEFAULT NULL COMMENT '来源邀请ID',

    role_def_id BIGINT DEFAULT NULL COMMENT '角色定义ID',

    role_code VARCHAR(100) NOT NULL COMMENT '角色编码',
    role_name VARCHAR(100) NOT NULL COMMENT '角色名称',

    committee_type VARCHAR(100) NOT NULL COMMENT '委员会类型编码',
    committee_name VARCHAR(100) NOT NULL COMMENT '委员会名称',

    member_name VARCHAR(100) DEFAULT NULL COMMENT '成员姓名',
    member_email VARCHAR(255) DEFAULT NULL COMMENT '成员邮箱',
    affiliation VARCHAR(255) DEFAULT NULL COMMENT '单位或机构',

    is_primary TINYINT NOT NULL DEFAULT 0 COMMENT '是否该角色主要负责人：1是，0否',

    member_status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE' COMMENT '成员状态：ACTIVE有效，INACTIVE停用，REMOVED移除',

    joined_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    removed_at DATETIME DEFAULT NULL COMMENT '移除时间',

    remark TEXT DEFAULT NULL COMMENT '备注',

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (id),

    UNIQUE KEY uk_conf_user_role (conference_id, user_id, role_code),

    KEY idx_conference_id (conference_id),
    KEY idx_user_id (user_id),
    KEY idx_role_code (role_code),
    KEY idx_committee_type (committee_type),
    KEY idx_member_status (member_status),
    KEY idx_source_invitation_id (source_invitation_id),

    CONSTRAINT fk_member_role_conference
        FOREIGN KEY (conference_id)
            REFERENCES conferences(id),

    CONSTRAINT fk_member_role_invitation
        FOREIGN KEY (source_invitation_id)
            REFERENCES conf_member_invitation(id),

    CONSTRAINT fk_member_role_role_def
        FOREIGN KEY (role_def_id)
            REFERENCES conf_committee_role_def(id),

    CONSTRAINT chk_member_role_primary
        CHECK (is_primary IN (0, 1))
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='会议成员角色表';