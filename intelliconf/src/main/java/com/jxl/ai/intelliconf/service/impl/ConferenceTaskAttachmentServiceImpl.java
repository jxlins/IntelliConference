package com.jxl.ai.intelliconf.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jxl.ai.intelliconf.common.biz.user.UserContext;
import com.jxl.ai.intelliconf.common.constant.ConferenceTaskConstant;
import com.jxl.ai.intelliconf.common.convention.exception.ClientException;
import com.jxl.ai.intelliconf.dao.entity.ConfTaskAttachmentDO;
import com.jxl.ai.intelliconf.dao.entity.ConfTaskDO;
import com.jxl.ai.intelliconf.dao.entity.ConfTaskLogDO;
import com.jxl.ai.intelliconf.dao.entity.ConferenceDO;
import com.jxl.ai.intelliconf.dao.mapper.ConfTaskAttachmentMapper;
import com.jxl.ai.intelliconf.dao.mapper.ConfTaskLogMapper;
import com.jxl.ai.intelliconf.dao.mapper.ConfTaskMapper;
import com.jxl.ai.intelliconf.dao.mapper.ConferenceMapper;
import com.jxl.ai.intelliconf.dto.resp.TaskAttachmentRespDTO;
import com.jxl.ai.intelliconf.enums.ConferenceRole;
import com.jxl.ai.intelliconf.service.ConferencePermissionService;
import com.jxl.ai.intelliconf.service.ConferenceTaskAttachmentService;
import com.jxl.ai.intelliconf.service.taskattachment.TaskAttachmentProcessContext;
import com.jxl.ai.intelliconf.service.taskattachment.TaskAttachmentProcessResult;
import com.jxl.ai.intelliconf.service.taskattachment.TaskAttachmentProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConferenceTaskAttachmentServiceImpl implements ConferenceTaskAttachmentService {

    private final ConferenceMapper conferenceMapper;
    private final ConfTaskMapper taskMapper;
    private final ConfTaskAttachmentMapper taskAttachmentMapper;
    private final ConfTaskLogMapper taskLogMapper;
    private final ConferencePermissionService conferencePermissionService;
    private final List<TaskAttachmentProcessor> attachmentProcessors;

    @Override
    @Transactional
    public TaskAttachmentRespDTO uploadAttachment(Long conferenceId, Long taskId, MultipartFile file, String currentUserId) {
        requireConference(conferenceId);
        conferencePermissionService.requireAtLeastRole(conferenceId, ConferenceRole.COMMITTEE);
        ConfTaskDO task = requireTask(conferenceId, taskId);
        if (file == null || file.isEmpty()) {
            throw new ClientException("Please upload a file");
        }

        Date now = new Date();
        Long operatorId = parseLongSafely(currentUserId);
        String operatorName = resolveOperatorName();
        String originalFilename = StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename().trim() : "attachment";
        String storedFileName = UUID.randomUUID() + "-" + originalFilename.replaceAll("[\\\\/:*?\"<>|]", "_");
        Path uploadDir = Paths.get(System.getProperty("user.dir"), "uploads", "task-attachments",
                String.valueOf(conferenceId), String.valueOf(taskId));
        try {
            Files.createDirectories(uploadDir);
            Path targetPath = uploadDir.resolve(storedFileName);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            ConfTaskAttachmentDO attachment = ConfTaskAttachmentDO.builder()
                    .conferenceId(conferenceId)
                    .taskId(taskId)
                    .fileName(originalFilename)
                    .fileUrl(targetPath.toString())
                    .fileType(file.getContentType())
                    .fileSize(file.getSize())
                    .uploadedBy(operatorId)
                    .uploadedByName(operatorName)
                    .uploadedAt(now)
                    .processStatus(ConferenceTaskConstant.ATTACHMENT_PROCESS_STATUS_UNPROCESSED)
                    .build();
            taskAttachmentMapper.insert(attachment);
            writeTaskLog(conferenceId, taskId, ConferenceTaskConstant.TASK_LOG_UPLOAD_ATTACHMENT,
                    operatorId, operatorName, now, originalFilename, null, null);
            processAttachmentIfNeeded(conferenceId, task, attachment, file, operatorId, operatorName);
            return toResp(attachment);
        } catch (IOException ex) {
            log.error("Upload task attachment failed, conferenceId={}, taskId={}", conferenceId, taskId, ex);
            throw new ClientException("Failed to upload task attachment");
        }
    }

    @Override
    public List<TaskAttachmentRespDTO> listAttachments(Long conferenceId, Long taskId, String currentUserId) {
        requireConference(conferenceId);
        conferencePermissionService.requireAtLeastRole(conferenceId, ConferenceRole.COMMITTEE);
        requireTask(conferenceId, taskId);
        return taskAttachmentMapper.selectList(Wrappers.lambdaQuery(ConfTaskAttachmentDO.class)
                        .eq(ConfTaskAttachmentDO::getConferenceId, conferenceId)
                        .eq(ConfTaskAttachmentDO::getTaskId, taskId)
                        .orderByDesc(ConfTaskAttachmentDO::getUploadedAt)
                        .orderByDesc(ConfTaskAttachmentDO::getId))
                .stream()
                .map(this::toResp)
                .toList();
    }

    @Override
    @Transactional
    public void deleteAttachment(Long conferenceId, Long taskId, Long attachmentId, String currentUserId) {
        requireConference(conferenceId);
        conferencePermissionService.requireAtLeastRole(conferenceId, ConferenceRole.COMMITTEE);
        requireTask(conferenceId, taskId);
        ConfTaskAttachmentDO attachment = taskAttachmentMapper.selectOne(Wrappers.lambdaQuery(ConfTaskAttachmentDO.class)
                .eq(ConfTaskAttachmentDO::getId, attachmentId)
                .eq(ConfTaskAttachmentDO::getConferenceId, conferenceId)
                .eq(ConfTaskAttachmentDO::getTaskId, taskId)
                .last("limit 1"));
        if (attachment == null) {
            throw new ClientException("Attachment not found");
        }
        taskAttachmentMapper.deleteById(attachmentId);
        writeTaskLog(conferenceId, taskId, ConferenceTaskConstant.TASK_LOG_DELETE_ATTACHMENT,
                parseLongSafely(currentUserId), resolveOperatorName(), new Date(), attachment.getFileName(), null, null);
    }

    private void processAttachmentIfNeeded(Long conferenceId,
                                           ConfTaskDO task,
                                           ConfTaskAttachmentDO attachment,
                                           MultipartFile file,
                                           Long operatorId,
                                           String operatorName) {
        Optional<TaskAttachmentProcessor> processorOptional = attachmentProcessors.stream()
                .filter(item -> item.supports(task.getTaskCode()))
                .findFirst();
        if (processorOptional.isEmpty()) {
            return;
        }

        Date now = new Date();
        writeTaskLog(conferenceId, task.getId(), ConferenceTaskConstant.TASK_LOG_PROCESS_ATTACHMENT_START,
                operatorId, operatorName, now, attachment.getFileName(), null, null);
        try {
            TaskAttachmentProcessResult result = processorOptional.get().process(TaskAttachmentProcessContext.builder()
                    .conferenceId(conferenceId)
                    .task(task)
                    .attachment(attachment)
                    .file(file)
                    .operatorId(operatorId)
                    .operatorName(operatorName)
                    .build());
            applyProcessResult(attachment, result);
            taskAttachmentMapper.updateById(attachment);
            String operationType = attachment.getSuccessCount() != null && attachment.getSuccessCount() > 0
                    ? ConferenceTaskConstant.TASK_LOG_IMPORT_POTENTIAL_AUTHOR_SUCCESS
                    : ConferenceTaskConstant.TASK_LOG_IMPORT_POTENTIAL_AUTHOR_FAILED;
            writeTaskLog(conferenceId, task.getId(), operationType,
                    operatorId, operatorName, new Date(), attachment.getProcessMessage(), null, attachment.getProcessStatus());
        } catch (Exception ex) {
            log.warn("Attachment processing failed, conferenceId={}, taskId={}, attachmentId={}",
                    conferenceId, task.getId(), attachment.getId(), ex);
            markProcessFailed(attachment, ex.getMessage());
            taskAttachmentMapper.updateById(attachment);
            writeTaskLog(conferenceId, task.getId(), ConferenceTaskConstant.TASK_LOG_IMPORT_POTENTIAL_AUTHOR_FAILED,
                    operatorId, operatorName, new Date(), attachment.getProcessMessage(), null, attachment.getProcessStatus());
        }
    }

    private void applyProcessResult(ConfTaskAttachmentDO attachment, TaskAttachmentProcessResult result) {
        attachment.setProcessType(result.getProcessType());
        attachment.setProcessStatus(result.getProcessStatus());
        attachment.setProcessMessage(result.getProcessMessage());
        attachment.setProcessResult(result.getProcessResult());
        attachment.setTotalCount(result.getTotalCount());
        attachment.setSuccessCount(result.getSuccessCount());
        attachment.setFailedCount(result.getFailedCount());
        attachment.setDuplicateCount(result.getDuplicateCount());
    }

    private void markProcessFailed(ConfTaskAttachmentDO attachment, String message) {
        attachment.setProcessType(ConferenceTaskConstant.ATTACHMENT_PROCESS_TYPE_POTENTIAL_AUTHOR_IMPORT);
        attachment.setProcessStatus(ConferenceTaskConstant.ATTACHMENT_PROCESS_STATUS_FAILED);
        attachment.setProcessMessage(StringUtils.hasText(message)
                ? message
                : "Potential author list import failed");
        attachment.setSuccessCount(0);
        attachment.setFailedCount(0);
        attachment.setDuplicateCount(0);
    }

    private void writeTaskLog(Long conferenceId,
                              Long taskId,
                              String operationType,
                              Long operatorId,
                              String operatorName,
                              Date operationTime,
                              String remark,
                              String oldValue,
                              String newValue) {
        taskLogMapper.insert(ConfTaskLogDO.builder()
                .conferenceId(conferenceId)
                .taskId(taskId)
                .operationType(operationType)
                .operatorId(operatorId)
                .operatorName(operatorName)
                .operationTime(operationTime)
                .remark(remark)
                .oldValue(oldValue)
                .newValue(newValue)
                .build());
    }

    private ConferenceDO requireConference(Long conferenceId) {
        ConferenceDO conference = conferenceMapper.selectById(conferenceId);
        if (conference == null || Integer.valueOf(1).equals(conference.getDelFlag())) {
            throw new ClientException("Conference not found");
        }
        return conference;
    }

    private ConfTaskDO requireTask(Long conferenceId, Long taskId) {
        ConfTaskDO task = taskMapper.selectOne(Wrappers.lambdaQuery(ConfTaskDO.class)
                .eq(ConfTaskDO::getId, taskId)
                .eq(ConfTaskDO::getConferenceId, conferenceId)
                .last("limit 1"));
        if (task == null) {
            throw new ClientException("Task not found");
        }
        return task;
    }

    private TaskAttachmentRespDTO toResp(ConfTaskAttachmentDO attachment) {
        return TaskAttachmentRespDTO.builder()
                .attachmentId(attachment.getId())
                .fileName(attachment.getFileName())
                .fileUrl(attachment.getFileUrl())
                .fileType(attachment.getFileType())
                .fileSize(attachment.getFileSize())
                .uploadedBy(attachment.getUploadedBy())
                .uploadedByName(attachment.getUploadedByName())
                .uploadedAt(attachment.getUploadedAt())
                .processStatus(attachment.getProcessStatus())
                .processType(attachment.getProcessType())
                .relatedBatchId(attachment.getRelatedBatchId())
                .processMessage(attachment.getProcessMessage())
                .processResult(attachment.getProcessResult())
                .totalCount(attachment.getTotalCount())
                .successCount(attachment.getSuccessCount())
                .failedCount(attachment.getFailedCount())
                .duplicateCount(attachment.getDuplicateCount())
                .build();
    }

    private Long parseLongSafely(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String resolveOperatorName() {
        return StringUtils.hasText(UserContext.getRealName()) ? UserContext.getRealName() : UserContext.getUsername();
    }
}
