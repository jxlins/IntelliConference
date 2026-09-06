package com.jxl.ai.intelliconf.service;

import com.jxl.ai.intelliconf.dao.entity.ConfTaskInstanceDO;
import com.jxl.ai.intelliconf.dto.req.ConferenceTaskQueryReqDTO;

import java.util.List;

public interface ConferenceTaskVisibilityService {
    List<ConfTaskInstanceDO> listMyTasks(Long conferenceId, String currentUserId, ConferenceTaskQueryReqDTO query);
    List<ConfTaskInstanceDO> listAllTasksForOrganizer(Long conferenceId, String currentUserId, ConferenceTaskQueryReqDTO query);
    boolean canViewTask(Long conferenceId, Long taskInstanceId, String currentUserId);
}
