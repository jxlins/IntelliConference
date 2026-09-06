package com.jxl.ai.intelliconf.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * Ensures the seven-stage conference workflow has a complete, idempotent task template set.
 */
@Slf4j
@Component
@Order(200)
@RequiredArgsConstructor
public class ConferenceWorkflowTemplateInitializer implements ApplicationRunner {

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        if (!tableExists("conf_stage_def") || !tableExists("conf_task_def")) {
            log.warn("[ConferenceWorkflowTemplate] stage or task template table is missing, skip seed");
            return;
        }
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ClassPathResource("database/conf_workflow_task_seed.sql")
        );
        populator.setSqlScriptEncoding("UTF-8");
        populator.setContinueOnError(false);
        populator.setIgnoreFailedDrops(true);
        populator.execute(dataSource);
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM conf_task_def d JOIN conf_stage_def s ON s.id=d.stage_def_id WHERE d.status=1 AND s.status=1",
                Integer.class
        );
        log.info("[ConferenceWorkflowTemplate] enabled task templates ready, count={}", count);
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=?",
                Integer.class,
                tableName
        );
        return count != null && count > 0;
    }
}
