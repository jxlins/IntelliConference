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
