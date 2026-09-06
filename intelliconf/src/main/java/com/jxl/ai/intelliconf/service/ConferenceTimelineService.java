package com.jxl.ai.intelliconf.service;

import com.jxl.ai.intelliconf.dao.entity.ConfMilestoneDO;
import com.jxl.ai.intelliconf.dto.resp.TimelineOverviewRespDTO;

public interface ConferenceTimelineService {
    void initTimelineForConference(Long conferenceId);
    TimelineOverviewRespDTO getTimeline(Long conferenceId);
    ConfMilestoneDO startMilestone(Long conferenceId, String nodeCode, String currentUserId);
    int generateTasksForMilestone(Long conferenceId, Long milestoneId, String currentUserId);
    void completeMilestone(Long conferenceId, String nodeCode, String currentUserId);
    void moveToNextMilestone(Long conferenceId, String currentNodeCode, String currentUserId);
    boolean checkMilestoneCompletion(Long conferenceId, String nodeCode);
    ConfMilestoneDO getCurrentMilestone(Long conferenceId);
    void finishConference(Long conferenceId, String currentUserId);
    void skipMilestone(Long conferenceId, String nodeCode, String currentUserId, String reason);
}
