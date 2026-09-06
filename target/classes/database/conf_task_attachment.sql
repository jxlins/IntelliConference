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
