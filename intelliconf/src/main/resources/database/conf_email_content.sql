CREATE TABLE `conf_email_content` (
    `task_log_id` bigint PRIMARY KEY COMMENT '关联具体的任务实例ID',
    `conf_id` bigint NOT NULL,
    `subject` varchar(255) COMMENT '邮件主题',
    `content_body` text COMMENT '用户输入的邮件正文',
    `target_role` varchar(50) COMMENT '冗余记录发送目标角色，如 PROSPECT',
    `send_time` datetime COMMENT '计划发送时间，为空默认当前时间',
    `content_status` tinyint DEFAULT 0 COMMENT '0=草稿,1=待发送,2=已发送,3=发送中',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB COMMENT='任务关联邮件内容表';