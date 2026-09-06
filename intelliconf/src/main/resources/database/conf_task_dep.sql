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