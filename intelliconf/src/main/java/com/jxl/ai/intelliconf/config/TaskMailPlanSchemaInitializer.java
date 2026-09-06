package com.jxl.ai.intelliconf.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(180)
@RequiredArgsConstructor
public class TaskMailPlanSchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS conf_task_mail_plan (
                  id BIGINT NOT NULL AUTO_INCREMENT,
                  conference_id BIGINT NOT NULL,
                  task_id BIGINT NOT NULL,
                  target_role VARCHAR(100) NOT NULL,
                  subject VARCHAR(500) NOT NULL,
                  content_body LONGTEXT NOT NULL,
                  planned_send_time DATETIME NOT NULL,
                  status VARCHAR(32) NOT NULL DEFAULT 'PENDING_REVIEW',
                  recipient_count INT NOT NULL DEFAULT 0,
                  success_count INT NOT NULL DEFAULT 0,
                  fail_count INT NOT NULL DEFAULT 0,
                  generated_by VARCHAR(100) DEFAULT 'SYSTEM',
                  generated_at DATETIME NOT NULL,
                  approved_by VARCHAR(100) DEFAULT NULL,
                  approved_at DATETIME DEFAULT NULL,
                  sent_at DATETIME DEFAULT NULL,
                  error_message TEXT DEFAULT NULL,
                  created_at DATETIME NOT NULL,
                  updated_at DATETIME NOT NULL,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_task_mail_plan (task_id),
                  KEY idx_conf_status_time (conference_id, status, planned_send_time),
                  CONSTRAINT fk_task_mail_plan_task FOREIGN KEY (task_id) REFERENCES conf_task(id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                COMMENT='新版会议任务关联邮件审核与发送计划'
                """);
        addColumnIfMissing("conf_task_mail_plan", "selected_emails",
                "TEXT DEFAULT NULL COMMENT '审核时勾选的收件人邮箱，换行分隔；为空表示发送给全部角色收件人'");
        log.info("[TaskMailPlanSchema] schema ready");
    }

    private void addColumnIfMissing(String table, String column, String definition) {
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                Integer.class, table, column);
        if (exists != null && exists > 0) return;
        jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        log.info("[TaskMailPlanSchema] added column {}.{}", table, column);
    }
}
