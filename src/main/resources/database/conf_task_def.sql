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