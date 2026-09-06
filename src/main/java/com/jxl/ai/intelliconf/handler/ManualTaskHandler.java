package com.jxl.ai.intelliconf.handler;

import com.jxl.ai.intelliconf.common.constant.TaskConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component(TaskConstants.MANUAL_TASK_HANDLER)
@RequiredArgsConstructor
public class ManualTaskHandler implements TaskHandler {

    // 假设你有一个待办事项表或通知表
    // private final TodoService todoService;

    @Override
    public void execute(Long confId, Long taskLogId, String params) {
        log.info("[ManualTaskHandler] Initializing manual task for conference: {}", confId);

        // 1. 解析参数 (例如：{"title": "分配审稿专家", "hint": "请进入专家管理页面进行分配"})
        // JSONObject json = JSON.parseObject(params);
        // String title = json.getString("title");

        // 2. 创建一条待办记录给组织者
        // 这一步是关键，它让组织者在后台能看到“有一个任务需要处理”
        // todoService.createTodo(confId, title, "PENDING");

        // 3. 注意：这里不需要更新 sys_task_log 的状态为 2 (成功)
        // 状态更新由用户在前端点击“完成”时，通过专门的接口触发
        log.info("[ManualTaskHandler] Manual task notification sent. Waiting for organizer action.");
    }
}