package com.jxl.ai.intelliconf.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Committee 兼容初始化：启动时自动补齐委员会表。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommitteeSchemaCompatibilityInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        createTableIfMissing("conf_committee", """
                CREATE TABLE `conf_committee` (
                    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                    `conf_id` bigint NOT NULL COMMENT '会议ID',
                    `email` varchar(256) NOT NULL COMMENT '委员会成员邮箱',
                    `name` varchar(256) DEFAULT NULL COMMENT '委员会成员姓名',
                    `institution` varchar(256) DEFAULT NULL COMMENT '委员会成员单位',
                    `role` varchar(64) NOT NULL COMMENT '委员会角色 CHAIR/REVIEWER',
                `invite_status` varchar(32) NOT NULL DEFAULT 'INVITED' COMMENT '邀请状态 INVITED/ACCEPTED/DECLINED',
                    `access_token` varchar(64) DEFAULT NULL COMMENT '门户访问令牌(UUID)',
                    `token_expire_time` datetime DEFAULT NULL COMMENT '令牌过期时间',
                    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                    `del_flag` tinyint DEFAULT 0 COMMENT '删除标识 0-有效 1-删除',
                    PRIMARY KEY (`id`),
                    UNIQUE KEY `uk_conf_email` (`conf_id`,`email`),
                    UNIQUE KEY `uk_access_token` (`access_token`),
                KEY `idx_conf_role` (`conf_id`,`role`),
                KEY `idx_conf_status` (`conf_id`,`invite_status`)
                ) ENGINE=InnoDB COMMENT='会议委员会成员表'
                """);

        addColumnIfMissing("conf_committee", "invite_status",
            "ALTER TABLE `conf_committee` ADD COLUMN `invite_status` varchar(32) NOT NULL DEFAULT 'INVITED' COMMENT '邀请状态 INVITED/ACCEPTED/DECLINED' AFTER `role`");
        addColumnIfMissing("conf_committee", "institution",
            "ALTER TABLE `conf_committee` ADD COLUMN `institution` varchar(256) DEFAULT NULL COMMENT '委员会成员单位' AFTER `name`");
        addIndexIfMissing("conf_committee", "idx_conf_status",
            "ALTER TABLE `conf_committee` ADD INDEX `idx_conf_status` (`conf_id`,`invite_status`)");
    }

    private void createTableIfMissing(String tableName, String createSql) {
        if (tableExists(tableName)) {
            return;
        }
        try {
            jdbcTemplate.execute(createSql);
            log.info("[CommitteeSchemaCompatibility] 已自动创建缺失表 {}", tableName);
        } catch (Exception ex) {
            log.warn("[CommitteeSchemaCompatibility] 自动建表失败 {}: {}", tableName, ex.getMessage());
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

    private void addColumnIfMissing(String tableName, String columnName, String alterSql) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
                Integer.class,
                tableName,
                columnName
        );
        if (count != null && count > 0) {
            return;
        }
        try {
            jdbcTemplate.execute(alterSql);
            log.info("[CommitteeSchemaCompatibility] 已自动补齐字段 {}.{}", tableName, columnName);
        } catch (Exception ex) {
            log.warn("[CommitteeSchemaCompatibility] 自动补字段失败 {}.{}: {}", tableName, columnName, ex.getMessage());
        }
    }

    private void addIndexIfMissing(String tableName, String indexName, String alterSql) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?",
                Integer.class,
                tableName,
                indexName
        );
        if (count != null && count > 0) {
            return;
        }
        try {
            jdbcTemplate.execute(alterSql);
            log.info("[CommitteeSchemaCompatibility] 已自动补齐索引 {}.{}", tableName, indexName);
        } catch (Exception ex) {
            log.warn("[CommitteeSchemaCompatibility] 自动补索引失败 {}.{}: {}", tableName, indexName, ex.getMessage());
        }
    }
}
