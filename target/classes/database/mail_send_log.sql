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
