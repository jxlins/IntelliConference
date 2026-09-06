package com.jxl.ai.intelliconf.service;

import com.jxl.ai.intelliconf.dao.entity.ConfMilestoneDO;

import java.util.Date;

public interface DeadlineRuleService {
    Date calculateDueTime(Long conferenceId, ConfMilestoneDO milestone, String deadlineRule);
}
