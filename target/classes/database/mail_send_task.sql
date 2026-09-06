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
