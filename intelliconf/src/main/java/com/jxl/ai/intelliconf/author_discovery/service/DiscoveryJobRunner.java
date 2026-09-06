package com.jxl.ai.intelliconf.author_discovery.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jxl.ai.intelliconf.author_discovery.entity.AuthorDiscoveryJobDO;
import com.jxl.ai.intelliconf.author_discovery.repository.AuthorDiscoveryJobMapper;
import com.jxl.ai.intelliconf.common.convention.exception.ClientException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Set;
import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class DiscoveryJobRunner {
    private final AuthorDiscoveryService service;
    private final DiscoveryJobControl jobControl;
    private final AuthorDiscoveryJobMapper jobMapper;
    private final Set<Long> active = ConcurrentHashMap.newKeySet();
    private final ExecutorService executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(2), new ThreadPoolExecutor.AbortPolicy());

    /**
     * 服务刚启动时内存中不可能有真正运行中的任务；数据库里仍为 RUNNING 的是上次进程遗留的僵尸任务，
     * 统一标记为可续跑的 PARTIAL，避免前端永远轮询一个不会再更新的任务。
     */
    @PostConstruct
    public void recoverStaleRunningJobs() {
        try {
            int updated = jobMapper.update(null, Wrappers.lambdaUpdate(AuthorDiscoveryJobDO.class)
                    .eq(AuthorDiscoveryJobDO::getStatus, "RUNNING")
                    .set(AuthorDiscoveryJobDO::getStatus, "PARTIAL")
                    .set(AuthorDiscoveryJobDO::getFinishedAt, new Date())
                    .set(AuthorDiscoveryJobDO::getErrorMessage,
                            "服务重启导致任务中断，已入库结果保留；可再次发起任务继续补充（系统会自动跳过已有结果）"));
            if (updated > 0) {
                log.warn("[AuthorDiscovery] recovered {} stale RUNNING job(s) to PARTIAL on startup", updated);
            }
        } catch (Exception ex) {
            log.warn("[AuthorDiscovery] stale job recovery failed: {}", ex.getClass().getSimpleName());
        }
    }

    public void start(Long id, boolean crossref, boolean extract) {
        if (!active.add(id)) return;
        try {
            Future<?> future = executor.submit(() -> {
                try { service.runJob(id, crossref, extract); }
                catch (Exception ignored) { /* runJob persists execution errors */ }
                finally {
                    active.remove(id);
                    jobControl.unregister(id);
                }
            });
            jobControl.register(id, future);
        } catch (RejectedExecutionException ex) {
            active.remove(id);
            throw new ClientException("发现任务队列已满，请等待当前任务结束");
        }
    }

    public boolean isRunning(Long id) {
        return active.contains(id);
    }

    @PreDestroy public void close() { executor.shutdownNow(); }
}
