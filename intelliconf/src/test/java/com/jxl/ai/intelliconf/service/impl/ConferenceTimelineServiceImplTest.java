package com.jxl.ai.intelliconf.service.impl;

import com.jxl.ai.intelliconf.dao.entity.ConfMilestoneDO;
import com.jxl.ai.intelliconf.dao.entity.ConfTaskAssignmentDO;
import com.jxl.ai.intelliconf.dao.entity.ConfTaskDefDO;
import com.jxl.ai.intelliconf.dao.entity.ConfTaskInstanceDO;
import com.jxl.ai.intelliconf.dao.entity.ConferenceDO;
import com.jxl.ai.intelliconf.dao.mapper.ConfMilestoneMapper;
import com.jxl.ai.intelliconf.dao.mapper.ConfTaskActionLogMapper;
import com.jxl.ai.intelliconf.dao.mapper.ConfTaskAssignmentMapper;
import com.jxl.ai.intelliconf.dao.mapper.ConfTaskDefMapper;
import com.jxl.ai.intelliconf.dao.mapper.ConfTaskInstanceMapper;
import com.jxl.ai.intelliconf.dao.mapper.ConferenceMapper;
import com.jxl.ai.intelliconf.service.ConferenceMemberRoleService;
import com.jxl.ai.intelliconf.service.DeadlineRuleService;
import com.jxl.ai.intelliconf.toolkit.MilestoneUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConferenceTimelineServiceImplTest {

    @Mock
    private ConferenceMapper conferenceMapper;
    @Mock
    private ConfMilestoneMapper milestoneMapper;
    @Mock
    private ConfTaskDefMapper taskDefMapper;
    @Mock
    private ConfTaskInstanceMapper taskInstanceMapper;
    @Mock
    private ConfTaskAssignmentMapper assignmentMapper;
    @Mock
    private ConfTaskActionLogMapper actionLogMapper;
    @Mock
    private DeadlineRuleService deadlineRuleService;
    @Mock
    private ConferenceMemberRoleService memberRoleService;

    @Test
    void initTimelineCreatesMilestonesAndInitialTaskAssignment() {
        ConferenceTimelineServiceImpl service = newService();
        ConferenceDO conference = new ConferenceDO();
        conference.setId(9L);
        conference.setCreateUser("organizer@example.com");
        conference.setStartTime(new Date());
        when(conferenceMapper.selectById(9L)).thenReturn(conference);
        when(milestoneMapper.selectCount(any())).thenReturn(0L);

        ConfMilestoneDO initiation = new ConfMilestoneDO();
        initiation.setId(100L);
        initiation.setConfereId(9L);
        initiation.setNodeCode("INITIATION");
        initiation.setNodeName("Conference Initiation");
        initiation.setStatus(1);
        initiation.setAutoGenerateTasks(1);
        when(milestoneMapper.selectOne(any())).thenReturn(initiation);
        when(milestoneMapper.selectById(100L)).thenReturn(initiation);

        ConfTaskDefDO def = new ConfTaskDefDO();
        def.setId(20L);
        def.setNodeCode("INITIATION");
        def.setTaskCode("INIT_BASIC_INFO");
        def.setTaskName("Complete basic information");
        def.setHandlerBean("manualTaskHandler");
        def.setTaskType("MANUAL");
        def.setDefaultAssigneeType("ROLE");
        def.setDefaultAssigneeRole("ORGANIZER");
        def.setPriority(2);
        def.setStatus(1);
        when(taskDefMapper.selectList(any())).thenReturn(List.of(def));
        when(deadlineRuleService.calculateDueTime(eq(9L), eq(initiation), any())).thenReturn(new Date());
        when(taskInstanceMapper.insert(any())).thenAnswer(invocation -> {
            ConfTaskInstanceDO task = invocation.getArgument(0);
            task.setId(300L);
            return 1;
        });

        service.initTimelineForConference(9L);

        verify(milestoneMapper, atLeast(16)).insert(any(ConfMilestoneDO.class));
        ArgumentCaptor<ConfTaskInstanceDO> taskCaptor = ArgumentCaptor.forClass(ConfTaskInstanceDO.class);
        verify(taskInstanceMapper).insert(taskCaptor.capture());
        assertEquals("INIT_BASIC_INFO", taskCaptor.getValue().getTaskCode());
        assertEquals("PENDING", taskCaptor.getValue().getStatus());

        ArgumentCaptor<ConfTaskAssignmentDO> assignmentCaptor = ArgumentCaptor.forClass(ConfTaskAssignmentDO.class);
        verify(assignmentMapper).insert(assignmentCaptor.capture());
        assertEquals("ROLE", assignmentCaptor.getValue().getAssigneeType());
        assertEquals("ORGANIZER", assignmentCaptor.getValue().getAssigneeRoleCode());
        assertNotNull(assignmentCaptor.getValue().getAssignedAt());
    }

    private ConferenceTimelineServiceImpl newService() {
        return new ConferenceTimelineServiceImpl(
                conferenceMapper,
                milestoneMapper,
                taskDefMapper,
                taskInstanceMapper,
                assignmentMapper,
                actionLogMapper,
                new MilestoneUtil(),
                deadlineRuleService,
                memberRoleService
        );
    }
}
