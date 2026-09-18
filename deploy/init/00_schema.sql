SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS=0;

-- BEGIN sys_user.sql
CREATE TABLE `sys_user`  (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `username` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户名',
    `password` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '密码',
    `email` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '邮箱',
    `phone` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '手机号码',
    `real_name` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '真实姓名',
    `delete_time` bigint NULL DEFAULT NULL COMMENT '注销时间戳',
    `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
    `del_flag` tinyint(1) NULL DEFAULT NULL COMMENT '删除标识 0：未删除 1：已删除',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `idx_username`(`username` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;
-- END sys_user.sql

-- BEGIN conferences.sql
CREATE TABLE `conferences`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `title` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '会议全称',
  `short_name` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '会议缩写',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '会议简介',
  `website_url` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '会议网站',
  `contact_email` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '官方联系网站',
  `host` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '主办方',
  `co_origanizer` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '承办方',
  `start_time` datetime NULL DEFAULT NULL COMMENT '会议开始时间',
  `end_time` datetime NULL DEFAULT NULL COMMENT '会议结束时间',
  `paper_submission_deadline` datetime NULL DEFAULT NULL COMMENT '投稿截止时间',
  `notification_of_acceptance` datetime NULL DEFAULT NULL COMMENT '录用通知时间',
  `camera_ready_submission` datetime NULL DEFAULT NULL COMMENT '终稿提交截止时间',
  `early_bird_registration` datetime NULL DEFAULT NULL COMMENT '早鸟注册截止时间',
  `conference_start_date` datetime NULL DEFAULT NULL COMMENT '会议开始时间',
  `conference_end_date` datetime NULL DEFAULT NULL COMMENT '会议结束时间',
  `current_state` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '当前状态',
  `setup_status` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'BASIC_CREATED' COMMENT '会议初始化状态',
  `create_user` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` tinyint(1) NULL DEFAULT NULL COMMENT '删除标识 0：未删除 1：已删除',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- END conferences.sql

-- BEGIN location.sql
CREATE TABLE `location`  (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `confer_id` bigint NOT NULL COMMENT '关联会议ID',
    `province` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '省名称',
    `city` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '市名称',
    `country` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '国家标识',
    `address` varchar(2048) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '详细地址',
    `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
    `del_flag` tinyint(1) NULL DEFAULT NULL COMMENT '删除标识 0：未删除 1：已删除',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `idx_confer_id`(`confer_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;
-- END location.sql

-- BEGIN conf_stage_def.sql
DROP TABLE IF EXISTS conf_stage_def;

CREATE TABLE conf_stage_def (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'stage template id',
    stage_code VARCHAR(100) NOT NULL COMMENT 'stage code',
    stage_name VARCHAR(255) NOT NULL COMMENT 'stage name',
    stage_order INT NOT NULL COMMENT 'stage order',
    stage_desc TEXT DEFAULT NULL COMMENT 'stage description',
    status TINYINT NOT NULL DEFAULT 1 COMMENT 'enabled status: 1 enabled, 0 disabled',
    create_time datetime NULL DEFAULT NULL COMMENT 'create time',
    update_time datetime NULL DEFAULT NULL COMMENT 'update time',

    PRIMARY KEY (id),
    UNIQUE KEY uk_stage_code (stage_code),
    UNIQUE KEY uk_stage_order (stage_order)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='conference stage template';

INSERT INTO conf_stage_def (stage_code, stage_name, stage_order, stage_desc, status, create_time, update_time) VALUES
('CONFERENCE_STARTUP', '会议创建与启动阶段', 1, '创建会议并完成启动准备', 1, NOW(), NOW()),
('CALL_FOR_PAPERS', '征文投稿阶段', 2, '发布征文并接收投稿', 1, NOW(), NOW()),
('REVIEW', '审稿阶段', 3, '组织审稿与录用决策', 1, NOW(), NOW()),
('REGISTRATION', '注册阶段', 4, '参会注册与缴费', 1, NOW(), NOW()),
('CONFERENCE_PREPARATION', '会务准备阶段', 5, '会议日程、场地与物料准备', 1, NOW(), NOW()),
('CONFERENCE_DAYS', '会议阶段', 6, '会议正式举办', 1, NOW(), NOW()),
('POST_CONFERENCE', '会后整理阶段', 7, '会后资料整理与归档', 1, NOW(), NOW());

-- END conf_stage_def.sql

-- BEGIN conf_task_def.sql
CREATE TABLE conf_task_def (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '任务模板ID',

    stage_def_id BIGINT NOT NULL COMMENT '所属阶段模板ID',

    task_code VARCHAR(100) NOT NULL COMMENT '任务编码',
    task_name VARCHAR(255) NOT NULL COMMENT '任务名称',
    task_desc TEXT DEFAULT NULL COMMENT '任务说明',

    task_type VARCHAR(100) DEFAULT NULL COMMENT '任务类型',
    default_role VARCHAR(100) DEFAULT NULL COMMENT '默认负责角色',

    is_core TINYINT NOT NULL DEFAULT 0 COMMENT '是否核心任务：1是，0否',
    completion_type VARCHAR(50) NOT NULL DEFAULT 'MANUAL_CONFIRM',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '任务排序',

    offset_base VARCHAR(100) DEFAULT NULL COMMENT '时间偏移基准',
    start_offset_days INT DEFAULT NULL COMMENT '计划开始时间偏移天数',
    end_offset_days INT DEFAULT NULL COMMENT '计划截止时间偏移天数',

    need_review TINYINT NOT NULL DEFAULT 0 COMMENT '是否需要审核：1是，0否',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：1启用，0停用',

    create_time datetime NULL DEFAULT NULL COMMENT '创建时间',
    update_time datetime NULL DEFAULT NULL COMMENT '更新时间',

    PRIMARY KEY (id),

    UNIQUE KEY uk_stage_task_code (stage_def_id, task_code),
    KEY idx_stage_def_id (stage_def_id),
    KEY idx_task_type (task_type),
    KEY idx_default_role (default_role),
    KEY idx_sort_order (sort_order),

    CONSTRAINT fk_task_def_stage_def
        FOREIGN KEY (stage_def_id)
            REFERENCES conf_stage_def(id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='会议任务模板表';
-- END conf_task_def.sql

-- BEGIN conf_stage.sql
DROP TABLE IF EXISTS conf_stage;

CREATE TABLE conf_stage (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'stage instance id',
    conference_id BIGINT NOT NULL COMMENT 'conference id',
    stage_def_id BIGINT NOT NULL COMMENT 'stage template id',
    stage_code VARCHAR(100) NOT NULL COMMENT 'stage code',
    stage_name VARCHAR(255) NOT NULL COMMENT 'stage name',
    stage_order INT NOT NULL COMMENT 'stage order',
    planned_start_time DATETIME DEFAULT NULL COMMENT 'planned start time',
    planned_end_time DATETIME DEFAULT NULL COMMENT 'planned end time',
    actual_start_time DATETIME DEFAULT NULL COMMENT 'actual start time',
    actual_end_time DATETIME DEFAULT NULL COMMENT 'actual end time',
    stage_status VARCHAR(50) NOT NULL DEFAULT 'PLANNED' COMMENT 'stage status',
    progress DECIMAL(5,2) NOT NULL DEFAULT 0.00 COMMENT 'stage progress',
    is_current TINYINT NOT NULL DEFAULT 0 COMMENT 'whether current stage',
    remark TEXT DEFAULT NULL COMMENT 'remark',
    create_time datetime NULL DEFAULT NULL COMMENT 'create time',
    update_time datetime NULL DEFAULT NULL COMMENT 'update time',

    PRIMARY KEY (id),
    UNIQUE KEY uk_conference_stage_code (conference_id, stage_code),
    UNIQUE KEY uk_conference_stage_order (conference_id, stage_order),
    KEY idx_conference_id (conference_id),
    KEY idx_stage_def_id (stage_def_id),
    KEY idx_stage_status (stage_status),
    KEY idx_is_current (is_current),
    KEY idx_planned_start_time (planned_start_time),
    KEY idx_planned_end_time (planned_end_time),
    CONSTRAINT fk_conf_stage_conference
        FOREIGN KEY (conference_id)
            REFERENCES conferences(id),
    CONSTRAINT fk_conf_stage_stage_def
        FOREIGN KEY (stage_def_id)
            REFERENCES conf_stage_def(id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='conference stage instance';

-- END conf_stage.sql

-- BEGIN conf_task.sql
CREATE TABLE conf_task (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '会议任务实例ID',

    conference_id BIGINT NOT NULL COMMENT '会议ID',
    stage_id BIGINT NOT NULL COMMENT '会议阶段实例ID',
    task_def_id BIGINT DEFAULT NULL COMMENT '任务模板ID',

    stage_code VARCHAR(100) NOT NULL COMMENT '阶段编码快照',
    task_code VARCHAR(100) NOT NULL COMMENT '任务编码快照',

    task_name VARCHAR(255) NOT NULL COMMENT '任务名称',
    task_desc TEXT DEFAULT NULL COMMENT '任务说明',

    task_type VARCHAR(100) DEFAULT NULL COMMENT '任务类型',
    principal_role VARCHAR(100) DEFAULT NULL COMMENT '默认负责角色',

    principal_user_id BIGINT DEFAULT NULL COMMENT '负责人用户ID',
    principal_name VARCHAR(100) DEFAULT NULL COMMENT '负责人姓名',

    planned_start_time DATETIME DEFAULT NULL COMMENT '计划开始时间',
    planned_end_time DATETIME DEFAULT NULL COMMENT '计划截止时间',

    actual_start_time DATETIME DEFAULT NULL COMMENT '实际开始时间',
    actual_end_time DATETIME DEFAULT NULL COMMENT '实际完成时间',

    task_status VARCHAR(50) NOT NULL DEFAULT 'NOT_STARTED' COMMENT '任务状态',

    priority VARCHAR(50) NOT NULL DEFAULT 'MEDIUM' COMMENT '任务优先级',
    risk_level VARCHAR(50) NOT NULL DEFAULT 'NORMAL' COMMENT '风险等级',

    is_core TINYINT NOT NULL DEFAULT 0 COMMENT '是否核心任务：1是，0否',
    completion_type VARCHAR(50) NOT NULL DEFAULT 'MANUAL_CONFIRM',
    completion_desc TEXT DEFAULT NULL COMMENT '任务完成说明',
    completion_url VARCHAR(500) DEFAULT NULL COMMENT '完成结果链接',
    completed_by BIGINT DEFAULT NULL COMMENT '完成人ID',
    completed_by_name VARCHAR(100) DEFAULT NULL COMMENT '完成人姓名',
    submitted_at DATETIME DEFAULT NULL COMMENT '提交完成时间',
    reviewed_by BIGINT DEFAULT NULL COMMENT '审核人ID',
    reviewed_by_name VARCHAR(100) DEFAULT NULL COMMENT '审核人姓名',
    reviewed_at DATETIME DEFAULT NULL COMMENT '审核时间',
    review_comment TEXT DEFAULT NULL COMMENT '审核意见',
    need_review TINYINT NOT NULL DEFAULT 0 COMMENT '是否需要审核：1是，0否',

    output_desc TEXT DEFAULT NULL COMMENT '任务产出物说明',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '任务排序号',

    remark TEXT DEFAULT NULL COMMENT '备注',

    create_user BIGINT DEFAULT NULL COMMENT '创建人ID',
    update_user BIGINT DEFAULT NULL COMMENT '更新人ID',

    create_time datetime NULL DEFAULT NULL COMMENT '创建时间',
    update_time datetime NULL DEFAULT NULL COMMENT '更新时间',

    PRIMARY KEY (id),

    UNIQUE KEY uk_conference_task_code (conference_id, task_code),

    KEY idx_conference_id (conference_id),
    KEY idx_stage_id (stage_id),
    KEY idx_task_def_id (task_def_id),
    KEY idx_stage_code (stage_code),
    KEY idx_task_status (task_status),
    KEY idx_priority (priority),
    KEY idx_risk_level (risk_level),
    KEY idx_principal_user_id (principal_user_id),
    KEY idx_planned_start_time (planned_start_time),
    KEY idx_planned_end_time (planned_end_time),
    KEY idx_sort_order (sort_order),

    CONSTRAINT fk_conf_task_conference
        FOREIGN KEY (conference_id)
            REFERENCES conferences(id),

    CONSTRAINT fk_conf_task_stage
        FOREIGN KEY (stage_id)
            REFERENCES conf_stage(id),

    CONSTRAINT fk_conf_task_task_def
        FOREIGN KEY (task_def_id)
            REFERENCES conf_task_def(id),

    CONSTRAINT chk_conf_task_is_core
        CHECK (is_core IN (0, 1)),

    CONSTRAINT chk_conf_task_need_review
        CHECK (need_review IN (0, 1))

) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='会议任务实例表';
-- END conf_task.sql

-- BEGIN conf_committee_role_def.sql
CREATE TABLE conf_committee_role_def (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '委员会角色定义ID',

    role_code VARCHAR(100) NOT NULL COMMENT '角色编码',
    role_name VARCHAR(100) NOT NULL COMMENT '角色名称',

    committee_type VARCHAR(100) NOT NULL COMMENT '委员会类型编码',
    committee_name VARCHAR(100) NOT NULL COMMENT '委员会名称',

    role_desc TEXT DEFAULT NULL COMMENT '角色说明',

    is_required TINYINT NOT NULL DEFAULT 0 COMMENT '是否关键角色：1是，0否',
    can_assign_task TINYINT NOT NULL DEFAULT 1 COMMENT '是否可作为任务负责人：1是，0否',

    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序号',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：1启用，0停用',

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (id),

    UNIQUE KEY uk_role_code (role_code),
    KEY idx_committee_type (committee_type),
    KEY idx_status (status),
    KEY idx_sort_order (sort_order),

    CONSTRAINT chk_committee_role_required
        CHECK (is_required IN (0, 1)),

    CONSTRAINT chk_committee_role_assign_task
        CHECK (can_assign_task IN (0, 1))
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='会议委员会角色定义表';
-- END conf_committee_role_def.sql

-- BEGIN conf_member_invitation.sql
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
            REFERENCES conferences(id),

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
-- END conf_member_invitation.sql

-- BEGIN conf_member_role.sql
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
-- END conf_member_role.sql

-- BEGIN conf_task_dep.sql
CREATE TABLE conf_task_dep (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '任务依赖ID',

    conference_id BIGINT NOT NULL COMMENT '会议ID',

    from_task_id BIGINT NOT NULL COMMENT '前置任务ID',
    to_task_id BIGINT NOT NULL COMMENT '后置任务ID',

    dep_type VARCHAR(50) NOT NULL DEFAULT 'FINISH_TO_START' COMMENT '依赖类型',

    is_required TINYINT NOT NULL DEFAULT 1 COMMENT '是否强依赖：1是，0否',

    lag_days INT NOT NULL DEFAULT 0 COMMENT '依赖滞后天数，前置任务完成后间隔多少天允许后置任务开始',

    remark TEXT DEFAULT NULL COMMENT '备注',

    create_user BIGINT DEFAULT NULL COMMENT '创建人ID',

    create_time datetime NULL DEFAULT NULL COMMENT '创建时间',
    update_time datetime NULL DEFAULT NULL COMMENT '更新时间',

    PRIMARY KEY (id),

    UNIQUE KEY uk_conf_task_dep (conference_id, from_task_id, to_task_id),

    KEY idx_conference_id (conference_id),
    KEY idx_from_task_id (from_task_id),
    KEY idx_to_task_id (to_task_id),
    KEY idx_dep_type (dep_type),

    CONSTRAINT fk_conf_task_dep_conference
        FOREIGN KEY (conference_id)
            REFERENCES conferences(id),

    CONSTRAINT fk_conf_task_dep_from_task
        FOREIGN KEY (from_task_id)
            REFERENCES conf_task(id),

    CONSTRAINT fk_conf_task_dep_to_task
        FOREIGN KEY (to_task_id)
            REFERENCES conf_task(id),

    CONSTRAINT chk_conf_task_dep_required
        CHECK (is_required IN (0, 1)),

    CONSTRAINT chk_conf_task_dep_not_self
        CHECK (from_task_id <> to_task_id)

) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='会议任务依赖表';
-- END conf_task_dep.sql

-- BEGIN conf_task_log.sql
CREATE TABLE IF NOT EXISTS conf_task_log (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '任务操作记录ID',

    conference_id BIGINT NOT NULL COMMENT '会议ID',
    task_id BIGINT NOT NULL COMMENT '任务ID',

    operation_type VARCHAR(100) NOT NULL COMMENT '操作类型',
    old_value TEXT DEFAULT NULL COMMENT '变更前内容',
    new_value TEXT DEFAULT NULL COMMENT '变更后内容',

    operator_id BIGINT DEFAULT NULL COMMENT '操作人ID',
    operator_name VARCHAR(100) DEFAULT NULL COMMENT '操作人姓名',

    operation_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    remark TEXT DEFAULT NULL COMMENT '备注',

    PRIMARY KEY (id),

    KEY idx_conference_id (conference_id),
    KEY idx_task_id (task_id),
    KEY idx_operation_type (operation_type),
    KEY idx_operation_time (operation_time),

    CONSTRAINT fk_task_log_task
    FOREIGN KEY (task_id)
    REFERENCES conf_task(id)

) ENGINE=InnoDB
DEFAULT CHARSET=utf8mb4
COLLATE=utf8mb4_unicode_ci
COMMENT='会议任务操作记录表';
-- END conf_task_log.sql

-- BEGIN conf_author_discovery.sql
CREATE TABLE IF NOT EXISTS `conf_author_discovery_job` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `conference_id` bigint NOT NULL COMMENT '会议ID',
  `topic_keywords` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'JSON topic keywords',
  `year_from` int NULL DEFAULT NULL COMMENT '起始发表年份',
  `year_to` int NULL DEFAULT NULL COMMENT '结束发表年份',
  `max_authors` int NULL DEFAULT 200 COMMENT '最大候选作者数',
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'PENDING/RUNNING/COMPLETED/FAILED',
  `total_papers` int NULL DEFAULT 0 COMMENT '论文数量',
  `total_candidates` int NULL DEFAULT 0 COMMENT '候选作者数量',
  `high_confidence_emails` int NULL DEFAULT 0 COMMENT '高可信邮箱数量',
  `error_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '错误信息',
  `created_by` bigint NULL DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `started_at` datetime NULL DEFAULT NULL COMMENT '开始时间',
  `finished_at` datetime NULL DEFAULT NULL COMMENT '结束时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_conf_author_discovery_job_conf` (`conference_id`) USING BTREE,
  KEY `idx_conf_author_discovery_job_status` (`status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

CREATE TABLE IF NOT EXISTS `conf_potential_author` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `conference_id` bigint NOT NULL COMMENT '会议ID',
  `discovery_job_id` bigint NULL DEFAULT NULL COMMENT '发现任务ID',
  `author_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '作者姓名',
  `normalized_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '归一化姓名',
  `email` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '邮箱',
  `organization` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '机构',
  `country_region` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '国家或地区',
  `research_keywords` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '研究关键词JSON',
  `representative_papers` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '代表论文JSON',
  `source_platform` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '来源平台',
  `source_url` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '来源URL',
  `topic_similarity` decimal(6,4) NULL DEFAULT 0 COMMENT '主题相关性',
  `email_confidence` decimal(6,4) NULL DEFAULT 0 COMMENT '邮箱可信度',
  `overall_score` decimal(6,4) NULL DEFAULT 0 COMMENT '综合分',
  `review_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED',
  `contact_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'NOT_CONTACTED' COMMENT '联系状态',
  `created_at` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `updated_at` datetime NULL DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_conf_author_identity` (`conference_id`, `normalized_name`, `organization`) USING BTREE,
  UNIQUE KEY `uk_conf_author_email` (`conference_id`, `email`) USING BTREE,
  KEY `idx_conf_potential_author_review` (`conference_id`, `review_status`) USING BTREE,
  KEY `idx_conf_potential_author_score` (`conference_id`, `overall_score`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

CREATE TABLE IF NOT EXISTS `conf_author_email_source` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `potential_author_id` bigint NULL DEFAULT NULL COMMENT '候选作者ID',
  `email` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '邮箱',
  `source_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'PAPER_PDF/OPEN_ACCESS_PAGE/PUBLISHER_PAGE/AUTHOR_PAGE',
  `source_url` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '来源URL',
  `evidence_text` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '证据文本',
  `confidence` decimal(6,4) NULL DEFAULT 0 COMMENT '可信度',
  `collected_at` datetime NULL DEFAULT NULL COMMENT '采集时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_conf_author_email_source_author` (`potential_author_id`) USING BTREE,
  KEY `idx_conf_author_email_source_email` (`email`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

CREATE TABLE IF NOT EXISTS `conf_email_suppression` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `conference_id` bigint NULL DEFAULT NULL COMMENT '会议ID，空表示全局屏蔽',
  `email` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '邮箱',
  `reason` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'OPT_OUT/BOUNCED/COMPLAINT/MANUAL_BLOCK',
  `created_at` datetime NULL DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_conf_email_suppression` (`conference_id`, `email`) USING BTREE,
  KEY `idx_conf_email_suppression_email` (`email`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- END conf_author_discovery.sql

-- BEGIN conf_committee.sql
CREATE TABLE `conf_committee` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `conf_id` bigint NOT NULL COMMENT '会议ID',
    `email` varchar(256) NOT NULL COMMENT '委员会成员邮箱',
    `name` varchar(256) DEFAULT NULL COMMENT '委员会成员姓名',
    `institution` varchar(256) DEFAULT NULL COMMENT '委员会成员单位',
    `role` varchar(64) NOT NULL COMMENT '委员会角色 CHAIR/REVIEWER',
    `invite_status` varchar(32) NOT NULL DEFAULT 'INVITED' COMMENT '邀请状态 INVITED/ACCEPTED/DECLINED',
    `access_token` varchar(64) DEFAULT NULL COMMENT '门户访问令牌(UUID)',
    `token_expire_time` datetime DEFAULT NULL COMMENT '令牌过期时间',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag` tinyint DEFAULT 0 COMMENT '删除标识 0-有效 1-删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_conf_email` (`conf_id`,`email`),
    UNIQUE KEY `uk_access_token` (`access_token`),
    KEY `idx_conf_role` (`conf_id`,`role`),
    KEY `idx_conf_status` (`conf_id`,`invite_status`)
) ENGINE=InnoDB COMMENT='会议委员会成员表';

-- END conf_committee.sql

-- BEGIN conf_contact_pool.sql
CREATE TABLE `conf_contact_pool` (
    `id` bigint PRIMARY KEY AUTO_INCREMENT,
    `owner_org_id` bigint NOT NULL COMMENT '所属组织者ID(关联sys_user或其机构)',
    `name` varchar(100) NOT NULL COMMENT '姓名',
    `email` varchar(150) NOT NULL COMMENT '邮箱',
    `institution` varchar(255) COMMENT '单位',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    UNIQUE INDEX `uk_org_email` (`owner_org_id`, `email`)
) ENGINE=InnoDB COMMENT='组织者名下的联系人总库';
-- END conf_contact_pool.sql

-- BEGIN conf_email_content.sql
CREATE TABLE `conf_email_content` (
    `task_log_id` bigint PRIMARY KEY COMMENT '关联具体的任务实例ID',
    `conf_id` bigint NOT NULL,
    `subject` varchar(255) COMMENT '邮件主题',
    `content_body` text COMMENT '用户输入的邮件正文',
    `target_role` varchar(50) COMMENT '冗余记录发送目标角色，如 PROSPECT',
    `send_time` datetime COMMENT '计划发送时间，为空默认当前时间',
    `content_status` tinyint DEFAULT 0 COMMENT '0=草稿,1=待发送,2=已发送,3=发送中',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB COMMENT='任务关联邮件内容表';
-- END conf_email_content.sql

-- BEGIN conf_member.sql
CREATE TABLE `conf_member` (
    `id` bigint PRIMARY KEY AUTO_INCREMENT,
    `conf_id` bigint NOT NULL COMMENT '具体会议ID',
    `contact_id` bigint NOT NULL COMMENT '关联contact_pool中的ID',
    `role` varchar(50) NOT NULL COMMENT '在此会议中的身份(如: AUTHOR, REVIEWER)',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_conf_role` (`conf_id`, `role`)
) ENGINE=InnoDB COMMENT='会议成员及身份关联表';
-- END conf_member.sql

-- BEGIN conf_task_action_log.sql
CREATE TABLE `conf_task_action_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_instance_id` bigint DEFAULT NULL COMMENT '事务实例ID',
  `conference_id` bigint NOT NULL COMMENT '会议ID',
  `operator_id` varchar(100) DEFAULT NULL,
  `operator_name` varchar(100) DEFAULT NULL,
  `operator_role_code` varchar(100) DEFAULT NULL,
  `action_type` varchar(50) NOT NULL COMMENT 'CREATE/ASSIGN/START/COMPLETE等',
  `comment` text COMMENT '备注',
  `before_status` varchar(32) DEFAULT NULL,
  `after_status` varchar(32) DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_task_time` (`task_instance_id`, `created_at`),
  KEY `idx_conf_time` (`conference_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会议事务处理日志';

-- END conf_task_action_log.sql

-- BEGIN conf_task_assignment.sql
CREATE TABLE `conf_task_assignment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_instance_id` bigint NOT NULL COMMENT '事务实例ID',
  `conference_id` bigint NOT NULL COMMENT '会议ID',
  `assignee_type` varchar(20) NOT NULL COMMENT 'ROLE/USER',
  `assignee_user_id` varchar(100) DEFAULT NULL,
  `assignee_user_name` varchar(100) DEFAULT NULL,
  `assignee_role_code` varchar(100) DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'ASSIGNED',
  `assigned_by` varchar(100) DEFAULT NULL,
  `assigned_at` datetime DEFAULT NULL,
  `accepted_at` datetime DEFAULT NULL,
  `completed_at` datetime DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_task` (`task_instance_id`),
  KEY `idx_conf_role` (`conference_id`, `assignee_role_code`),
  KEY `idx_conf_user` (`conference_id`, `assignee_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会议事务分派';

-- END conf_task_assignment.sql

-- BEGIN conf_task_attachment.sql
CREATE TABLE conf_task_attachment (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'task attachment id',
    conference_id BIGINT NOT NULL COMMENT 'conference id',
    task_id BIGINT NOT NULL COMMENT 'task id',
    file_name VARCHAR(255) NOT NULL COMMENT 'original file name',
    file_url VARCHAR(500) NOT NULL COMMENT 'stored file path',
    file_type VARCHAR(100) DEFAULT NULL COMMENT 'file content type',
    file_size BIGINT DEFAULT NULL COMMENT 'file size',
    uploaded_by BIGINT DEFAULT NULL COMMENT 'uploaded by user id',
    uploaded_by_name VARCHAR(100) DEFAULT NULL COMMENT 'uploaded by user name',
    uploaded_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'uploaded time',
    process_status VARCHAR(50) NOT NULL DEFAULT 'UNPROCESSED' COMMENT 'attachment process status',
    process_type VARCHAR(100) DEFAULT NULL COMMENT 'attachment process type',
    related_batch_id BIGINT DEFAULT NULL COMMENT 'related batch id, reserved',
    process_message TEXT DEFAULT NULL COMMENT 'attachment process summary',
    process_result TEXT DEFAULT NULL COMMENT 'attachment process result json',
    total_count INT DEFAULT NULL COMMENT 'processed total count',
    success_count INT DEFAULT NULL COMMENT 'processed success count',
    failed_count INT DEFAULT NULL COMMENT 'processed failed count',
    duplicate_count INT DEFAULT NULL COMMENT 'processed duplicate count',
    PRIMARY KEY (id),
    KEY idx_conference_id (conference_id),
    KEY idx_task_id (task_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='conference task attachments';

-- END conf_task_attachment.sql

-- BEGIN conf_task_instance.sql
CREATE TABLE `conf_task_instance` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `conference_id` bigint NOT NULL COMMENT '会议ID',
  `milestone_id` bigint NOT NULL COMMENT '阶段实例ID',
  `task_def_id` bigint NOT NULL COMMENT '标准事务定义ID',
  `node_code` varchar(100) NOT NULL COMMENT '阶段编码',
  `task_code` varchar(100) DEFAULT NULL COMMENT '事务编码',
  `task_name` varchar(255) NOT NULL COMMENT '事务名称',
  `task_desc` text COMMENT '事务说明',
  `task_type` varchar(50) DEFAULT 'MANUAL' COMMENT 'MANUAL/EMAIL/SYSTEM/CHECK',
  `handler_bean` varchar(100) DEFAULT NULL COMMENT '处理器Bean',
  `params` text COMMENT '扩展参数',
  `priority` varchar(20) DEFAULT 'MEDIUM' COMMENT 'HIGH/MEDIUM/LOW',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `due_time` datetime DEFAULT NULL COMMENT '截止时间',
  `jump_url` varchar(255) DEFAULT NULL COMMENT '处理页面',
  `current_handler_id` varchar(100) DEFAULT NULL COMMENT '当前处理人ID',
  `current_handler_name` varchar(100) DEFAULT NULL COMMENT '当前处理人名称',
  `created_by` varchar(100) DEFAULT NULL COMMENT '创建人',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_conf_task_def` (`conference_id`, `task_def_id`),
  KEY `idx_conf_status` (`conference_id`, `status`),
  KEY `idx_milestone` (`milestone_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会议真实事务实例';

-- END conf_task_instance.sql

-- BEGIN conference_mail_account.sql
CREATE TABLE `conference_mail_account` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `conference_id` bigint NOT NULL COMMENT '会议ID',
    `provider_type` varchar(32) NOT NULL COMMENT 'SMTP/TENCENT_EXMAIL',
    `from_email` varchar(150) NOT NULL COMMENT '官方发件邮箱',
    `from_name` varchar(100) DEFAULT NULL COMMENT '发件显示名',
    `reply_to` varchar(150) DEFAULT NULL COMMENT '回复邮箱',
    `smtp_host` varchar(150) NOT NULL COMMENT 'SMTP服务器',
    `smtp_port` int NOT NULL COMMENT 'SMTP端口',
    `username` varchar(150) NOT NULL COMMENT '邮箱账号',
    `password_cipher` text NOT NULL COMMENT '加密后的邮箱密码或客户端专用密码',
    `ssl_enabled` tinyint(1) DEFAULT 1 COMMENT '是否启用SSL',
    `starttls_enabled` tinyint(1) DEFAULT 0 COMMENT '是否启用STARTTLS',
    `enabled` tinyint(1) DEFAULT 1 COMMENT '是否启用',
    `last_test_status` varchar(32) DEFAULT NULL COMMENT '最近一次测试状态',
    `last_test_time` datetime DEFAULT NULL COMMENT '最近一次测试时间',
    `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
    `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_conference_mail_account_conf` (`conference_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会议官方邮箱配置';

-- END conference_mail_account.sql

-- BEGIN mail_send_log.sql
CREATE TABLE `mail_send_log` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `task_id` bigint NOT NULL COMMENT '发送任务ID',
    `conference_id` bigint NOT NULL COMMENT '会议ID',
    `recipient_email` varchar(150) NOT NULL COMMENT '收件人邮箱',
    `subject` varchar(255) NOT NULL COMMENT '邮件主题',
    `status` varchar(16) NOT NULL COMMENT 'SUCCESS/FAILED',
    `error_code` varchar(32) DEFAULT NULL COMMENT '错误编码',
    `error_message` text COMMENT '错误信息',
    `provider_type` varchar(32) NOT NULL COMMENT '邮件服务商',
    `sent_at` datetime DEFAULT NULL COMMENT '发送时间',
    `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_task_status` (`task_id`, `status`),
    KEY `idx_conf_created` (`conference_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='逐封邮件发送日志';

-- END mail_send_log.sql

-- BEGIN mail_send_task.sql
CREATE TABLE `mail_send_task` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `conference_id` bigint NOT NULL COMMENT '会议ID',
    `template_id` bigint DEFAULT NULL COMMENT '模板ID',
    `subject` varchar(255) NOT NULL COMMENT '发送主题',
    `status` varchar(32) NOT NULL COMMENT 'PENDING/SENDING/SUCCESS/PARTIAL_FAILED/FAILED',
    `total_count` int DEFAULT 0,
    `success_count` int DEFAULT 0,
    `fail_count` int DEFAULT 0,
    `created_by` varchar(100) DEFAULT NULL COMMENT '创建人',
    `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
    `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_conf_created` (`conference_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='邮件批量发送任务';

-- END mail_send_task.sql

-- BEGIN mail_template.sql
CREATE TABLE `mail_template` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `conference_id` bigint NOT NULL COMMENT '会议ID',
    `scene_code` varchar(64) NOT NULL COMMENT '邮件场景',
    `template_name` varchar(150) NOT NULL COMMENT '模板名称',
    `subject_template` varchar(255) NOT NULL COMMENT '主题模板',
    `html_template` text COMMENT 'HTML正文模板',
    `text_template` text COMMENT '纯文本正文模板',
    `enabled` tinyint(1) DEFAULT 1 COMMENT '是否启用',
    `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
    `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_conf_scene_enabled` (`conference_id`, `scene_code`, `enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会议邮件模板';

-- END mail_template.sql

-- BEGIN milestone.sql
CREATE TABLE `milestone` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `confere_id` bigint NOT NULL COMMENT 'conference id, keep legacy column name',
  `node_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'milestone node code',
  `node_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'milestone node name',
  `pre_node_code` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'previous milestone node code',
  `start_date` datetime DEFAULT NULL COMMENT 'planned start time',
  `target_end_date` datetime DEFAULT NULL COMMENT 'planned end time',
  `actual_end_date` datetime DEFAULT NULL COMMENT 'actual completed time',
  `trigger_event` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'legacy FSM trigger event',
  `status` tinyint DEFAULT 0 COMMENT '0 waiting, 1 processing, 2 completed, 3 skipped',
  `remark` varchar(1024) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'remark',
  `is_confirmed` tinyint(1) DEFAULT 0 COMMENT 'legacy timeline confirmed flag',
  `sort_order` int DEFAULT 0 COMMENT 'milestone order',
  `is_required` tinyint(1) DEFAULT 1 COMMENT 'required milestone flag',
  `auto_generate_tasks` tinyint(1) DEFAULT 1 COMMENT 'auto generate task instances flag',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_conf_node` (`confere_id`, `node_code`) USING BTREE,
  KEY `idx_confere_id` (`confere_id`) USING BTREE,
  KEY `idx_conf_status` (`confere_id`, `status`) USING BTREE,
  KEY `idx_conf_sort` (`confere_id`, `sort_order`) USING BTREE,
  KEY `idx_conf_pre_node` (`confere_id`, `pre_node_code`) USING BTREE
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='conference timeline milestone instances';

-- END milestone.sql

-- BEGIN sys_mail_log.sql
CREATE TABLE `sys_mail_log` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `task_log_id` bigint NOT NULL COMMENT '关联的任务日志ID',
    `recipient_email` varchar(150) NOT NULL COMMENT '收件人邮箱',
    `send_status` tinyint NOT NULL COMMENT '发送状态: 1-成功, 0-失败',
    `error_msg` text COMMENT '失败原因',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    PRIMARY KEY (`id`),
    INDEX `idx_task_status`(`task_log_id`, `send_status`)
) ENGINE=InnoDB COMMENT='邮件发送明细日志表';

-- END sys_mail_log.sql

-- BEGIN sys_task_batch_stat.sql
CREATE TABLE `sys_task_batch_stat` (
    `task_log_id` bigint PRIMARY KEY COMMENT '关联 sys_task_log 的 ID',
    `total_count` int DEFAULT 0 COMMENT '总条数（如总人数、总稿件数）',
    `success_count` int DEFAULT 0 COMMENT '成功处理数',
    `fail_count` int DEFAULT 0 COMMENT '失败处理数',
    `process_status` tinyint DEFAULT 0 COMMENT '0:待处理, 1:处理中, 2:已完成',
    `last_update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB COMMENT='批量任务执行统计表';
-- END sys_task_batch_stat.sql

-- BEGIN sys_task_log.sql
CREATE TABLE `sys_task_log` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `confere_id` bigint NOT NULL COMMENT '会议ID',
    `milestone_id` bigint NOT NULL COMMENT '关联的里程碑ID',
    `task_def_id` bigint NOT NULL COMMENT '关联的任务定义ID',
    `handler_bean` varchar(100) COMMENT '执行器的Bean名称',
    `execution_status` tinyint DEFAULT 0 COMMENT '状态: 0-待处理, 1-执行中, 2-成功, 3-失败',
    `retry_count` int DEFAULT 0 COMMENT '重试次数',
    `error_msg` text COMMENT '异常堆栈信息',
    `start_time` datetime DEFAULT NULL COMMENT '开始时间',
    `end_time` datetime DEFAULT NULL COMMENT '结束时间',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    PRIMARY KEY (`id`),
    INDEX `idx_conf_milestone`(`confere_id`, `milestone_id`),
    INDEX `idx_status`(`execution_status`)
) ENGINE=InnoDB COMMENT='自动化任务执行日志表';
-- END sys_task_log.sql

-- BEGIN conf_workflow_task_seed.sql
-- IntelliConf 标准会议流程任务模板。
-- 使用阶段内相对日期，会议关键日期调整后可重新生成计划而无需修改模板。

UPDATE conf_task_def
SET default_role = 'SECRETARY'
WHERE default_role = 'SECRETARIAT';

UPDATE conf_task
SET principal_role = 'SECRETARY'
WHERE principal_role = 'SECRETARIAT';

INSERT INTO conf_task_def
    (stage_def_id, task_code, task_name, task_desc, task_type, default_role, completion_type,
     sort_order, offset_base, start_offset_days, end_offset_days, is_core, need_review, status,
     create_time, update_time)
SELECT s.id, v.task_code, v.task_name, v.task_desc, v.task_type, v.default_role, v.completion_type,
       v.sort_order, v.offset_base, v.start_offset_days, v.end_offset_days, v.is_core, v.need_review, 1,
       NOW(), NOW()
FROM conf_stage_def s
JOIN (
    SELECT 'DEFINE_REVIEW_POLICY' task_code, '确认评审规则与评分标准' task_name, '确认评审轮次、评分维度、利益冲突和录用规则' task_desc, 'RULE_CONFIG' task_type, 'PROGRAM_CHAIR' default_role, 'MANUAL_CONFIRM' completion_type, 10 sort_order, 'STAGE_START_TIME' offset_base, 0 start_offset_days, 2 end_offset_days, 1 is_core, 1 need_review
    UNION ALL SELECT 'RECRUIT_REVIEWERS', '补充并确认审稿人库', '根据投稿主题和工作量补充审稿专家', 'MEMBER_MANAGEMENT', 'PROGRAM_CHAIR', 'MANUAL_CONFIRM', 20, 'STAGE_START_TIME', 0, 5, 1, 0
    UNION ALL SELECT 'CHECK_CONFLICTS_OF_INTEREST', '检查利益冲突', '检查作者、机构与审稿人之间的利益冲突', 'SYSTEM_CHECK', 'PROGRAM_CHAIR', 'SYSTEM_CHECK', 30, 'STAGE_START_TIME', 1, 4, 1, 0
    UNION ALL SELECT 'ASSIGN_REVIEWERS', '分派论文评审任务', '按研究方向、负载和利益冲突为论文分派审稿人', 'ASSIGNMENT', 'PROGRAM_CHAIR', 'MANUAL_CONFIRM', 40, 'STAGE_START_TIME', 2, 7, 1, 1
    UNION ALL SELECT 'SEND_REVIEW_INVITATIONS', '发送审稿邀请', '向已分派的审稿人发送邀请和评审说明', 'EMAIL', 'PROGRAM_CHAIR', 'MANUAL_CONFIRM', 50, 'STAGE_START_TIME', 3, 8, 1, 0
    UNION ALL SELECT 'MONITOR_REVIEW_PROGRESS', '跟踪评审进度', '持续检查评审完成率、拒审和超时情况', 'DATA_MONITOR', 'PROGRAM_CHAIR', 'MANUAL_CONFIRM', 60, 'STAGE_START_TIME', 7, 28, 0, 0
    UNION ALL SELECT 'SEND_REVIEW_REMINDERS', '发送评审提醒', '对临近截止且未完成的审稿人发送提醒', 'EMAIL', 'SECRETARY', 'MANUAL_CONFIRM', 70, 'STAGE_END_TIME', -14, -7, 0, 0
    UNION ALL SELECT 'MANAGE_REBUTTAL', '组织作者答辩与复议', '开放作者回复并汇总争议论文', 'COMMUNICATION', 'PROGRAM_CHAIR', 'MANUAL_CONFIRM', 80, 'STAGE_END_TIME', -14, -7, 0, 0
    UNION ALL SELECT 'HOLD_DECISION_MEETING', '召开录用决策会议', '汇总评审意见并形成录用建议', 'DECISION', 'GENERAL_CHAIR', 'MANUAL_CONFIRM', 90, 'STAGE_END_TIME', -7, -3, 1, 1
    UNION ALL SELECT 'RECORD_ACCEPTANCE_DECISIONS', '确认并录入录用结果', '完成录用、拒稿及候补结果录入', 'SYSTEM_OPERATION', 'PROGRAM_CHAIR', 'SYSTEM_CHECK', 100, 'STAGE_END_TIME', -5, -2, 1, 1
    UNION ALL SELECT 'PREPARE_DECISION_EMAILS', '生成录用结果邮件', '按论文结果生成作者通知邮件草稿', 'EMAIL', 'SECRETARY', 'MANUAL_CONFIRM', 110, 'STAGE_END_TIME', -4, -1, 0, 1
    UNION ALL SELECT 'SEND_DECISION_NOTICES', '发送录用结果通知', '审核后向所有投稿作者发送评审结果', 'EMAIL', 'GENERAL_CHAIR', 'MANUAL_CONFIRM', 120, 'STAGE_END_TIME', 0, 0, 1, 1
    UNION ALL SELECT 'GENERATE_REVIEW_REPORT', '生成评审阶段报告', '统计投稿、评审、录用率和异常处理情况', 'REPORT', 'PROGRAM_CHAIR', 'MANUAL_CONFIRM', 130, 'STAGE_END_TIME', 0, 2, 0, 0
) v ON s.stage_code = 'REVIEW'
ON DUPLICATE KEY UPDATE
    task_name=VALUES(task_name), task_desc=VALUES(task_desc), task_type=VALUES(task_type),
    default_role=VALUES(default_role), completion_type=VALUES(completion_type), sort_order=VALUES(sort_order),
    offset_base=VALUES(offset_base), start_offset_days=VALUES(start_offset_days), end_offset_days=VALUES(end_offset_days),
    is_core=VALUES(is_core), need_review=VALUES(need_review), status=1, update_time=NOW();

INSERT INTO conf_task_def
    (stage_def_id, task_code, task_name, task_desc, task_type, default_role, completion_type,
     sort_order, offset_base, start_offset_days, end_offset_days, is_core, need_review, status,
     create_time, update_time)
SELECT s.id, v.task_code, v.task_name, v.task_desc, v.task_type, v.default_role, v.completion_type,
       v.sort_order, v.offset_base, v.start_offset_days, v.end_offset_days, v.is_core, v.need_review, 1,
       NOW(), NOW()
FROM conf_stage_def s
JOIN (
    SELECT 'OPEN_REGISTRATION' task_code, '开放作者注册通道' task_name, '配置注册类别、费用、支付和发票规则' task_desc, 'SYSTEM_CONFIG' task_type, 'REGISTRATION_CHAIR' default_role, 'MANUAL_CONFIRM' completion_type, 10 sort_order, 'STAGE_START_TIME' offset_base, 0 start_offset_days, 2 end_offset_days, 1 is_core, 1 need_review
    UNION ALL SELECT 'PUBLISH_REGISTRATION_GUIDE', '发布注册与缴费指南', '在会议网站发布注册流程、费用和退款说明', 'WEBSITE', 'PUBLICITY_CHAIR', 'MANUAL_CONFIRM', 20, 'STAGE_START_TIME', 0, 3, 1, 1
    UNION ALL SELECT 'COLLECT_CAMERA_READY_PAPERS', '收集论文终稿', '跟踪作者终稿提交并处理缺失材料', 'DATA_MONITOR', 'PUBLICATION_CHAIR', 'MANUAL_CONFIRM', 30, 'STAGE_START_TIME', 1, 15, 1, 0
    UNION ALL SELECT 'VERIFY_CAMERA_READY_FORMAT', '检查终稿格式', '检查终稿页数、模板、作者和元数据', 'MANUAL_REVIEW', 'PUBLICATION_CHAIR', 'MANUAL_CONFIRM', 40, 'STAGE_START_TIME', 5, 18, 1, 0
    UNION ALL SELECT 'COLLECT_COPYRIGHT_FORMS', '收集出版授权文件', '跟踪版权协议或出版授权文件', 'FILE_UPLOAD', 'PUBLICATION_CHAIR', 'FILE_UPLOAD', 50, 'STAGE_START_TIME', 5, 20, 1, 0
    UNION ALL SELECT 'TRACK_AUTHOR_REGISTRATION', '跟踪作者注册状态', '核对每篇录用论文至少一位作者完成注册', 'DATA_MONITOR', 'REGISTRATION_CHAIR', 'SYSTEM_CHECK', 60, 'STAGE_START_TIME', 3, 25, 1, 0
    UNION ALL SELECT 'VERIFY_REGISTRATION_PAYMENTS', '核对注册缴费与发票', '核对支付结果并处理异常订单和发票需求', 'FINANCE', 'FINANCE_OFFICER', 'MANUAL_CONFIRM', 70, 'STAGE_START_TIME', 3, 25, 0, 0
    UNION ALL SELECT 'SEND_REGISTRATION_REMINDERS', '发送注册缴费提醒', '向尚未完成注册的录用作者发送提醒', 'EMAIL', 'REGISTRATION_CHAIR', 'MANUAL_CONFIRM', 80, 'STAGE_END_TIME', -14, -3, 0, 0
    UNION ALL SELECT 'CLOSE_EARLY_BIRD_REGISTRATION', '关闭早鸟注册', '切换注册价格并保存早鸟阶段统计', 'SYSTEM_OPERATION', 'REGISTRATION_CHAIR', 'MANUAL_CONFIRM', 90, 'STAGE_END_TIME', 0, 0, 0, 0
    UNION ALL SELECT 'CONFIRM_PRESENTER_LIST', '确认论文报告人名单', '确认报告人、报告方式和缺席安排', 'DATA_REVIEW', 'PROGRAM_CHAIR', 'MANUAL_CONFIRM', 100, 'STAGE_END_TIME', -5, 0, 1, 1
    UNION ALL SELECT 'GENERATE_REGISTRATION_REPORT', '生成注册阶段报告', '统计注册、缴费、国家地区和人员类型', 'REPORT', 'REGISTRATION_CHAIR', 'MANUAL_CONFIRM', 110, 'STAGE_END_TIME', 0, 3, 0, 0
) v ON s.stage_code = 'REGISTRATION'
ON DUPLICATE KEY UPDATE
    task_name=VALUES(task_name), task_desc=VALUES(task_desc), task_type=VALUES(task_type),
    default_role=VALUES(default_role), completion_type=VALUES(completion_type), sort_order=VALUES(sort_order),
    offset_base=VALUES(offset_base), start_offset_days=VALUES(start_offset_days), end_offset_days=VALUES(end_offset_days),
    is_core=VALUES(is_core), need_review=VALUES(need_review), status=1, update_time=NOW();

INSERT INTO conf_task_def
    (stage_def_id, task_code, task_name, task_desc, task_type, default_role, completion_type,
     sort_order, offset_base, start_offset_days, end_offset_days, is_core, need_review, status,
     create_time, update_time)
SELECT s.id, v.task_code, v.task_name, v.task_desc, v.task_type, v.default_role, v.completion_type,
       v.sort_order, v.offset_base, v.start_offset_days, v.end_offset_days, v.is_core, v.need_review, 1,
       NOW(), NOW()
FROM conf_stage_def s
JOIN (
    SELECT 'DESIGN_CONFERENCE_PROGRAM' task_code, '设计会议议程框架' task_name, '确定分会场、主题论坛和时间分配' task_desc, 'PROGRAM' task_type, 'PROGRAM_CHAIR' default_role, 'MANUAL_CONFIRM' completion_type, 10 sort_order, 'STAGE_START_TIME' offset_base, 0 start_offset_days, 14 end_offset_days, 1 is_core, 1 need_review
    UNION ALL SELECT 'CONFIRM_KEYNOTE_SPEAKERS', '确认主旨报告嘉宾', '完成嘉宾邀请、报告题目和行程确认', 'COMMUNICATION', 'GENERAL_CHAIR', 'MANUAL_CONFIRM', 20, 'STAGE_START_TIME', 0, 20, 1, 1
    UNION ALL SELECT 'COLLECT_PRESENTER_MATERIALS', '收集报告人与演讲材料', '收集简介、照片、演讲题目和授权材料', 'FILE_UPLOAD', 'PROGRAM_CHAIR', 'FILE_UPLOAD', 30, 'STAGE_START_TIME', 7, 30, 0, 0
    UNION ALL SELECT 'SCHEDULE_TECHNICAL_SESSIONS', '编排技术分会场', '按照主题和报告形式完成论文排期', 'PROGRAM', 'PROGRAM_CHAIR', 'MANUAL_CONFIRM', 40, 'STAGE_START_TIME', 7, 30, 1, 1
    UNION ALL SELECT 'CONFIRM_VENUE_AND_ROOMS', '确认场地与分会场', '锁定会场、容量、动线和设备配置', 'LOGISTICS', 'LOCAL_CHAIR', 'MANUAL_CONFIRM', 50, 'STAGE_END_TIME', -45, -30, 1, 1
    UNION ALL SELECT 'ARRANGE_CATERING_AND_HOTELS', '安排餐饮与住宿', '确认茶歇、餐饮、推荐酒店和交通信息', 'LOGISTICS', 'LOCAL_CHAIR', 'MANUAL_CONFIRM', 60, 'STAGE_END_TIME', -30, -7, 0, 0
    UNION ALL SELECT 'PROCESS_VISA_INVITATION_LETTERS', '处理签证邀请函', '审核注册信息并出具参会邀请文件', 'DOCUMENT', 'SECRETARY', 'MANUAL_CONFIRM', 70, 'STAGE_END_TIME', -45, -14, 0, 0
    UNION ALL SELECT 'PREPARE_CONFERENCE_MATERIALS', '准备会议物料', '制作胸卡、指引、签到资料和会场物料', 'LOGISTICS', 'LOCAL_CHAIR', 'FILE_UPLOAD', 80, 'STAGE_END_TIME', -21, -3, 1, 0
    UNION ALL SELECT 'PUBLISH_FINAL_PROGRAM', '发布最终版会议日程', '审核后在网站发布最终议程和报告安排', 'WEBSITE', 'PUBLICITY_CHAIR', 'MANUAL_CONFIRM', 90, 'STAGE_END_TIME', -14, -7, 1, 1
    UNION ALL SELECT 'SEND_ATTENDEE_GUIDE', '发送参会指南', '向已注册人员发送交通、签到和日程说明', 'EMAIL', 'SECRETARY', 'MANUAL_CONFIRM', 100, 'STAGE_END_TIME', -7, -3, 0, 1
    UNION ALL SELECT 'CONDUCT_SYSTEM_REHEARSAL', '完成会议系统与设备彩排', '验证直播、投影、音频、网络和签到系统', 'SYSTEM_CHECK', 'LOCAL_CHAIR', 'SYSTEM_CHECK', 110, 'STAGE_END_TIME', -5, -2, 1, 0
    UNION ALL SELECT 'FINAL_READINESS_CHECK', '执行会前最终检查', '按清单确认人员、场地、议程、物料和应急预案', 'SYSTEM_CHECK', 'GENERAL_CHAIR', 'SYSTEM_CHECK', 120, 'STAGE_END_TIME', -2, -1, 1, 1
) v ON s.stage_code = 'CONFERENCE_PREPARATION'
ON DUPLICATE KEY UPDATE
    task_name=VALUES(task_name), task_desc=VALUES(task_desc), task_type=VALUES(task_type),
    default_role=VALUES(default_role), completion_type=VALUES(completion_type), sort_order=VALUES(sort_order),
    offset_base=VALUES(offset_base), start_offset_days=VALUES(start_offset_days), end_offset_days=VALUES(end_offset_days),
    is_core=VALUES(is_core), need_review=VALUES(need_review), status=1, update_time=NOW();

INSERT INTO conf_task_def
    (stage_def_id, task_code, task_name, task_desc, task_type, default_role, completion_type,
     sort_order, offset_base, start_offset_days, end_offset_days, is_core, need_review, status,
     create_time, update_time)
SELECT s.id, v.task_code, v.task_name, v.task_desc, v.task_type, v.default_role, v.completion_type,
       v.sort_order, v.offset_base, v.start_offset_days, v.end_offset_days, v.is_core, v.need_review, 1,
       NOW(), NOW()
FROM conf_stage_def s
JOIN (
    SELECT 'OPEN_ONSITE_REGISTRATION' task_code, '开放现场签到' task_name, '启用签到台并处理现场注册问题' task_desc, 'ONSITE' task_type, 'REGISTRATION_CHAIR' default_role, 'MANUAL_CONFIRM' completion_type, 10 sort_order, 'STAGE_START_TIME' offset_base, 0 start_offset_days, 0 end_offset_days, 1 is_core, 0 need_review
    UNION ALL SELECT 'RUN_OPENING_CEREMONY', '执行开幕式流程', '按议程完成致辞、介绍和开幕环节', 'ONSITE', 'GENERAL_CHAIR', 'MANUAL_CONFIRM', 20, 'STAGE_START_TIME', 0, 0, 1, 0
    UNION ALL SELECT 'OPERATE_TECHNICAL_SESSIONS', '运行技术分会场', '协调主持人、报告人、计时和问答', 'ONSITE', 'PROGRAM_CHAIR', 'MANUAL_CONFIRM', 30, 'STAGE_START_TIME', 0, 3, 1, 0
    UNION ALL SELECT 'SUPPORT_SPEAKERS', '支持嘉宾与报告人', '处理演讲材料、设备和临时变更', 'ONSITE', 'PROGRAM_CHAIR', 'MANUAL_CONFIRM', 40, 'STAGE_START_TIME', 0, 3, 0, 0
    UNION ALL SELECT 'OPERATE_VENUE_SERVICES', '运行会场保障', '保障场地、餐饮、交通、网络和应急支持', 'ONSITE', 'LOCAL_CHAIR', 'MANUAL_CONFIRM', 50, 'STAGE_START_TIME', 0, 3, 1, 0
    UNION ALL SELECT 'HANDLE_ATTENDEE_SUPPORT', '处理参会者服务请求', '集中响应签到、日程、发票和会场咨询', 'COMMUNICATION', 'SECRETARY', 'MANUAL_CONFIRM', 60, 'STAGE_START_TIME', 0, 3, 0, 0
    UNION ALL SELECT 'TRACK_DAILY_ATTENDANCE', '统计每日签到与参会情况', '形成每日签到、会场容量和缺席记录', 'DATA_MONITOR', 'REGISTRATION_CHAIR', 'MANUAL_CONFIRM', 70, 'STAGE_START_TIME', 0, 3, 0, 0
    UNION ALL SELECT 'RUN_CLOSING_CEREMONY', '执行闭幕式与奖项发布', '完成总结、奖项发布和下一届会议预告', 'ONSITE', 'GENERAL_CHAIR', 'MANUAL_CONFIRM', 80, 'STAGE_END_TIME', 0, 0, 1, 0
) v ON s.stage_code = 'CONFERENCE_DAYS'
ON DUPLICATE KEY UPDATE
    task_name=VALUES(task_name), task_desc=VALUES(task_desc), task_type=VALUES(task_type),
    default_role=VALUES(default_role), completion_type=VALUES(completion_type), sort_order=VALUES(sort_order),
    offset_base=VALUES(offset_base), start_offset_days=VALUES(start_offset_days), end_offset_days=VALUES(end_offset_days),
    is_core=VALUES(is_core), need_review=VALUES(need_review), status=1, update_time=NOW();

INSERT INTO conf_task_def
    (stage_def_id, task_code, task_name, task_desc, task_type, default_role, completion_type,
     sort_order, offset_base, start_offset_days, end_offset_days, is_core, need_review, status,
     create_time, update_time)
SELECT s.id, v.task_code, v.task_name, v.task_desc, v.task_type, v.default_role, v.completion_type,
       v.sort_order, v.offset_base, v.start_offset_days, v.end_offset_days, v.is_core, v.need_review, 1,
       NOW(), NOW()
FROM conf_stage_def s
JOIN (
    SELECT 'SEND_THANK_YOU_EMAILS' task_code, '发送会后感谢邮件' task_name, '向参会者、嘉宾、委员和志愿者发送感谢邮件' task_desc, 'EMAIL' task_type, 'SECRETARY' default_role, 'MANUAL_CONFIRM' completion_type, 10 sort_order, 'STAGE_START_TIME' offset_base, 0 start_offset_days, 3 end_offset_days, 0 is_core, 1 need_review
    UNION ALL SELECT 'COLLECT_PARTICIPANT_FEEDBACK', '收集参会反馈', '发送问卷并汇总议程、会务和场地反馈', 'SURVEY', 'SECRETARY', 'MANUAL_CONFIRM', 20, 'STAGE_START_TIME', 0, 14, 0, 0
    UNION ALL SELECT 'ISSUE_CERTIFICATES', '发放参会与报告证书', '核对签到和报告记录后生成电子证书', 'DOCUMENT', 'SECRETARY', 'MANUAL_CONFIRM', 30, 'STAGE_START_TIME', 2, 14, 0, 0
    UNION ALL SELECT 'PUBLISH_CONFERENCE_RECAP', '发布会议新闻与回顾', '发布会议照片、数据和成果摘要', 'WEBSITE', 'PUBLICITY_CHAIR', 'MANUAL_CONFIRM', 40, 'STAGE_START_TIME', 3, 10, 0, 1
    UNION ALL SELECT 'PUBLISH_PROCEEDINGS', '完成论文集出版与发布', '提交最终出版材料并核对检索信息', 'PUBLICATION', 'PUBLICATION_CHAIR', 'MANUAL_CONFIRM', 50, 'STAGE_START_TIME', 1, 30, 1, 1
    UNION ALL SELECT 'SETTLE_CONFERENCE_FINANCES', '完成会议财务结算', '核对收入、支出、报销、税务和供应商付款', 'FINANCE', 'FINANCE_OFFICER', 'MANUAL_CONFIRM', 60, 'STAGE_START_TIME', 1, 21, 1, 1
    UNION ALL SELECT 'FOLLOW_UP_SPONSORS', '完成赞助商回访', '交付赞助权益报告并完成合作回访', 'COMMUNICATION', 'GENERAL_CHAIR', 'MANUAL_CONFIRM', 70, 'STAGE_START_TIME', 7, 30, 0, 0
    UNION ALL SELECT 'ARCHIVE_CONFERENCE_MATERIALS', '归档会议资料与数据', '归档论文、评审、注册、财务、邮件和现场资料', 'ARCHIVE', 'SECRETARY', 'FILE_UPLOAD', 80, 'STAGE_START_TIME', 7, 30, 1, 0
    UNION ALL SELECT 'PREPARE_FINAL_CONFERENCE_REPORT', '编制会议总结报告', '汇总学术质量、参会数据、财务与改进建议', 'REPORT', 'GENERAL_CHAIR', 'MANUAL_CONFIRM', 90, 'STAGE_START_TIME', 14, 45, 1, 1
    UNION ALL SELECT 'CLOSE_CONFERENCE_PROJECT', '完成会议项目关闭', '确认所有核心事务完成并将会议归档', 'SYSTEM_CHECK', 'GENERAL_CHAIR', 'SYSTEM_CHECK', 100, 'STAGE_END_TIME', -5, 0, 1, 1
) v ON s.stage_code = 'POST_CONFERENCE'
ON DUPLICATE KEY UPDATE
    task_name=VALUES(task_name), task_desc=VALUES(task_desc), task_type=VALUES(task_type),
    default_role=VALUES(default_role), completion_type=VALUES(completion_type), sort_order=VALUES(sort_order),
    offset_base=VALUES(offset_base), start_offset_days=VALUES(start_offset_days), end_offset_days=VALUES(end_offset_days),
    is_core=VALUES(is_core), need_review=VALUES(need_review), status=1, update_time=NOW();

-- 所有模板就绪后再次执行幂等补齐，覆盖后续五个阶段。
INSERT INTO conf_task
    (conference_id, stage_id, task_def_id, stage_code, task_code, task_name, task_desc, task_type,
     principal_role, planned_start_time, planned_end_time, actual_start_time, task_status, priority,
     risk_level, is_core, completion_type, need_review, sort_order, create_time, update_time)
SELECT q.conference_id, q.stage_id, q.task_def_id, q.stage_code, q.task_code, q.task_name, q.task_desc, q.task_type,
       q.principal_role, q.planned_start_time, q.planned_end_time,
       CASE WHEN q.planned_start_time <= NOW() THEN NOW() ELSE NULL END,
       CASE WHEN q.planned_start_time <= NOW() THEN 'IN_PROGRESS' ELSE 'NOT_STARTED' END,
       'MEDIUM', CASE WHEN q.planned_end_time < NOW() THEN 'OVERDUE' ELSE 'NORMAL' END,
       q.is_core, q.completion_type, q.need_review, q.sort_order, NOW(), NOW()
FROM (
    SELECT c.id conference_id, si.id stage_id, d.id task_def_id, sd.stage_code, d.task_code,
           d.task_name, d.task_desc, d.task_type,
           COALESCE(NULLIF(d.default_role, ''), 'GENERAL_CHAIR') principal_role,
           CASE WHEN d.start_offset_days IS NULL THEN si.planned_start_time ELSE DATE_ADD(
               COALESCE(CASE d.offset_base
                   WHEN 'CONFERENCE_CREATE_TIME' THEN c.create_time
                   WHEN 'PAPER_SUBMISSION_DEADLINE' THEN c.paper_submission_deadline
                   WHEN 'NOTIFICATION_OF_ACCEPTANCE' THEN c.notification_of_acceptance
                   WHEN 'CAMERA_READY_SUBMISSION' THEN c.camera_ready_submission
                   WHEN 'EARLY_BIRD_REGISTRATION' THEN c.early_bird_registration
                   WHEN 'CONFERENCE_START_DATE' THEN c.conference_start_date
                   WHEN 'CONFERENCE_END_DATE' THEN c.conference_end_date
                   WHEN 'STAGE_END_TIME' THEN si.planned_end_time
                   ELSE si.planned_start_time END, si.planned_start_time),
               INTERVAL d.start_offset_days DAY) END planned_start_time,
           CASE WHEN d.end_offset_days IS NULL THEN si.planned_end_time ELSE DATE_ADD(
               COALESCE(CASE d.offset_base
                   WHEN 'CONFERENCE_CREATE_TIME' THEN c.create_time
                   WHEN 'PAPER_SUBMISSION_DEADLINE' THEN c.paper_submission_deadline
                   WHEN 'NOTIFICATION_OF_ACCEPTANCE' THEN c.notification_of_acceptance
                   WHEN 'CAMERA_READY_SUBMISSION' THEN c.camera_ready_submission
                   WHEN 'EARLY_BIRD_REGISTRATION' THEN c.early_bird_registration
                   WHEN 'CONFERENCE_START_DATE' THEN c.conference_start_date
                   WHEN 'CONFERENCE_END_DATE' THEN c.conference_end_date
                   WHEN 'STAGE_START_TIME' THEN si.planned_start_time
                   ELSE si.planned_end_time END, si.planned_end_time),
               INTERVAL d.end_offset_days DAY) END planned_end_time,
           COALESCE(d.is_core, 0) is_core,
           COALESCE(NULLIF(d.completion_type, ''), 'MANUAL_CONFIRM') completion_type,
           COALESCE(d.need_review, 0) need_review, COALESCE(d.sort_order, 0) sort_order
    FROM conferences c
    JOIN conf_stage si ON si.conference_id = c.id
    JOIN conf_stage_def sd ON sd.id = si.stage_def_id AND sd.status = 1
    JOIN conf_task_def d ON d.stage_def_id = sd.id AND d.status = 1
    WHERE c.del_flag = 0 AND c.setup_status = 'TASK_GENERATED'
) q
ON DUPLICATE KEY UPDATE
    stage_id=VALUES(stage_id), task_def_id=VALUES(task_def_id), stage_code=VALUES(stage_code),
    task_name=VALUES(task_name), task_desc=VALUES(task_desc), task_type=VALUES(task_type),
    principal_role=VALUES(principal_role), planned_start_time=VALUES(planned_start_time),
    planned_end_time=VALUES(planned_end_time), is_core=VALUES(is_core),
    completion_type=VALUES(completion_type), need_review=VALUES(need_review),
    sort_order=VALUES(sort_order), update_time=NOW();

-- END conf_workflow_task_seed.sql
SET FOREIGN_KEY_CHECKS=1;
