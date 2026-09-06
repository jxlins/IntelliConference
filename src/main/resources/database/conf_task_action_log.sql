CREATE TABLE `conf_task_action_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_instance_id` bigint DEFAULT NULL COMMENT '事务实例ID',
  `conference_id` bigint NOT NULL COMMENT '会议ID',
  `operator_id` varchar(100) DEFAULT NULL,
  `operator_name` varchar(100) DEFAULT NULL,
  `operator_role_code` varchar(100) DEFAULT NULL,
  `action_type` varchar(50) NOT NULL COMMENT 'CREATE/ASSIGN/START/COMPLETE等',
  `comment` text COMMENT '备注',
  `before_status` varchar(32) DEFAULT NULL,
  `after_status` varchar(32) DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_task_time` (`task_instance_id`, `created_at`),
  KEY `idx_conf_time` (`conference_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会议事务处理日志';
