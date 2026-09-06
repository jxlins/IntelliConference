package com.jxl.ai.intelliconf.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 专门用于邮件发送的线程池
     */
    @Bean(name = "emailTaskExecutor")
    public Executor emailTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);        // 核心线程数：同时处理 5 封邮件
        executor.setMaxPoolSize(10);    // 最大线程数
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("Mail-Pool-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());    // 将拒绝的任务交还给调用的线程去立即执行

        executor.initialize();
        return executor;
    }
}
