package com.jxl.ai.intelliconf.service.impl;

import com.jxl.ai.intelliconf.dao.entity.ConfMilestoneDO;
import com.jxl.ai.intelliconf.dao.mapper.ConfMilestoneMapper;
import com.jxl.ai.intelliconf.dao.mapper.ConferenceMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Calendar;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class DeadlineRuleServiceImplTest {

    @Mock
    private ConferenceMapper conferenceMapper;
    @Mock
    private ConfMilestoneMapper milestoneMapper;

    @Test
    void calculateDueTimeFallsBackToMilestoneTargetEndDateWhenRuleIsEmpty() {
        DeadlineRuleServiceImpl service = new DeadlineRuleServiceImpl(conferenceMapper, milestoneMapper);
        Date targetEnd = date(2026, Calendar.JUNE, 10);
        ConfMilestoneDO milestone = new ConfMilestoneDO();
        milestone.setTargetEndDate(targetEnd);

        assertEquals(targetEnd, service.calculateDueTime(1L, milestone, null));
    }

    @Test
    void calculateDueTimeAppliesOffsetToMilestoneFallbackWhenFieldUnknown() {
        DeadlineRuleServiceImpl service = new DeadlineRuleServiceImpl(conferenceMapper, milestoneMapper);
        ConfMilestoneDO milestone = new ConfMilestoneDO();
        milestone.setTargetEndDate(date(2026, Calendar.JUNE, 10));

        Date dueTime = service.calculateDueTime(1L, milestone, "{\"baseDateField\":\"unknown\",\"offsetDays\":-2}");

        assertEquals(date(2026, Calendar.JUNE, 8), dueTime);
    }

    private Date date(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.YEAR, year);
        calendar.set(Calendar.MONTH, month);
        calendar.set(Calendar.DAY_OF_MONTH, day);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }
}
