package com.jxl.ai.intelliconf.common.constant;

public final class ConferenceTaskConstant {

    private ConferenceTaskConstant() {
    }

    public static final String COMPLETION_TYPE_MANUAL_CONFIRM = "MANUAL_CONFIRM";
    public static final String COMPLETION_TYPE_FILE_UPLOAD = "FILE_UPLOAD";
    public static final String COMPLETION_TYPE_SYSTEM_CHECK = "SYSTEM_CHECK";

    public static final String TASK_STATUS_NOT_STARTED = "NOT_STARTED";
    public static final String TASK_STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String TASK_STATUS_REJECTED = "REJECTED";
    public static final String TASK_STATUS_COMPLETED = "COMPLETED";
    public static final String TASK_STATUS_CANCELLED = "CANCELLED";

    public static final String TASK_LOG_UPLOAD_ATTACHMENT = "UPLOAD_ATTACHMENT";
    public static final String TASK_LOG_PROCESS_ATTACHMENT_START = "PROCESS_ATTACHMENT_START";
    public static final String TASK_LOG_IMPORT_POTENTIAL_AUTHOR_SUCCESS = "IMPORT_POTENTIAL_AUTHOR_SUCCESS";
    public static final String TASK_LOG_IMPORT_POTENTIAL_AUTHOR_FAILED = "IMPORT_POTENTIAL_AUTHOR_FAILED";
    public static final String TASK_LOG_DELETE_ATTACHMENT = "DELETE_ATTACHMENT";
    public static final String TASK_LOG_COMPLETE_TASK = "COMPLETE_TASK";

    public static final String ATTACHMENT_PROCESS_TYPE_POTENTIAL_AUTHOR_IMPORT = "POTENTIAL_AUTHOR_IMPORT";
    public static final String ATTACHMENT_PROCESS_STATUS_UNPROCESSED = "UNPROCESSED";
    public static final String ATTACHMENT_PROCESS_STATUS_SUCCESS = "SUCCESS";
    public static final String ATTACHMENT_PROCESS_STATUS_PARTIAL_SUCCESS = "PARTIAL_SUCCESS";
    public static final String ATTACHMENT_PROCESS_STATUS_FAILED = "FAILED";

    public static final String MEMBER_STATUS_ACTIVE = "ACTIVE";
    public static final String TASK_CODE_CONFIRM_CONFERENCE_COMMITTEE = "CONFIRM_CONFERENCE_COMMITTEE";
    public static final String TASK_CODE_IMPORT_POTENTIAL_AUTHOR_LIST = "IMPORT_POTENTIAL_AUTHOR_LIST";
}
