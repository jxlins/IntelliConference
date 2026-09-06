CREATE TABLE `milestone` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `confere_id` bigint NOT NULL COMMENT 'conference id, keep legacy column name',
  `node_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'milestone node code',
  `node_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'milestone node name',
  `pre_node_code` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'previous milestone node code',
  `start_date` datetime DEFAULT NULL COMMENT 'planned start time',
  `target_end_date` datetime DEFAULT NULL COMMENT 'planned end time',
  `actual_end_date` datetime DEFAULT NULL COMMENT 'actual completed time',
  `trigger_event` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'legacy FSM trigger event',
  `status` tinyint DEFAULT 0 COMMENT '0 waiting, 1 processing, 2 completed, 3 skipped',
  `remark` varchar(1024) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'remark',
  `is_confirmed` tinyint(1) DEFAULT 0 COMMENT 'legacy timeline confirmed flag',
  `sort_order` int DEFAULT 0 COMMENT 'milestone order',
  `is_required` tinyint(1) DEFAULT 1 COMMENT 'required milestone flag',
  `auto_generate_tasks` tinyint(1) DEFAULT 1 COMMENT 'auto generate task instances flag',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_conf_node` (`confere_id`, `node_code`) USING BTREE,
  KEY `idx_confere_id` (`confere_id`) USING BTREE,
  KEY `idx_conf_status` (`confere_id`, `status`) USING BTREE,
  KEY `idx_conf_sort` (`confere_id`, `sort_order`) USING BTREE,
  KEY `idx_conf_pre_node` (`confere_id`, `pre_node_code`) USING BTREE
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='conference timeline milestone instances';
