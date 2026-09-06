package com.jxl.ai.intelliconf.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jxl.ai.intelliconf.common.convention.exception.ClientException;
import com.jxl.ai.intelliconf.dao.entity.ConfTaskAssignmentDO;
import com.jxl.ai.intelliconf.dao.entity.ConfTaskInstanceDO;
import com.jxl.ai.intelliconf.dao.mapper.ConfTaskAssignmentMapper;
import com.jxl.ai.intelliconf.dao.mapper.ConfTaskInstanceMapper;
import com.jxl.ai.intelliconf.dto.req.ConferenceTaskQueryReqDTO;
import com.jxl.ai.intelliconf.service.ConferenceMemberRoleService;
import com.jxl.ai.intelliconf.service.ConferenceTaskVisibilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConferenceTaskVisibilityServiceImpl implements ConferenceTaskVisibilityService {

    private final ConfTaskInstanceMapper taskMapper;
    private final ConfTaskAssignmentMapper assignmentMapper;
    private final ConferenceMemberRoleService memberRoleService;

    @Override
    public List<ConfTaskInstanceDO> listMyTasks(Long conferenceId, String currentUserId, ConferenceTaskQueryReqDTO query) {
        memberRoleService.requireMember(conferenceId, currentUserId);
        if (memberRoleService.isOrganizer(conferenceId, currentUserId)) {
            return listAllTasksForOrganizer(conferenceId, currentUserId, query);
        }
        Set<String> roles = memberRoleService.getRoleCodes(conferenceId, currentUserId);
        Set<Long> visibleTaskIds = new HashSet<>();
        List<ConfTaskAssignmentDO> assignments = assignmentMapper.selectList(Wrappers.lambdaQuery(ConfTaskAssignmentDO.class)
                .eq(ConfTaskAssignmentDO::getConferenceId, conferenceId)
                .and(w -> w.eq(ConfTaskAssignmentDO::getAssigneeUserId, currentUserId)
                        .or(!CollectionUtils.isEmpty(roles), x -> x.in(ConfTaskAssignmentDO::getAssigneeRoleCode, roles))));
        assignments.forEach(a -> visibleTaskIds.add(a.getTaskInstanceId()));
        taskMapper.selectList(Wrappers.lambdaQuery(ConfTaskInstanceDO.class)
                        .eq(ConfTaskInstanceDO::getConferenceId, conferenceId)
                        .eq(ConfTaskInstanceDO::getCurrentHandlerId, currentUserId))
                .forEach(t -> visibleTaskIds.add(t.getId()));
        if (visibleTaskIds.isEmpty()) {
            return List.of();
        }
        return taskMapper.selectList(applyQuery(Wrappers.lambdaQuery(ConfTaskInstanceDO.class)
                .eq(ConfTaskInstanceDO::getConferenceId, conferenceId)
                .in(ConfTaskInstanceDO::getId, visibleTaskIds), query));
    }

    @Override
    public List<ConfTaskInstanceDO> listAllTasksForOrganizer(Long conferenceId, String currentUserId, ConferenceTaskQueryReqDTO query) {
        memberRoleService.requireOrganizer(conferenceId, currentUserId);
        return taskMapper.selectList(applyQuery(Wrappers.lambdaQuery(ConfTaskInstanceDO.class)
                .eq(ConfTaskInstanceDO::getConferenceId, conferenceId), query));
    }

    @Override
    public boolean canViewTask(Long conferenceId, Long taskInstanceId, String currentUserId) {
        if (memberRoleService.isOrganizer(conferenceId, currentUserId)) {
            return true;
        }
        return listMyTasks(conferenceId, currentUserId, new ConferenceTaskQueryReqDTO()).stream()
                .map(ConfTaskInstanceDO::getId)
                .collect(Collectors.toSet())
                .contains(taskInstanceId);
    }

    private LambdaQueryWrapper<ConfTaskInstanceDO> applyQuery(LambdaQueryWrapper<ConfTaskInstanceDO> wrapper, ConferenceTaskQueryReqDTO query) {
        if (query == null) {
            wrapper.ne(ConfTaskInstanceDO::getStatus, "CANCELLED");
            return wrapper.orderByAsc(ConfTaskInstanceDO::getDueTime).orderByDesc(ConfTaskInstanceDO::getCreatedAt);
        }
        if (!StringUtils.hasText(query.getStatus())) {
            wrapper.ne(ConfTaskInstanceDO::getStatus, "CANCELLED");
        }
        wrapper.eq(StringUtils.hasText(query.getNodeCode()), ConfTaskInstanceDO::getNodeCode, query.getNodeCode());
        wrapper.eq(StringUtils.hasText(query.getStatus()), ConfTaskInstanceDO::getStatus, query.getStatus());
        wrapper.eq(StringUtils.hasText(query.getPriority()), ConfTaskInstanceDO::getPriority, query.getPriority());
        wrapper.eq(StringUtils.hasText(query.getTaskType()), ConfTaskInstanceDO::getTaskType, query.getTaskType());
        wrapper.and(StringUtils.hasText(query.getKeyword()), w -> w.like(ConfTaskInstanceDO::getTaskName, query.getKeyword())
                .or().like(ConfTaskInstanceDO::getTaskCode, query.getKeyword()));
        return wrapper.orderByAsc(ConfTaskInstanceDO::getDueTime).orderByDesc(ConfTaskInstanceDO::getCreatedAt);
    }
}
