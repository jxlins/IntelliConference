package com.jxl.ai.intelliconf.service;

import com.jxl.ai.intelliconf.dto.req.ConferenceTaskQueryReqDTO;
import com.jxl.ai.intelliconf.dto.req.TaskCompleteReqDTO;
import com.jxl.ai.intelliconf.dto.req.TaskActionReqDTO;
import com.jxl.ai.intelliconf.dto.req.TaskAssignReqDTO;
import com.jxl.ai.intelliconf.dto.resp.ConferenceTaskRespDTO;
import com.jxl.ai.intelliconf.dto.resp.TaskAssigneeCandidateRespDTO;
import com.jxl.ai.intelliconf.dto.resp.TaskCompleteRespDTO;
import com.jxl.ai.intelliconf.dto.resp.TaskActionLogRespDTO;
import com.jxl.ai.intelliconf.dto.resp.TaskSystemCheckRespDTO;

import java.util.List;

public interface ConferenceTaskService {
    List<ConferenceTaskRespDTO> previewTasksForConference(Long conferenceId, String currentUserId);
    List<ConferenceTaskRespDTO> generateTasksForConference(Long conferenceId, String currentUserId);
    List<ConferenceTaskRespDTO> listGeneratedTasksForConference(Long conferenceId, String currentUserId);
    void evictGeneratedTaskCacheAfterCommit(Long conferenceId);
    List<ConferenceTaskRespDTO> listMyTasks(Long conferenceId, String currentUserId, ConferenceTaskQueryReqDTO query);
    List<ConferenceTaskRespDTO> listAllTasksForOrganizer(Long conferenceId, String currentUserId, ConferenceTaskQueryReqDTO query);
    ConferenceTaskRespDTO getTaskDetail(Long conferenceId, Long taskInstanceId, String currentUserId);
    TaskSystemCheckRespDTO getTaskSystemCheck(Long conferenceId, Long taskId, String currentUserId);
    List<TaskAssigneeCandidateRespDTO> listTaskAssigneeCandidates(Long conferenceId, Long taskId, String currentUserId);
    void assignTask(Long conferenceId, Long taskId, String currentUserId, TaskAssignReqDTO request);
    void startTask(Long conferenceId, Long taskInstanceId, String currentUserId, TaskActionReqDTO command);
    TaskCompleteRespDTO completeTask(Long conferenceId, Long taskInstanceId, String currentUserId, TaskCompleteReqDTO command);
    void transferTask(Long conferenceId, Long taskInstanceId, String currentUserId, TaskActionReqDTO command);
    void rejectTask(Long conferenceId, Long taskInstanceId, String currentUserId, TaskActionReqDTO command);
    void cancelTask(Long conferenceId, Long taskInstanceId, String currentUserId, TaskActionReqDTO command);
    void addComment(Long conferenceId, Long taskInstanceId, String currentUserId, TaskActionReqDTO command);
    List<TaskActionLogRespDTO> listTaskLogs(Long conferenceId, Long taskInstanceId, String currentUserId);
}
