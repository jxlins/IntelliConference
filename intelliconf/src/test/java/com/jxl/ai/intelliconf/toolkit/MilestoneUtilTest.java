package com.jxl.ai.intelliconf.toolkit;

import com.jxl.ai.intelliconf.dao.entity.ConfMilestoneDO;
import com.jxl.ai.intelliconf.enums.MilestoneTemplate;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MilestoneUtilTest {

    @Test
    void generateDefaultTimelineCreatesFullConferenceProcess() {
        List<ConfMilestoneDO> milestones = new MilestoneUtil().generateDefaultTimeline(10L, new Date());

        assertEquals(MilestoneTemplate.values().length, milestones.size());
        assertEquals("INITIATION", milestones.get(0).getNodeCode());
        assertEquals("POST_CONF", milestones.get(milestones.size() - 1).getNodeCode());
        assertEquals(1, milestones.get(0).getSortOrder());
        assertEquals(1, milestones.get(0).getIsRequired());
        assertEquals(1, milestones.get(0).getAutoGenerateTasks());
        assertNotNull(milestones.get(0).getTargetEndDate());
    }
}
