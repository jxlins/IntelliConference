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
