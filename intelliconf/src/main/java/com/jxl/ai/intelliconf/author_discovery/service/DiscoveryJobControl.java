package com.jxl.ai.intelliconf.author_discovery.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

/**
 * 发现任务的取消控制：跨线程传递"取消请求"标记，并持有运行任务的 Future 以便中断。
 * 由 DiscoveryJobRunner（注册/注销）与 AuthorDiscoveryService（检查/消费标记）共用。
 */
@Service
public class DiscoveryJobControl {

    private final Set<Long> cancelRequests = ConcurrentHashMap.newKeySet();
    private final Map<Long, Future<?>> tasks = new ConcurrentHashMap<>();

    public void register(Long jobId, Future<?> future) {
        tasks.put(jobId, future);
    }

    public void unregister(Long jobId) {
        tasks.remove(jobId);
        cancelRequests.remove(jobId);
    }

    /** 该任务是否正由当前进程执行 */
    public boolean isRegistered(Long jobId) {
        return tasks.containsKey(jobId);
    }

    /** 请求取消：设置标记并中断执行线程，由 runJob 捕获后落库为 CANCELLED */
    public void requestCancel(Long jobId) {
        cancelRequests.add(jobId);
        Future<?> future = tasks.get(jobId);
        if (future != null) {
            future.cancel(true);
        }
    }

    public boolean isCancelRequested(Long jobId) {
        return cancelRequests.contains(jobId);
    }

    /** 消费（清除）取消标记，返回是否存在 */
    public boolean consumeCancel(Long jobId) {
        return cancelRequests.remove(jobId);
    }
}
