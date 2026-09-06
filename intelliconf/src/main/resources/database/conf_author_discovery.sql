CREATE TABLE IF NOT EXISTS `conf_author_discovery_job` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `conference_id` bigint NOT NULL COMMENT '会议ID',
  `topic_keywords` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'JSON topic keywords',
  `year_from` int NULL DEFAULT NULL COMMENT '起始发表年份',
  `year_to` int NULL DEFAULT NULL COMMENT '结束发表年份',
  `max_authors` int NULL DEFAULT 200 COMMENT '最大候选作者数',
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'PENDING/RUNNING/COMPLETED/FAILED',
  `total_papers` int NULL DEFAULT 0 COMMENT '论文数量',
  `total_candidates` int NULL DEFAULT 0 COMMENT '候选作者数量',
  `high_confidence_emails` int NULL DEFAULT 0 COMMENT '高可信邮箱数量',
  `error_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '错误信息',
  `created_by` bigint NULL DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `started_at` datetime NULL DEFAULT NULL COMMENT '开始时间',
  `finished_at` datetime NULL DEFAULT NULL COMMENT '结束时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_conf_author_discovery_job_conf` (`conference_id`) USING BTREE,
  KEY `idx_conf_author_discovery_job_status` (`status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

CREATE TABLE IF NOT EXISTS `conf_potential_author` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `conference_id` bigint NOT NULL COMMENT '会议ID',
  `discovery_job_id` bigint NULL DEFAULT NULL COMMENT '发现任务ID',
  `author_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '作者姓名',
  `normalized_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '归一化姓名',
  `email` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '邮箱',
  `organization` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '机构',
  `country_region` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '国家或地区',
  `research_keywords` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '研究关键词JSON',
  `representative_papers` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '代表论文JSON',
  `source_platform` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '来源平台',
  `source_url` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '来源URL',
  `topic_similarity` decimal(6,4) NULL DEFAULT 0 COMMENT '主题相关性',
  `email_confidence` decimal(6,4) NULL DEFAULT 0 COMMENT '邮箱可信度',
  `overall_score` decimal(6,4) NULL DEFAULT 0 COMMENT '综合分',
  `review_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED',
  `contact_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'NOT_CONTACTED' COMMENT '联系状态',
  `created_at` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `updated_at` datetime NULL DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_conf_author_identity` (`conference_id`, `normalized_name`, `organization`) USING BTREE,
  UNIQUE KEY `uk_conf_author_email` (`conference_id`, `email`) USING BTREE,
  KEY `idx_conf_potential_author_review` (`conference_id`, `review_status`) USING BTREE,
  KEY `idx_conf_potential_author_score` (`conference_id`, `overall_score`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

CREATE TABLE IF NOT EXISTS `conf_author_email_source` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `potential_author_id` bigint NULL DEFAULT NULL COMMENT '候选作者ID',
  `email` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '邮箱',
  `source_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'PAPER_PDF/OPEN_ACCESS_PAGE/PUBLISHER_PAGE/AUTHOR_PAGE',
  `source_url` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '来源URL',
  `evidence_text` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '证据文本',
  `confidence` decimal(6,4) NULL DEFAULT 0 COMMENT '可信度',
  `collected_at` datetime NULL DEFAULT NULL COMMENT '采集时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_conf_author_email_source_author` (`potential_author_id`) USING BTREE,
  KEY `idx_conf_author_email_source_email` (`email`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

CREATE TABLE IF NOT EXISTS `conf_email_suppression` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `conference_id` bigint NULL DEFAULT NULL COMMENT '会议ID，空表示全局屏蔽',
  `email` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '邮箱',
  `reason` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'OPT_OUT/BOUNCED/COMPLAINT/MANUAL_BLOCK',
  `created_at` datetime NULL DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_conf_email_suppression` (`conference_id`, `email`) USING BTREE,
  KEY `idx_conf_email_suppression_email` (`email`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;
