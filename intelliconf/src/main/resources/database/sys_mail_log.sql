CREATE TABLE `sys_mail_log` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `task_log_id` bigint NOT NULL COMMENT '关联的任务日志ID',
    `recipient_email` varchar(150) NOT NULL COMMENT '收件人邮箱',
    `send_status` tinyint NOT NULL COMMENT '发送状态: 1-成功, 0-失败',
    `error_msg` text COMMENT '失败原因',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    PRIMARY KEY (`id`),
    INDEX `idx_task_status`(`task_log_id`, `send_status`)
) ENGINE=InnoDB COMMENT='邮件发送明细日志表';
