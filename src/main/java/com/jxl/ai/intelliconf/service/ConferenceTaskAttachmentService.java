package com.jxl.ai.intelliconf.service;

import com.jxl.ai.intelliconf.dto.resp.TaskAttachmentRespDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ConferenceTaskAttachmentService {

    TaskAttachmentRespDTO uploadAttachment(Long conferenceId, Long taskId, MultipartFile file, String currentUserId);

    List<TaskAttachmentRespDTO> listAttachments(Long conferenceId, Long taskId, String currentUserId);

    void deleteAttachment(Long conferenceId, Long taskId, Long attachmentId, String currentUserId);
}
