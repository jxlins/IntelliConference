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