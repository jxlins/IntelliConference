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