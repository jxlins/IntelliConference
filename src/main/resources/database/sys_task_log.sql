CREATE TABLE `sys_task_log` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `confere_id` bigint NOT NULL COMMENT '会议ID',
    `milestone_id` bigint NOT NULL COMMENT '关联的里程碑ID',
    `task_def_id` bigint NOT NULL COMMENT '关联的任务定义ID',
    `handler_bean` varchar(100) COMMENT '执行器的Bean名称',
    `execution_status` tinyint DEFAULT 0 COMMENT '状态: 0-待处理, 1-执行中, 2-成功, 3-失败',
    `retry_count` int DEFAULT 0 COMMENT '重试次数',
    `error_msg` text COMMENT '异常堆栈信息',
    `start_time` datetime DEFAULT NULL COMMENT '开始时间',
    `end_time` datetime DEFAULT NULL COMMENT '结束时间',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    PRIMARY KEY (`id`),
    INDEX `idx_conf_milestone`(`confere_id`, `milestone_id`),
    INDEX `idx_status`(`execution_status`)
) ENGINE=InnoDB COMMENT='自动化任务执行日志表';