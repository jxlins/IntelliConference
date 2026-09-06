package com.jxl.ai.intelliconf.scheduler;

import com.jxl.ai.intelliconf.service.EmailTaskDispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailTaskScheduler {

    private final EmailTaskDispatchService emailTaskDispatchService;

    @Scheduled(cron = "0 */1 * * * ?")
    public void dispatchEmailTasks() {
        emailTaskDispatchService.dispatchDueEmailTasks();
    }
}
