package com.jxl.ai.intelliconf.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jxl.ai.intelliconf.dao.entity.ConfMilestoneDO;
import com.jxl.ai.intelliconf.dao.entity.ConferenceDO;
import com.jxl.ai.intelliconf.dao.mapper.ConfMilestoneMapper;
import com.jxl.ai.intelliconf.dao.mapper.ConferenceMapper;
import com.jxl.ai.intelliconf.service.DeadlineRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Calendar;
import java.util.Date;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DeadlineRuleServiceImpl implements DeadlineRuleService {

    private static final Map<String, String> FIELD_TO_NODE = Map.ofEntries(
            Map.entry("submissionOpenDate", "SUBMISSION"),
            Map.entry("submissionDeadline", "SUBMISSION"),
            Map.entry("reviewStartDate", "REVIEW"),
            Map.entry("reviewDeadline", "REVIEW"),
            Map.entry("rebuttalStartDate", "REBUTTAL"),
            Map.entry("rebuttalDeadline", "REBUTTAL"),
            Map.entry("acceptanceDecisionDate", "ACCEPTANCE"),
            Map.entry("notificationDate", "ACCEPTANCE"),
            Map.entry("cameraReadyOpenDate", "CAMERA_READY"),
            Map.entry("cameraReadyDeadline", "CAMERA_READY"),
            Map.entry("registrationDeadline", "REGISTRATION"),
            Map.entry("conferenceStartDate", "CONFERENCE"),
            Map.entry("conferenceEndDate", "CONFERENCE")
    );

    private final ConferenceMapper conferenceMapper;
    private final ConfMilestoneMapper milestoneMapper;

    @Override
    public Date calculateDueTime(Long conferenceId, ConfMilestoneDO milestone, String deadlineRule) {
        if (!StringUtils.hasText(deadlineRule)) {
            return milestone == null ? null : milestone.getTargetEndDate();
        }
        try {
            JSONObject json = JSON.parseObject(deadlineRule);
            String baseDateField = json.getString("baseDateField");
            int offsetDays = json.getIntValue("offsetDays", 0);
            Date base = resolveBaseDate(conferenceId, milestone, baseDateField);
            return base == null ? (milestone == null ? null : milestone.getTargetEndDate()) : addDays(base, offsetDays);
        } catch (Exception ex) {
            return milestone == null ? null : milestone.getTargetEndDate();
        }
    }

    private Date resolveBaseDate(Long conferenceId, ConfMilestoneDO milestone, String baseDateField) {
        if ("createdAt".equalsIgnoreCase(baseDateField)) {
            ConferenceDO conference = conferenceMapper.selectById(conferenceId);
            return conference == null ? null : conference.getCreateTime();
        }
        String nodeCode = FIELD_TO_NODE.get(baseDateField);
        if (!StringUtils.hasText(nodeCode)) {
            return milestone == null ? null : milestone.getTargetEndDate();
        }
        ConfMilestoneDO ref = milestoneMapper.selectOne(Wrappers.lambdaQuery(ConfMilestoneDO.class)
                .eq(ConfMilestoneDO::getConfereId, conferenceId)
                .eq(ConfMilestoneDO::getNodeCode, nodeCode)
                .last("limit 1"));
        if (ref == null) {
            return milestone == null ? null : milestone.getTargetEndDate();
        }
        if (baseDateField.toLowerCase().contains("start") || baseDateField.toLowerCase().contains("open")) {
            return ref.getStartDate();
        }
        return ref.getTargetEndDate();
    }

    private Date addDays(Date date, int days) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.DAY_OF_YEAR, days);
        return calendar.getTime();
    }
}
