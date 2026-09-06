package com.jxl.ai.intelliconf.service.impl;

import com.jxl.ai.intelliconf.common.convention.exception.ClientException;
import com.jxl.ai.intelliconf.dao.entity.ConfCommitteeRoleDefDO;
import com.jxl.ai.intelliconf.dao.entity.ConfMemberInvitationDO;
import com.jxl.ai.intelliconf.dao.entity.ConfTaskDO;
import com.jxl.ai.intelliconf.dao.entity.ConferenceDO;
import com.jxl.ai.intelliconf.dao.mapper.ConfCommitteeRoleDefMapper;
import com.jxl.ai.intelliconf.dao.mapper.ConfMemberInvitationMapper;
import com.jxl.ai.intelliconf.dao.mapper.ConfMemberRoleMapper;
import com.jxl.ai.intelliconf.dao.mapper.ConfTaskMapper;
import com.jxl.ai.intelliconf.dao.mapper.ConferenceMapper;
import com.jxl.ai.intelliconf.dao.mapper.UserMapper;
import com.jxl.ai.intelliconf.dto.req.CommitteeInvitationSaveReqDTO;
import com.jxl.ai.intelliconf.service.ConferenceMemberRoleService;
import com.jxl.ai.intelliconf.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommitteeInvitationServiceImplTest {

    private ConferenceMapper conferenceMapper;
    private ConfTaskMapper taskMapper;
    private ConfCommitteeRoleDefMapper roleDefMapper;
    private ConfMemberInvitationMapper invitationMapper;
    private CommitteeInvitationServiceImpl service;

    @BeforeEach
    void setUp() {
        conferenceMapper = mock(ConferenceMapper.class);
        taskMapper = mock(ConfTaskMapper.class);
        roleDefMapper = mock(ConfCommitteeRoleDefMapper.class);
        invitationMapper = mock(ConfMemberInvitationMapper.class);
        service = new CommitteeInvitationServiceImpl(
                conferenceMapper,
                taskMapper,
                roleDefMapper,
                invitationMapper,
                mock(ConfMemberRoleMapper.class),
                mock(UserMapper.class),
                mock(ConferenceMemberRoleService.class),
                mock(EmailService.class)
        );

        when(conferenceMapper.selectOne(any())).thenReturn(ConferenceDO.builder().id(1L).build());
        when(taskMapper.selectOne(any())).thenReturn(ConfTaskDO.builder()
                .id(8L)
                .conferenceId(1L)
                .taskCode("CONFIRM_CONFERENCE_COMMITTEE")
                .build());
        when(roleDefMapper.selectOne(any())).thenReturn(ConfCommitteeRoleDefDO.builder()
                .id(3L)
                .roleCode("SECRETARY")
                .roleName("会议秘书")
                .committeeType("SECRETARIAT")
                .committeeName("会议秘书处")
                .status(1)
                .build());
    }

    @Test
    void declinedInvitationCanBeResetAndReused() {
        ConfMemberInvitationDO existing = ConfMemberInvitationDO.builder()
                .id(10L)
                .conferenceId(1L)
                .roleCode("SECRETARY")
                .inviteeEmail("person@example.com")
                .invitationToken("old-token")
                .invitationStatus("DECLINED")
                .build();
        when(invitationMapper.selectOne(any())).thenReturn(existing);

        service.saveInvitation(1L, request(), "organizer");

        assertEquals("DRAFT", existing.getInvitationStatus());
        assertNotEquals("old-token", existing.getInvitationToken());
        assertNull(existing.getDeclinedAt());
        assertNull(existing.getDeclinedReason());
        verify(invitationMapper).updateById(existing);
        verify(invitationMapper, never()).insert(any());
    }

    @Test
    void acceptedInvitationCannotBeCreatedAgain() {
        when(invitationMapper.selectOne(any())).thenReturn(ConfMemberInvitationDO.builder()
                .id(10L)
                .conferenceId(1L)
                .roleCode("SECRETARY")
                .inviteeEmail("person@example.com")
                .invitationStatus("ACCEPTED")
                .build());

        assertThrows(ClientException.class, () -> service.saveInvitation(1L, request(), "organizer"));
        verify(invitationMapper, never()).insert(any());
        verify(invitationMapper, never()).updateById(any());
    }

    private CommitteeInvitationSaveReqDTO request() {
        CommitteeInvitationSaveReqDTO request = new CommitteeInvitationSaveReqDTO();
        request.setSourceTaskId(8L);
        request.setRoleDefId(3L);
        request.setInviteeName("Test User");
        request.setInviteeEmail("person@example.com");
        return request;
    }
}
