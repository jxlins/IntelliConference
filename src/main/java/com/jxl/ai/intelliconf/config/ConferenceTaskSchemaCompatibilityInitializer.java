package com.jxl.ai.intelliconf.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConferenceTaskSchemaCompatibilityInitializer implements ApplicationRunner {

    private static final Map<String, String> LEGACY_ENGLISH_TASKS = Map.ofEntries(
            Map.entry("CONFIG_MAIL_ACCOUNT", "Configure official mail account"),
            Map.entry("COMPLETE_BASIC_INFO", "Complete conference basic information"),
            Map.entry("GENERATE_PROCESS_TASK_PLAN", "Generate full conference task plan"),
            Map.entry("SET_KEY_MILESTONES", "Set key conference timeline milestones"),
            Map.entry("BUILD_COMMITTEE", "Build conference committee"),
            Map.entry("PUBLISH_CFP", "Publish call for papers"),
            Map.entry("CONFIG_SUBMISSION", "Configure submission settings"),
            Map.entry("MONITOR_SUBMISSION", "Monitor paper submissions"),
            Map.entry("SCREEN_FORMAT", "Screen paper format and eligibility"),
            Map.entry("ASSIGN_REVIEWERS", "Assign reviewers"),
            Map.entry("SEND_REVIEW_REMINDER", "Send review reminders"),
            Map.entry("OPEN_REBUTTAL", "Open rebuttal stage"),
            Map.entry("SEND_DECISION_NOTICE", "Send acceptance and rejection notices"),
            Map.entry("COLLECT_CAMERA_READY", "Collect camera-ready papers"),
            Map.entry("REMIND_REGISTRATION", "Remind registration and payment"),
            Map.entry("ARRANGE_PROGRAM", "Arrange conference program"),
            Map.entry("PREPARE_LOGISTICS", "Prepare venue and logistics"),
            Map.entry("RUN_CONFERENCE", "Run conference onsite process"),
            Map.entry("POST_CONFERENCE_WRAPUP", "Complete post-conference wrap-up")
    );

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        ensureColumn("milestone", "pre_node_code", "ALTER TABLE milestone ADD COLUMN pre_node_code varchar(100) NULL COMMENT 'previous milestone node code' AFTER node_name");
        ensureColumn("milestone", "sort_order", "ALTER TABLE milestone ADD COLUMN sort_order int NULL DEFAULT 0 COMMENT '阶段排序'");
        ensureColumn("milestone", "is_required", "ALTER TABLE milestone ADD COLUMN is_required tinyint(1) NULL DEFAULT 1 COMMENT '是否必经阶段'");
        ensureColumn("milestone", "auto_generate_tasks", "ALTER TABLE milestone ADD COLUMN auto_generate_tasks tinyint(1) NULL DEFAULT 1 COMMENT '是否自动生成事务'");
        ensureColumn("milestone", "created_at", "ALTER TABLE milestone ADD COLUMN created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP");
        ensureColumn("milestone", "updated_at", "ALTER TABLE milestone ADD COLUMN updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");

        ensureColumn("conf_task_def", "task_code", "ALTER TABLE conf_task_def ADD COLUMN task_code varchar(100) NULL COMMENT '标准事务编码'");
        ensureColumn("conf_task_def", "node_code", "ALTER TABLE conf_task_def ADD COLUMN node_code varchar(100) NULL COMMENT 'milestone node code' AFTER task_code");
        ensureColumn("conf_task_def", "handler_bean", "ALTER TABLE conf_task_def ADD COLUMN handler_bean varchar(100) NULL COMMENT 'task handler bean' AFTER task_name");
        ensureColumn("conf_task_def", "params", "ALTER TABLE conf_task_def ADD COLUMN params text NULL COMMENT 'task params' AFTER task_type");
        ensureColumn("conf_task_def", "default_assignee_type", "ALTER TABLE conf_task_def ADD COLUMN default_assignee_type varchar(30) NULL DEFAULT 'ROLE'");
        ensureColumn("conf_task_def", "default_assignee_role", "ALTER TABLE conf_task_def ADD COLUMN default_assignee_role varchar(100) NULL");
        ensureColumn("conf_task_def", "task_type", "ALTER TABLE conf_task_def ADD COLUMN task_type varchar(50) NULL DEFAULT 'MANUAL'");
        ensureColumn("conf_task_def", "jump_url", "ALTER TABLE conf_task_def ADD COLUMN jump_url varchar(255) NULL");
        ensureColumn("conf_task_def", "deadline_rule", "ALTER TABLE conf_task_def ADD COLUMN deadline_rule text NULL");
        ensureColumn("conf_task_def", "is_async", "ALTER TABLE conf_task_def ADD COLUMN is_async tinyint NULL DEFAULT 0");
        ensureColumn("conf_task_def", "priority", "ALTER TABLE conf_task_def ADD COLUMN priority int NULL DEFAULT 2");
        ensureColumn("conf_task_def", "sort_order", "ALTER TABLE conf_task_def ADD COLUMN sort_order int NULL DEFAULT 0");
        ensureColumn("conf_task_def", "created_at", "ALTER TABLE conf_task_def ADD COLUMN created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP");
        ensureColumn("conf_task_def", "updated_at", "ALTER TABLE conf_task_def ADD COLUMN updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");

        ensureColumn("conf_task_def", "stage_def_id", "ALTER TABLE conf_task_def ADD COLUMN stage_def_id bigint NULL COMMENT 'conf_stage_def id' FIRST");
        ensureColumn("conf_task_def", "task_desc", "ALTER TABLE conf_task_def ADD COLUMN task_desc text NULL AFTER task_name");
        ensureColumn("conf_task_def", "default_role", "ALTER TABLE conf_task_def ADD COLUMN default_role varchar(100) NULL AFTER task_type");
        ensureColumn("conf_task_def", "offset_base", "ALTER TABLE conf_task_def ADD COLUMN offset_base varchar(100) NULL AFTER sort_order");
        ensureColumn("conf_task_def", "start_offset_days", "ALTER TABLE conf_task_def ADD COLUMN start_offset_days int NULL AFTER offset_base");
        ensureColumn("conf_task_def", "end_offset_days", "ALTER TABLE conf_task_def ADD COLUMN end_offset_days int NULL AFTER start_offset_days");
        ensureColumn("conf_task_def", "is_core", "ALTER TABLE conf_task_def ADD COLUMN is_core tinyint NULL DEFAULT 0 AFTER end_offset_days");
        ensureColumn("conf_task_def", "need_review", "ALTER TABLE conf_task_def ADD COLUMN need_review tinyint NULL DEFAULT 0 AFTER is_core");
        ensureColumn("conf_task_def", "create_time", "ALTER TABLE conf_task_def ADD COLUMN create_time datetime NULL DEFAULT NULL");
        ensureColumn("conf_task_def", "update_time", "ALTER TABLE conf_task_def ADD COLUMN update_time datetime NULL DEFAULT NULL");

        createTableIfMissing("conf_task", """
                CREATE TABLE `conf_task` (
                  `id` bigint NOT NULL AUTO_INCREMENT,
                  `conference_id` bigint NOT NULL,
                  `stage_id` bigint NOT NULL,
                  `task_def_id` bigint DEFAULT NULL,
                  `stage_code` varchar(100) NOT NULL,
                  `task_code` varchar(100) NOT NULL,
                  `task_name` varchar(255) NOT NULL,
                  `task_desc` text,
                  `task_type` varchar(100) DEFAULT NULL,
                  `principal_role` varchar(100) DEFAULT NULL,
                  `principal_user_id` bigint DEFAULT NULL,
                  `principal_name` varchar(100) DEFAULT NULL,
                  `planned_start_time` datetime DEFAULT NULL,
                  `planned_end_time` datetime DEFAULT NULL,
                  `actual_start_time` datetime DEFAULT NULL,
                  `actual_end_time` datetime DEFAULT NULL,
                  `task_status` varchar(50) NOT NULL DEFAULT 'NOT_STARTED',
                  `priority` varchar(50) NOT NULL DEFAULT 'MEDIUM',
                  `risk_level` varchar(50) NOT NULL DEFAULT 'NORMAL',
                  `is_core` tinyint NOT NULL DEFAULT 0,
                  `completion_type` varchar(50) NOT NULL DEFAULT 'MANUAL_CONFIRM',
                  `completion_desc` text,
                  `completion_url` varchar(500) DEFAULT NULL,
                  `completed_by` bigint DEFAULT NULL,
                  `completed_by_name` varchar(100) DEFAULT NULL,
                  `submitted_at` datetime DEFAULT NULL,
                  `reviewed_by` bigint DEFAULT NULL,
                  `reviewed_by_name` varchar(100) DEFAULT NULL,
                  `reviewed_at` datetime DEFAULT NULL,
                  `review_comment` text,
                  `need_review` tinyint NOT NULL DEFAULT 0,
                  `output_desc` text,
                  `sort_order` int NOT NULL DEFAULT 0,
                  `remark` text,
                  `create_user` bigint DEFAULT NULL,
                  `update_user` bigint DEFAULT NULL,
                  `create_time` datetime DEFAULT NULL,
                  `update_time` datetime DEFAULT NULL,
                  PRIMARY KEY (`id`),
                  UNIQUE KEY `uk_conference_task_code` (`conference_id`,`task_code`),
                  KEY `idx_conference_id` (`conference_id`),
                  KEY `idx_stage_id` (`stage_id`),
                  KEY `idx_task_def_id` (`task_def_id`),
                  KEY `idx_stage_code` (`stage_code`),
                  KEY `idx_task_status` (`task_status`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='conference task instances'
                """);
        ensureColumn("conf_task", "completion_type", "ALTER TABLE conf_task ADD COLUMN completion_type varchar(50) NOT NULL DEFAULT 'MANUAL_CONFIRM' AFTER is_core");
        ensureColumn("conf_task", "completion_desc", "ALTER TABLE conf_task ADD COLUMN completion_desc text NULL AFTER completion_type");
        ensureColumn("conf_task", "completion_url", "ALTER TABLE conf_task ADD COLUMN completion_url varchar(500) NULL AFTER completion_desc");
        ensureColumn("conf_task", "completed_by", "ALTER TABLE conf_task ADD COLUMN completed_by bigint NULL AFTER completion_url");
        ensureColumn("conf_task", "completed_by_name", "ALTER TABLE conf_task ADD COLUMN completed_by_name varchar(100) NULL AFTER completed_by");
        ensureColumn("conf_task", "submitted_at", "ALTER TABLE conf_task ADD COLUMN submitted_at datetime NULL AFTER completed_by_name");
        ensureColumn("conf_task", "reviewed_by", "ALTER TABLE conf_task ADD COLUMN reviewed_by bigint NULL AFTER submitted_at");
        ensureColumn("conf_task", "reviewed_by_name", "ALTER TABLE conf_task ADD COLUMN reviewed_by_name varchar(100) NULL AFTER reviewed_by");
        ensureColumn("conf_task", "reviewed_at", "ALTER TABLE conf_task ADD COLUMN reviewed_at datetime NULL AFTER reviewed_by_name");
        ensureColumn("conf_task", "review_comment", "ALTER TABLE conf_task ADD COLUMN review_comment text NULL AFTER reviewed_at");
        ensureColumn("conf_task", "need_review", "ALTER TABLE conf_task ADD COLUMN need_review tinyint NOT NULL DEFAULT 0 AFTER review_comment");
        ensureColumn("conf_task", "output_desc", "ALTER TABLE conf_task ADD COLUMN output_desc text NULL AFTER need_review");
        ensureColumn("conf_task", "sort_order", "ALTER TABLE conf_task ADD COLUMN sort_order int NOT NULL DEFAULT 0 AFTER output_desc");
        ensureColumn("conf_task", "remark", "ALTER TABLE conf_task ADD COLUMN remark text NULL AFTER sort_order");
        ensureColumn("conf_task", "create_user", "ALTER TABLE conf_task ADD COLUMN create_user bigint NULL AFTER remark");
        ensureColumn("conf_task", "update_user", "ALTER TABLE conf_task ADD COLUMN update_user bigint NULL AFTER create_user");
        ensureColumn("conf_task", "create_time", "ALTER TABLE conf_task ADD COLUMN create_time datetime NULL DEFAULT NULL AFTER update_user");
        ensureColumn("conf_task", "update_time", "ALTER TABLE conf_task ADD COLUMN update_time datetime NULL DEFAULT NULL AFTER create_time");
        ensureColumn("conf_task_attachment", "process_status", "ALTER TABLE conf_task_attachment ADD COLUMN process_status varchar(50) NOT NULL DEFAULT 'UNPROCESSED' AFTER uploaded_at");
        ensureColumn("conf_task_attachment", "process_type", "ALTER TABLE conf_task_attachment ADD COLUMN process_type varchar(100) NULL AFTER process_status");
        ensureColumn("conf_task_attachment", "related_batch_id", "ALTER TABLE conf_task_attachment ADD COLUMN related_batch_id bigint NULL AFTER process_type");
        ensureColumn("conf_task_attachment", "process_message", "ALTER TABLE conf_task_attachment ADD COLUMN process_message text NULL AFTER related_batch_id");
        ensureColumn("conf_task_attachment", "process_result", "ALTER TABLE conf_task_attachment ADD COLUMN process_result text NULL AFTER process_message");
        ensureColumn("conf_task_attachment", "total_count", "ALTER TABLE conf_task_attachment ADD COLUMN total_count int NULL AFTER process_result");
        ensureColumn("conf_task_attachment", "success_count", "ALTER TABLE conf_task_attachment ADD COLUMN success_count int NULL AFTER total_count");
        ensureColumn("conf_task_attachment", "failed_count", "ALTER TABLE conf_task_attachment ADD COLUMN failed_count int NULL AFTER success_count");
        ensureColumn("conf_task_attachment", "duplicate_count", "ALTER TABLE conf_task_attachment ADD COLUMN duplicate_count int NULL AFTER failed_count");

        createTableIfMissing("conf_task_instance", """
                CREATE TABLE `conf_task_instance` (
                  `id` bigint NOT NULL AUTO_INCREMENT,
                  `conference_id` bigint NOT NULL,
                  `milestone_id` bigint NOT NULL,
                  `task_def_id` bigint NOT NULL,
                  `node_code` varchar(100) NOT NULL,
                  `task_code` varchar(100) DEFAULT NULL,
                  `task_name` varchar(255) NOT NULL,
                  `task_desc` text,
                  `task_type` varchar(50) DEFAULT 'MANUAL',
                  `handler_bean` varchar(100) DEFAULT NULL,
                  `params` text,
                  `priority` varchar(20) DEFAULT 'MEDIUM',
                  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
                  `due_time` datetime DEFAULT NULL,
                  `jump_url` varchar(255) DEFAULT NULL,
                  `current_handler_id` varchar(100) DEFAULT NULL,
                  `current_handler_name` varchar(100) DEFAULT NULL,
                  `created_by` varchar(100) DEFAULT NULL,
                  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
                  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (`id`),
                  UNIQUE KEY `uk_conf_task_def` (`conference_id`, `task_def_id`),
                  KEY `idx_conf_status` (`conference_id`, `status`),
                  KEY `idx_milestone` (`milestone_id`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会议真实事务实例'
                """);
        createTableIfMissing("conf_task_assignment", """
                CREATE TABLE `conf_task_assignment` (
                  `id` bigint NOT NULL AUTO_INCREMENT,
                  `task_instance_id` bigint NOT NULL,
                  `conference_id` bigint NOT NULL,
                  `assignee_type` varchar(20) NOT NULL,
                  `assignee_user_id` varchar(100) DEFAULT NULL,
                  `assignee_user_name` varchar(100) DEFAULT NULL,
                  `assignee_role_code` varchar(100) DEFAULT NULL,
                  `status` varchar(32) NOT NULL DEFAULT 'ASSIGNED',
                  `assigned_by` varchar(100) DEFAULT NULL,
                  `assigned_at` datetime DEFAULT NULL,
                  `accepted_at` datetime DEFAULT NULL,
                  `completed_at` datetime DEFAULT NULL,
                  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
                  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (`id`),
                  KEY `idx_task` (`task_instance_id`),
                  KEY `idx_conf_role` (`conference_id`, `assignee_role_code`),
                  KEY `idx_conf_user` (`conference_id`, `assignee_user_id`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会议事务分派'
                """);
        createTableIfMissing("conf_task_action_log", """
                CREATE TABLE `conf_task_action_log` (
                  `id` bigint NOT NULL AUTO_INCREMENT,
                  `task_instance_id` bigint DEFAULT NULL,
                  `conference_id` bigint NOT NULL,
                  `operator_id` varchar(100) DEFAULT NULL,
                  `operator_name` varchar(100) DEFAULT NULL,
                  `operator_role_code` varchar(100) DEFAULT NULL,
                  `action_type` varchar(50) NOT NULL,
                  `comment` text,
                  `before_status` varchar(32) DEFAULT NULL,
                  `after_status` varchar(32) DEFAULT NULL,
                  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
                  PRIMARY KEY (`id`),
                  KEY `idx_task_time` (`task_instance_id`, `created_at`),
                  KEY `idx_conf_time` (`conference_id`, `created_at`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会议事务处理日志'
                """);
        ensureVarcharColumn("conf_task_action_log", "operator_id", 100);
        ensureVarcharColumn("conf_task_action_log", "operator_name", 100);
        ensureVarcharColumn("conf_task_action_log", "operator_role_code", 100);
        ensureVarcharColumn("conf_task_assignment", "assignee_user_id", 100);
        ensureVarcharColumn("conf_task_assignment", "assignee_user_name", 100);
        ensureVarcharColumn("conf_task_assignment", "assigned_by", 100);
        ensureVarcharColumn("conf_task_instance", "current_handler_id", 100);
        ensureVarcharColumn("conf_task_instance", "current_handler_name", 100);
        ensureVarcharColumn("conf_task_instance", "created_by", 100);
        ensureNullableColumn("conf_task_action_log", "task_instance_id", "bigint");
        disableLegacyEnglishTaskDefinitions();
        normalizeTaskJumpUrls();
    }

    private void ensureColumn(String tableName, String columnName, String sql) {
        if (!tableExists(tableName) || columnExists(tableName, columnName)) {
            return;
        }
        try {
            jdbcTemplate.execute(sql);
        } catch (Exception ex) {
            log.warn("[ConferenceTaskSchema] add column failed {}.{}: {}", tableName, columnName, ex.getMessage());
        }
    }

    private void createTableIfMissing(String tableName, String createSql) {
        if (tableExists(tableName)) {
            return;
        }
        try {
            jdbcTemplate.execute(createSql);
        } catch (Exception ex) {
            log.warn("[ConferenceTaskSchema] create table failed {}: {}", tableName, ex.getMessage());
        }
    }

    private void ensureVarcharColumn(String tableName, String columnName, int length) {
        if (!tableExists(tableName) || !columnExists(tableName, columnName)) {
            return;
        }
        String dataType = jdbcTemplate.queryForObject(
                "SELECT DATA_TYPE FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
                String.class,
                tableName,
                columnName
        );
        if ("varchar".equalsIgnoreCase(dataType)) {
            return;
        }
        try {
            jdbcTemplate.execute("ALTER TABLE " + tableName + " MODIFY COLUMN " + columnName + " varchar(" + length + ") NULL");
        } catch (Exception ex) {
            log.warn("[ConferenceTaskSchema] modify column to varchar failed {}.{}: {}", tableName, columnName, ex.getMessage());
        }
    }

    private void ensureNullableColumn(String tableName, String columnName, String columnType) {
        if (!tableExists(tableName) || !columnExists(tableName, columnName)) {
            return;
        }
        String nullable = jdbcTemplate.queryForObject(
                "SELECT IS_NULLABLE FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
                String.class,
                tableName,
                columnName
        );
        if ("YES".equalsIgnoreCase(nullable)) {
            return;
        }
        try {
            jdbcTemplate.execute("ALTER TABLE " + tableName + " MODIFY COLUMN " + columnName + " " + columnType + " NULL");
        } catch (Exception ex) {
            log.warn("[ConferenceTaskSchema] modify column nullable failed {}.{}: {}", tableName, columnName, ex.getMessage());
        }
    }

    private void disableLegacyEnglishTaskDefinitions() {
        if (!tableExists("conf_task_def")) {
            return;
        }
        for (Map.Entry<String, String> entry : LEGACY_ENGLISH_TASKS.entrySet()) {
            try {
                jdbcTemplate.update("""
                        UPDATE conf_task_def
                        SET status = 0, updated_at = NOW()
                        WHERE task_code = ? AND task_name = ? AND status <> 0
                        """, entry.getKey(), entry.getValue());
                if (tableExists("conf_task_instance")) {
                    jdbcTemplate.update("""
                            UPDATE conf_task_instance
                            SET status = 'CANCELLED', updated_at = NOW()
                            WHERE task_code = ? AND task_name = ?
                              AND status IN ('PENDING', 'PROCESSING', 'REJECTED', 'OVERDUE')
                            """, entry.getKey(), entry.getValue());
                }
            } catch (Exception ex) {
                log.warn("[ConferenceTaskSchema] disable legacy English task failed {}: {}", entry.getKey(), ex.getMessage());
            }
        }
    }

    private void normalizeTaskJumpUrls() {
        if (!tableExists("conf_task_def")) {
            return;
        }
        jdbcTemplate.update("UPDATE conf_task_def SET jump_url = NULL WHERE task_code <> 'COMPLETE_BASIC_INFO'");
        jdbcTemplate.update("UPDATE conf_task_def SET jump_url = '/conference/basic' WHERE task_code = 'COMPLETE_BASIC_INFO'");
        if (tableExists("conf_task_instance")) {
            jdbcTemplate.update("UPDATE conf_task_instance SET jump_url = NULL WHERE task_code <> 'COMPLETE_BASIC_INFO'");
            jdbcTemplate.update("UPDATE conf_task_instance SET jump_url = '/conference/basic' WHERE task_code = 'COMPLETE_BASIC_INFO'");
        }
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?",
                Integer.class,
                tableName
        );
        return count != null && count > 0;
    }

    private boolean columnExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
                Integer.class,
                tableName,
                columnName
        );
        return count != null && count > 0;
    }
}
