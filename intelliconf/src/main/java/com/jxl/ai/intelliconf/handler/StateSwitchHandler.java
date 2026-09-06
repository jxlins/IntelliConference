package com.jxl.ai.intelliconf.handler;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.jxl.ai.intelliconf.common.convention.exception.ServiceException;
import com.jxl.ai.intelliconf.service.ConferenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 会议状态切换处理器
 * <p>
 * Bean 名称固定为 "stateSwitchHandler"，与 conf_task_def.handler_bean 字段对应。
 * params JSON 格式示例：{"targetState": "SUBMISSION"}
 */
@Slf4j
@Component("stateSwitchHandler")
@RequiredArgsConstructor
public class StateSwitchHandler implements TaskHandler {

    private final ConferenceService conferenceService;

    @Override
    public void execute(Long confId, Long taskLogId, String params) throws Exception {
        // ---- 1. 解析参数 ----
        String targetState;
        try {
            JSONObject paramsJson = JSON.parseObject(params);
            if (paramsJson == null) {
                throw new ServiceException("StateSwitchHandler: params 不能为空或 null");
            }
            targetState = paramsJson.getString("targetState");
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("StateSwitchHandler: params 解析失败，原始值=" + params);
        }

        if (!StringUtils.hasText(targetState)) {
            throw new ServiceException("StateSwitchHandler: params 中 targetState 不能为空");
        }

        log.info("[StateSwitchHandler] 开始切换会议状态, 会议ID: {}, 目标状态: {}", confId, targetState);

        // ---- 2 & 3. 委托 ConferenceService 完成校验与更新 ----
        conferenceService.switchState(confId, targetState);

        log.info("[StateSwitchHandler] 会议状态切换完成, 会议ID: {}, 目标状态: {}", confId, targetState);
    }
}
