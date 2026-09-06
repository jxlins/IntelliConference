package com.jxl.ai.intelliconf.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 兼容历史库结构：启动时补齐 conf_email_content 新增字段，避免旧库因缺列导致查询失败。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailSchemaCompatibilityInitializer implements ApplicationRunner {

    private static final String TABLE_NAME = "conf_email_content";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        createTableIfMissing("conference_mail_account", """
                CREATE TABLE `conference_mail_account` (
                    `id` bigint NOT NULL AUTO_INCREMENT,
                    `conference_id` bigint NOT NULL COMMENT '会议ID',
                    `provider_type` varchar(32) NOT NULL COMMENT 'SMTP/TENCENT_EXMAIL',
                    `from_email` varchar(150) NOT NULL COMMENT '官方发件邮箱',
                    `from_name` varchar(100) DEFAULT NULL COMMENT '发件显示名',
                    `reply_to` varchar(150) DEFAULT NULL COMMENT '回复邮箱',
                    `smtp_host` varchar(150) NOT NULL COMMENT 'SMTP服务器',
                    `smtp_port` int NOT NULL COMMENT 'SMTP端口',
                    `username` varchar(150) NOT NULL COMMENT '邮箱账号',
                    `password_cipher` text NOT NULL COMMENT '加密后的邮箱密码或客户端专用密码',
                    `ssl_enabled` tinyint(1) DEFAULT 1 COMMENT '是否启用SSL',
                    `starttls_enabled` tinyint(1) DEFAULT 0 COMMENT '是否启用STARTTLS',
                    `enabled` tinyint(1) DEFAULT 1 COMMENT '是否启用',
                    `last_test_status` varchar(32) DEFAULT NULL COMMENT '最近一次测试状态',
                    `last_test_time` datetime DEFAULT NULL COMMENT '最近一次测试时间',
                    `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
                    `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    PRIMARY KEY (`id`),
                    UNIQUE KEY `uk_conference_mail_account_conf` (`conference_id`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会议官方邮箱配置'
                """);

        createTableIfMissing("mail_template", """
                CREATE TABLE `mail_template` (
                    `id` bigint NOT NULL AUTO_INCREMENT,
                    `conference_id` bigint NOT NULL COMMENT '会议ID',
                    `scene_code` varchar(64) NOT NULL COMMENT '邮件场景',
                    `template_name` varchar(150) NOT NULL COMMENT '模板名称',
                    `subject_template` varchar(255) NOT NULL COMMENT '主题模板',
                    `html_template` text COMMENT 'HTML正文模板',
                    `text_template` text COMMENT '纯文本正文模板',
                    `enabled` tinyint(1) DEFAULT 1 COMMENT '是否启用',
                    `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
                    `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    PRIMARY KEY (`id`),
                    KEY `idx_conf_scene_enabled` (`conference_id`, `scene_code`, `enabled`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会议邮件模板'
                """);

        createTableIfMissing("mail_send_task", """
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
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='邮件批量发送任务'
                """);

        createTableIfMissing("mail_send_log", """
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
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='逐封邮件发送日志'
                """);

        createTableIfMissing("sys_mail_log", """
                CREATE TABLE `sys_mail_log` (
                    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                    `task_log_id` bigint NOT NULL COMMENT '关联的任务日志ID',
                    `recipient_email` varchar(150) NOT NULL COMMENT '收件人邮箱',
                    `send_status` tinyint NOT NULL COMMENT '发送状态: 1-成功, 0-失败',
                    `error_msg` text COMMENT '失败原因',
                    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
                    PRIMARY KEY (`id`),
                    INDEX `idx_task_status`(`task_log_id`, `send_status`)
                ) ENGINE=InnoDB COMMENT='邮件发送明细日志表'
                """);

        createTableIfMissing("sys_task_batch_stat", """
                CREATE TABLE `sys_task_batch_stat` (
                    `task_log_id` bigint PRIMARY KEY COMMENT '关联 sys_task_log 的 ID',
                    `total_count` int DEFAULT 0 COMMENT '总条数（如总人数、总稿件数）',
                    `success_count` int DEFAULT 0 COMMENT '成功处理数',
                    `fail_count` int DEFAULT 0 COMMENT '失败处理数',
                    `process_status` tinyint DEFAULT 0 COMMENT '0:待处理, 1:处理中, 2:已完成',
                    `last_update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                ) ENGINE=InnoDB COMMENT='批量任务执行统计表'
                """);

        ensureColumnExists("send_time", "ALTER TABLE conf_email_content ADD COLUMN send_time datetime COMMENT '计划发送时间，为空默认当前时间'");
        ensureColumnExists("content_status", "ALTER TABLE conf_email_content ADD COLUMN content_status tinyint DEFAULT 0 COMMENT '0=草稿,1=待发送,2=已发送,3=发送中'");

        // 对历史数据兜底：未初始化状态统一视为草稿。
        try {
            jdbcTemplate.update("UPDATE conf_email_content SET content_status = 0 WHERE content_status IS NULL");
        } catch (Exception ex) {
            log.warn("[EmailSchemaCompatibility] 初始化 content_status 默认值失败: {}", ex.getMessage());
        }
    }

    private void ensureColumnExists(String columnName, String alterSql) {
        if (!tableExists(TABLE_NAME)) {
            return;
        }
        if (columnExists(TABLE_NAME, columnName)) {
            return;
        }
        try {
            jdbcTemplate.execute(alterSql);
            log.info("[EmailSchemaCompatibility] 已自动补齐字段 {}.{}", TABLE_NAME, columnName);
        } catch (Exception ex) {
            log.warn("[EmailSchemaCompatibility] 自动补字段失败 {}.{}: {}", TABLE_NAME, columnName, ex.getMessage());
        }
    }

    private void createTableIfMissing(String tableName, String createSql) {
        if (tableExists(tableName)) {
            return;
        }
        try {
            jdbcTemplate.execute(createSql);
            log.info("[EmailSchemaCompatibility] 已自动创建缺失表 {}", tableName);
        } catch (Exception ex) {
            log.warn("[EmailSchemaCompatibility] 自动建表失败 {}: {}", tableName, ex.getMessage());
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
