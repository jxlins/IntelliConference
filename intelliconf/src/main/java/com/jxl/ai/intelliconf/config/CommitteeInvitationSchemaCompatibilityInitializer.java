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
public class CommitteeInvitationSchemaCompatibilityInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        createTableIfMissing("conf_member_invitation", """
                CREATE TABLE `conf_member_invitation` (
                  `id` bigint NOT NULL AUTO_INCREMENT,
                  `conference_id` bigint NOT NULL,
                  `source_task_id` bigint DEFAULT NULL,
                  `role_def_id` bigint DEFAULT NULL,
                  `role_code` varchar(100) DEFAULT NULL,
                  `role_name` varchar(200) DEFAULT NULL,
                  `committee_type` varchar(100) DEFAULT NULL,
                  `committee_name` varchar(200) DEFAULT NULL,
                  `invitee_name` varchar(200) DEFAULT NULL,
                  `invitee_email` varchar(200) NOT NULL,
                  `invitee_affiliation` varchar(255) DEFAULT NULL,
                  `invitation_token` varchar(200) DEFAULT NULL,
                  `invitation_status` varchar(50) NOT NULL DEFAULT 'DRAFT',
                  `send_count` int DEFAULT 0,
                  `sent_at` datetime DEFAULT NULL,
                  `last_sent_at` datetime DEFAULT NULL,
                  `expired_at` datetime DEFAULT NULL,
                  `accepted_user_id` bigint DEFAULT NULL,
                  `accepted_at` datetime DEFAULT NULL,
                  `declined_at` datetime DEFAULT NULL,
                  `declined_reason` text,
                  `cancelled_at` datetime DEFAULT NULL,
                  `created_at` datetime DEFAULT NULL,
                  `updated_at` datetime DEFAULT NULL,
                  PRIMARY KEY (`id`),
                  KEY `idx_conf_email_role` (`conference_id`,`invitee_email`,`role_code`),
                  KEY `idx_invitation_token` (`invitation_token`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
    }

    private void createTableIfMissing(String tableName, String createSql) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?",
                Integer.class,
                tableName
        );
        if (count != null && count > 0) {
            return;
        }
        try {
            jdbcTemplate.execute(createSql);
        } catch (Exception ex) {
            log.warn("[CommitteeInvitationSchema] create table failed {}: {}", tableName, ex.getMessage());
        }
    }
}
