package com.jxl.ai.intelliconf.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConferenceSetupSchemaCompatibilityInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        ensureColumn("conferences", "setup_status",
                "ALTER TABLE conferences ADD COLUMN setup_status varchar(50) NULL DEFAULT 'BASIC_CREATED' COMMENT 'conference setup status' AFTER current_state");
        ensureColumn("conferences", "paper_submission_deadline",
                "ALTER TABLE conferences ADD COLUMN paper_submission_deadline datetime NULL COMMENT 'paper submission deadline' AFTER end_time");
        ensureColumn("conferences", "notification_of_acceptance",
                "ALTER TABLE conferences ADD COLUMN notification_of_acceptance datetime NULL COMMENT 'notification of acceptance' AFTER paper_submission_deadline");
        ensureColumn("conferences", "camera_ready_submission",
                "ALTER TABLE conferences ADD COLUMN camera_ready_submission datetime NULL COMMENT 'camera-ready submission deadline' AFTER notification_of_acceptance");
        ensureColumn("conferences", "early_bird_registration",
                "ALTER TABLE conferences ADD COLUMN early_bird_registration datetime NULL COMMENT 'early-bird registration deadline' AFTER camera_ready_submission");
        ensureColumn("conferences", "conference_start_date",
                "ALTER TABLE conferences ADD COLUMN conference_start_date datetime NULL COMMENT 'conference start date' AFTER early_bird_registration");
        ensureColumn("conferences", "conference_end_date",
                "ALTER TABLE conferences ADD COLUMN conference_end_date datetime NULL COMMENT 'conference end date' AFTER conference_start_date");
    }

    private void ensureColumn(String tableName, String columnName, String sql) {
        if (!tableExists(tableName) || columnExists(tableName, columnName)) {
            return;
        }
        try {
            jdbcTemplate.execute(sql);
        } catch (Exception ex) {
            log.warn("[ConferenceSetupSchema] add column failed {}.{}: {}", tableName, columnName, ex.getMessage());
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
