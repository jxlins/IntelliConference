package com.jxl.ai.intelliconf.scheduler;

import com.jxl.ai.intelliconf.service.TaskMailAutomationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskMailAutomationScheduler {
    private final TaskMailAutomationService service;

    @Scheduled(cron = "30 * * * * ?")
    public void generateAndSendTaskMail() {
        int generated = service.generateUpcomingPlans();
        if (generated > 0) log.info("Generated upcoming task mail plans, count={}", generated);
        service.sendApprovedDuePlans();
    }
}
