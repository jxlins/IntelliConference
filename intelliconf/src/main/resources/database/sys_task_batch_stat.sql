CREATE TABLE `sys_task_batch_stat` (
    `task_log_id` bigint PRIMARY KEY COMMENT '关联 sys_task_log 的 ID',
    `total_count` int DEFAULT 0 COMMENT '总条数（如总人数、总稿件数）',
    `success_count` int DEFAULT 0 COMMENT '成功处理数',
    `fail_count` int DEFAULT 0 COMMENT '失败处理数',
    `process_status` tinyint DEFAULT 0 COMMENT '0:待处理, 1:处理中, 2:已完成',
    `last_update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB COMMENT='批量任务执行统计表';