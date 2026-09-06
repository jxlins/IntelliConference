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
