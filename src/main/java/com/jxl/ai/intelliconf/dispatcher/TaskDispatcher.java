package com.jxl.ai.intelliconf.dispatcher;

import cn.hutool.core.exceptions.ExceptionUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jxl.ai.intelliconf.common.constant.TaskConstants;
import com.jxl.ai.intelliconf.dao.entity.ConfTaskDefDO;
import com.jxl.ai.intelliconf.dao.entity.SysTaskLogDO;
import com.jxl.ai.intelliconf.dao.mapper.ConfTaskDefMapper;
import com.jxl.ai.intelliconf.dao.mapper.SysTaskLogMapper;
import com.jxl.ai.intelliconf.event.MilestoneConfirmedEvent;
import com.jxl.ai.intelliconf.handler.TaskHandler;
import com.jxl.ai.intelliconf.service.MilestoneCompletionChecker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 任务分发引擎
 * <p>
 * 当里程碑节点激活时，按优先级顺序查找并执行 conf_task_def 中 node_code 匹配的所有已启用任务，
 * 单个任务失败后记录日志并继续执行后续任务，保证整体流程不中断。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskDispatcher {

    private final ApplicationContext applicationContext;
    private final ConfTaskDefMapper confTaskDefMapper;
    private final SysTaskLogMapper sysTaskLogMapper;
    private final MilestoneCompletionChecker milestoneCompletionChecker;

    /**
     * 根据节点编码分发并执行对应的任务链
     *
     * @param confId      会议ID
     * @param nodeCode    激活的里程碑节点编码（对应 conf_task_def.node_code）
     * @param milestoneId 激活的里程碑ID
     */
    public void dispatch(Long confId, String nodeCode, Long milestoneId) {
        // 第一步：查询 conf_task_def 中匹配 nodeCode 且 status=1 （启用）的任务，按 priority 升序
        List<ConfTaskDefDO> tasks = confTaskDefMapper.selectList(
                Wrappers.lambdaQuery(ConfTaskDefDO.class)
                        .eq(ConfTaskDefDO::getNodeCode, nodeCode)
                        .eq(ConfTaskDefDO::getStatus, 1)
                        .orderByAsc(ConfTaskDefDO::getPriority)
        );

        if (tasks.isEmpty()) {
            log.info("[TaskDispatcher] No enabled tasks found, nodeCode={}, confId={}", nodeCode, confId);
            return;
        }

        log.info("[TaskDispatcher] Dispatching {} task(s), nodeCode={}, confId={}, milestoneId={}",
                tasks.size(), nodeCode, confId, milestoneId);

        // 第二步：幂等构建任务日志。已存在的任务日志复用，不存在的补写 executionStatus=0。
        List<Long> taskDefIds = tasks.stream().map(ConfTaskDefDO::getId).collect(Collectors.toList());
        Map<Long, SysTaskLogDO> existingLogMap = sysTaskLogMapper.selectList(
                Wrappers.lambdaQuery(SysTaskLogDO.class)
                        .eq(SysTaskLogDO::getConfereId, confId)
                        .eq(SysTaskLogDO::getMilestoneId, milestoneId)
                        .in(SysTaskLogDO::getTaskDefId, taskDefIds)
                        .orderByAsc(SysTaskLogDO::getId)
        ).stream().collect(Collectors.toMap(
                SysTaskLogDO::getTaskDefId,
                Function.identity(),
                (first, second) -> first
        ));

        List<SysTaskLogDO> taskLogs = tasks.stream().map(task -> {
            SysTaskLogDO existing = existingLogMap.get(task.getId());
            if (existing != null) {
                return existing;
            }

            SysTaskLogDO created = SysTaskLogDO.builder()
                    .confereId(confId)
                    .milestoneId(milestoneId)
                    .taskDefId(task.getId())
                    .handlerBean(task.getHandlerBean())
                    .executionStatus(0)
                    .retryCount(0)
                    .createTime(new Date())
                    .build();
            sysTaskLogMapper.insert(created);
            return created;
        }).collect(Collectors.toList());

        // 第三步：按优先级逐个执行任务（tasks 已按 priority 升序排列）
        for (int i = 0; i < tasks.size(); i++) {
            ConfTaskDefDO task = tasks.get(i);
            SysTaskLogDO taskLog = taskLogs.get(i);
            String handlerBean = task.getHandlerBean();
            Long logId = taskLog.getId();

                        // 仅执行待处理任务，避免重复执行已运行/已完成/失败任务。
                        if (!Integer.valueOf(0).equals(taskLog.getExecutionStatus())) {
                                continue;
                        }

            // 步骤 A：执行前将状态更新为 1（执行中）
            sysTaskLogMapper.updateById(SysTaskLogDO.builder()
                    .id(logId)
                    .executionStatus(1)
                    .startTime(new Date())
                    .build());

            // 步骤 B：动态获取 Handler 并执行，失败时隔离记录，继续下一任务
            try {
                TaskHandler handler = applicationContext.getBean(handlerBean, TaskHandler.class);
                log.info("[TaskDispatcher] Executing task, handlerBean={}, confId={}, milestoneId={}",
                        handlerBean, confId, milestoneId);

                                handler.execute(confId, logId, task.getParams());

                // 步骤 C：区分人工任务与自动任务的完成策略
                if (TaskConstants.MANUAL_TASK_HANDLER.equals(handlerBean)) {
                    // 人工任务：Handler 内部仅负责初始化待办/通知，日志状态保持为 1（等待人工操作）
                    log.info("[TaskDispatcher] Manual task initialized, waiting for user action. logId={}", logId);
                                } else if (TaskConstants.EMAIL_TASK_HANDLER.equals(handlerBean)) {
                                        // 邮件任务：异步执行，状态由 EmailTaskHandler 在收敛时更新。
                                        log.info("[TaskDispatcher] Email task submitted async, waiting handler finalize status. logId={}", logId);
                } else {
                    // 自动任务：Handler 执行完即视为成功，更新状态为 2（已完成）
                    sysTaskLogMapper.updateById(SysTaskLogDO.builder()
                            .id(logId)
                            .executionStatus(2)
                            .endTime(new Date())
                            .build());
                    log.info("[TaskDispatcher] Task succeeded, handlerBean={}, confId={}, milestoneId={}",
                            handlerBean, confId, milestoneId);
                }
            } catch (Exception e) {
                // 步骤 D：失败隔离——更新 executionStatus=3，记录异常堆栈，继续执行下一个任务
                log.error("[TaskDispatcher] Task failed, handlerBean={}, confId={}, milestoneId={}",
                        handlerBean, confId, milestoneId, e);
                sysTaskLogMapper.updateById(SysTaskLogDO.builder()
                        .id(logId)
                        .executionStatus(3)
                        .endTime(new Date())
                        .errorMsg(ExceptionUtil.stacktraceToString(e))
                        .build());
            }
        }

        log.info("[TaskDispatcher] Dispatch completed, nodeCode={}, confId={}, milestoneId={}",
                nodeCode, confId, milestoneId);

        // 所有任务执行完毕后，检查里程碑是否已达成（全部任务成功则 1 → 2）
        milestoneCompletionChecker.checkAndComplete(milestoneId);
    }

    /**
     * 监听里程碑确认事件，自动触发任务分发
     * <p>
     * 使用事件驱动模式解耦依赖关系，避免循环依赖。
     *
     * @param event 里程碑确认事件
     */
    @EventListener
    public void handleMilestoneConfirmed(MilestoneConfirmedEvent event) {
        log.info("[TaskDispatcher] Received MilestoneConfirmedEvent, confId={}, nodeCode={}, milestoneId={}",
                event.getConfId(), event.getNodeCode(), event.getMilestoneId());
        
        // 触发任务分发
        dispatch(event.getConfId(), event.getNodeCode(), event.getMilestoneId());
    }
}