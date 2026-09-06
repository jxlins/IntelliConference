-- IEIR2026 non-destructive merge
-- Target: current database selected by the client.
-- Source: legacy_source restored from conference.sql.
-- Deliberately excluded: conference_mail_account, mail_send_task,
-- conf_task_mail_plan, and all SMTP password_cipher data.

SET NAMES utf8mb4;
SET @ieir_short_name := 'IEIR2026';

START TRANSACTION;

-- Temporary maps keep every legacy identifier separate from the target ID.
CREATE TEMPORARY TABLE map_user (
  source_id BIGINT NOT NULL PRIMARY KEY,
  target_id BIGINT NOT NULL,
  UNIQUE KEY uk_target_id (target_id)
) ENGINE=InnoDB;
CREATE TEMPORARY TABLE map_conference LIKE map_user;
CREATE TEMPORARY TABLE map_stage_def LIKE map_user;
CREATE TEMPORARY TABLE map_task_def LIKE map_user;
CREATE TEMPORARY TABLE map_stage LIKE map_user;
CREATE TEMPORARY TABLE map_task LIKE map_user;
CREATE TEMPORARY TABLE map_contact LIKE map_user;
CREATE TEMPORARY TABLE map_member LIKE map_user;
CREATE TEMPORARY TABLE map_discovery_job LIKE map_user;
CREATE TEMPORARY TABLE map_potential_author LIKE map_user;
CREATE TEMPORARY TABLE map_milestone LIKE map_user;
CREATE TEMPORARY TABLE map_task_instance LIKE map_user;
CREATE TEMPORARY TABLE map_role_def LIKE map_user;
CREATE TEMPORARY TABLE map_invitation LIKE map_user;

-- Accounts: insert missing legacy users. The target row wins for every existing
-- username, and the source admin row (if one appears in a later dump) is never
-- inserted or used to update the target admin.
INSERT INTO sys_user
  (username, password, email, phone, real_name, delete_time,
   create_time, update_time, del_flag)
SELECT s.username, s.password, s.email, s.phone, s.real_name, s.delete_time,
       s.create_time, s.update_time, s.del_flag
FROM legacy_source.sys_user s
WHERE LOWER(s.username) <> 'admin'
  AND NOT EXISTS (
    SELECT 1 FROM sys_user t WHERE BINARY LOWER(t.username) = BINARY LOWER(s.username)
  );

INSERT INTO map_user (source_id, target_id)
SELECT s.id, t.id
FROM legacy_source.sys_user s
JOIN sys_user t ON BINARY LOWER(t.username) = BINARY LOWER(s.username);

-- Conference: preserve an existing target IEIR2026 row; otherwise insert it.
INSERT INTO conferences
  (title, short_name, description, website_url, contact_email, host,
   co_origanizer, start_time, end_time, paper_submission_deadline,
   notification_of_acceptance, camera_ready_submission,
   early_bird_registration, conference_start_date, conference_end_date,
   current_state, setup_status, create_user, create_time, update_time, del_flag)
SELECT s.title, s.short_name, s.description, s.website_url, s.contact_email,
       s.host, s.co_origanizer, s.start_time, s.end_time,
       s.paper_submission_deadline, s.notification_of_acceptance,
       s.camera_ready_submission, s.early_bird_registration,
       s.conference_start_date, s.conference_end_date, s.current_state,
       s.setup_status, s.create_user, s.create_time, s.update_time, s.del_flag
FROM legacy_source.conferences s
WHERE BINARY s.short_name = BINARY @ieir_short_name
  AND NOT EXISTS (
    SELECT 1 FROM conferences t WHERE BINARY t.short_name = BINARY s.short_name
  );

INSERT INTO map_conference (source_id, target_id)
SELECT s.id, t.id
FROM legacy_source.conferences s
JOIN conferences t ON BINARY t.short_name = BINARY s.short_name
WHERE BINARY s.short_name = BINARY @ieir_short_name;

-- Global stage definitions: target definitions are authoritative.
INSERT INTO conf_stage_def
  (stage_code, stage_name, stage_order, stage_desc, status,
   create_time, update_time)
SELECT s.stage_code, s.stage_name, s.stage_order, s.stage_desc, s.status,
       s.create_time, s.update_time
FROM legacy_source.conf_stage_def s
WHERE NOT EXISTS (
  SELECT 1 FROM conf_stage_def t WHERE BINARY t.stage_code = BINARY s.stage_code
);

INSERT INTO map_stage_def (source_id, target_id)
SELECT s.id, t.id
FROM legacy_source.conf_stage_def s
JOIN conf_stage_def t ON BINARY t.stage_code = BINARY s.stage_code;

-- Compatible merge of all 82 legacy task templates. Existing newer templates
-- are not overwritten; only task codes absent from the corresponding stage are
-- inserted using columns supported by the current schema.
INSERT INTO conf_task_def
  (stage_def_id, task_code, task_name, task_desc, task_type, default_role,
   is_core, completion_type, sort_order, offset_base, start_offset_days,
   end_offset_days, need_review, status, create_time, update_time)
SELECT ms.target_id, s.task_code, s.task_name, s.task_desc, s.task_type,
       s.default_role, s.is_core, s.completion_type, s.sort_order,
       s.offset_base, s.start_offset_days, s.end_offset_days, s.need_review,
       s.status, s.create_time, s.update_time
FROM legacy_source.conf_task_def s
JOIN map_stage_def ms ON ms.source_id = s.stage_def_id
WHERE NOT EXISTS (
  SELECT 1
  FROM conf_task_def t
  WHERE t.stage_def_id = ms.target_id AND BINARY t.task_code = BINARY s.task_code
);

INSERT INTO map_task_def (source_id, target_id)
SELECT s.id, t.id
FROM legacy_source.conf_task_def s
JOIN map_stage_def ms ON ms.source_id = s.stage_def_id
JOIN conf_task_def t
  ON t.stage_def_id = ms.target_id AND BINARY t.task_code = BINARY s.task_code;

-- Conference stages.
INSERT INTO conf_stage
  (conference_id, stage_def_id, stage_code, stage_name, stage_order,
   planned_start_time, planned_end_time, actual_start_time, actual_end_time,
   stage_status, progress, is_current, remark, create_time, update_time)
SELECT mc.target_id, msd.target_id, s.stage_code, s.stage_name, s.stage_order,
       s.planned_start_time, s.planned_end_time, s.actual_start_time,
       s.actual_end_time, s.stage_status, s.progress, s.is_current, s.remark,
       s.create_time, s.update_time
FROM legacy_source.conf_stage s
JOIN map_conference mc ON mc.source_id = s.conference_id
JOIN map_stage_def msd ON msd.source_id = s.stage_def_id
WHERE NOT EXISTS (
  SELECT 1 FROM conf_stage t
  WHERE t.conference_id = mc.target_id AND BINARY t.stage_code = BINARY s.stage_code
);

INSERT INTO map_stage (source_id, target_id)
SELECT s.id, t.id
FROM legacy_source.conf_stage s
JOIN map_conference mc ON mc.source_id = s.conference_id
JOIN conf_stage t
  ON t.conference_id = mc.target_id AND BINARY t.stage_code = BINARY s.stage_code;

-- Conference task rows and every user reference they contain.
INSERT INTO conf_task
  (conference_id, stage_id, task_def_id, stage_code, task_code, task_name,
   task_desc, task_type, principal_role, principal_user_id, principal_name,
   planned_start_time, planned_end_time, actual_start_time, actual_end_time,
   task_status, priority, risk_level, is_core, completion_type,
   completion_desc, completion_url, completed_by, completed_by_name,
   submitted_at, reviewed_by, reviewed_by_name, reviewed_at, review_comment,
   need_review, output_desc, sort_order, remark, create_user, update_user,
   create_time, update_time)
SELECT mc.target_id, mst.target_id, mtd.target_id, s.stage_code, s.task_code,
       s.task_name, s.task_desc, s.task_type, s.principal_role,
       COALESCE(mpu.target_id, s.principal_user_id), s.principal_name,
       s.planned_start_time, s.planned_end_time, s.actual_start_time,
       s.actual_end_time, s.task_status, s.priority, s.risk_level, s.is_core,
       s.completion_type, s.completion_desc, s.completion_url,
       COALESCE(mcu.target_id, s.completed_by), s.completed_by_name,
       s.submitted_at, COALESCE(mru.target_id, s.reviewed_by),
       s.reviewed_by_name, s.reviewed_at, s.review_comment, s.need_review,
       s.output_desc, s.sort_order, s.remark,
       COALESCE(mcr.target_id, s.create_user),
       COALESCE(mup.target_id, s.update_user), s.create_time, s.update_time
FROM legacy_source.conf_task s
JOIN map_conference mc ON mc.source_id = s.conference_id
JOIN map_stage mst ON mst.source_id = s.stage_id
LEFT JOIN map_task_def mtd ON mtd.source_id = s.task_def_id
LEFT JOIN map_user mpu ON mpu.source_id = s.principal_user_id
LEFT JOIN map_user mcu ON mcu.source_id = s.completed_by
LEFT JOIN map_user mru ON mru.source_id = s.reviewed_by
LEFT JOIN map_user mcr ON mcr.source_id = s.create_user
LEFT JOIN map_user mup ON mup.source_id = s.update_user
WHERE NOT EXISTS (
  SELECT 1 FROM conf_task t
  WHERE t.conference_id = mc.target_id AND BINARY t.task_code = BINARY s.task_code
);

INSERT INTO map_task (source_id, target_id)
SELECT s.id, t.id
FROM legacy_source.conf_task s
JOIN map_conference mc ON mc.source_id = s.conference_id
JOIN conf_task t
  ON t.conference_id = mc.target_id AND BINARY t.task_code = BINARY s.task_code;

-- Contact pool required by IEIR2026 members. owner_org_id may refer either to
-- a legacy user or to the legacy conference; map both forms.
INSERT INTO conf_contact_pool
  (owner_org_id, name, email, institution, create_time)
SELECT COALESCE(mu.target_id, moc.target_id, s.owner_org_id),
       s.name, s.email, s.institution, s.create_time
FROM legacy_source.conf_contact_pool s
JOIN (
  SELECT DISTINCT contact_id
  FROM legacy_source.conf_member sm
  JOIN map_conference mc ON mc.source_id = sm.conf_id
) used ON used.contact_id = s.id
LEFT JOIN map_user mu ON mu.source_id = s.owner_org_id
LEFT JOIN map_conference moc ON moc.source_id = s.owner_org_id
WHERE NOT EXISTS (
  SELECT 1 FROM conf_contact_pool t
  WHERE t.owner_org_id = COALESCE(mu.target_id, moc.target_id, s.owner_org_id)
    AND BINARY t.email = BINARY s.email
);

INSERT INTO map_contact (source_id, target_id)
SELECT s.id, t.id
FROM legacy_source.conf_contact_pool s
JOIN (
  SELECT DISTINCT contact_id
  FROM legacy_source.conf_member sm
  JOIN map_conference mc ON mc.source_id = sm.conf_id
) used ON used.contact_id = s.id
LEFT JOIN map_user mu ON mu.source_id = s.owner_org_id
LEFT JOIN map_conference moc ON moc.source_id = s.owner_org_id
JOIN conf_contact_pool t
  ON t.owner_org_id = COALESCE(mu.target_id, moc.target_id, s.owner_org_id)
 AND BINARY t.email = BINARY s.email;

INSERT INTO conf_member (conf_id, contact_id, role, create_time)
SELECT mc.target_id, mct.target_id, s.role, s.create_time
FROM legacy_source.conf_member s
JOIN map_conference mc ON mc.source_id = s.conf_id
JOIN map_contact mct ON mct.source_id = s.contact_id
WHERE NOT EXISTS (
  SELECT 1 FROM conf_member t
  WHERE t.conf_id = mc.target_id AND t.contact_id = mct.target_id
    AND BINARY t.role = BINARY s.role
);

INSERT INTO map_member (source_id, target_id)
SELECT s.id, MIN(t.id)
FROM legacy_source.conf_member s
JOIN map_conference mc ON mc.source_id = s.conf_id
JOIN map_contact mct ON mct.source_id = s.contact_id
JOIN conf_member t
  ON t.conf_id = mc.target_id AND t.contact_id = mct.target_id
 AND BINARY t.role = BINARY s.role
GROUP BY s.id;

-- Author discovery runs. A full-row match makes this section idempotent even
-- though the application table has no business-key unique constraint.
INSERT INTO conf_author_discovery_job
  (conference_id, topic_keywords, year_from, year_to, max_authors, status,
   total_papers, total_candidates, high_confidence_emails, error_message,
   created_by, created_at, started_at, finished_at)
SELECT mc.target_id, s.topic_keywords, s.year_from, s.year_to, s.max_authors,
       s.status, s.total_papers, s.total_candidates, s.high_confidence_emails,
       s.error_message, COALESCE(mu.target_id, s.created_by), s.created_at,
       s.started_at, s.finished_at
FROM legacy_source.conf_author_discovery_job s
JOIN map_conference mc ON mc.source_id = s.conference_id
LEFT JOIN map_user mu ON mu.source_id = s.created_by
WHERE NOT EXISTS (
  SELECT 1 FROM conf_author_discovery_job t
  WHERE t.conference_id = mc.target_id
    AND BINARY t.topic_keywords <=> BINARY s.topic_keywords
    AND t.year_from <=> s.year_from AND t.year_to <=> s.year_to
    AND t.max_authors <=> s.max_authors AND BINARY t.status <=> BINARY s.status
    AND t.total_papers <=> s.total_papers
    AND t.total_candidates <=> s.total_candidates
    AND t.high_confidence_emails <=> s.high_confidence_emails
    AND BINARY t.error_message <=> BINARY s.error_message
    AND t.created_by <=> COALESCE(mu.target_id, s.created_by)
    AND t.created_at <=> s.created_at AND t.started_at <=> s.started_at
    AND t.finished_at <=> s.finished_at
);

INSERT INTO map_discovery_job (source_id, target_id)
SELECT s.id, MIN(t.id)
FROM legacy_source.conf_author_discovery_job s
JOIN map_conference mc ON mc.source_id = s.conference_id
LEFT JOIN map_user mu ON mu.source_id = s.created_by
JOIN conf_author_discovery_job t
  ON t.conference_id = mc.target_id
 AND BINARY t.topic_keywords <=> BINARY s.topic_keywords
 AND t.year_from <=> s.year_from AND t.year_to <=> s.year_to
 AND t.max_authors <=> s.max_authors AND BINARY t.status <=> BINARY s.status
 AND t.total_papers <=> s.total_papers
 AND t.total_candidates <=> s.total_candidates
 AND t.high_confidence_emails <=> s.high_confidence_emails
 AND BINARY t.error_message <=> BINARY s.error_message
 AND t.created_by <=> COALESCE(mu.target_id, s.created_by)
 AND t.created_at <=> s.created_at AND t.started_at <=> s.started_at
 AND t.finished_at <=> s.finished_at
GROUP BY s.id;

-- Potential authors use the table's natural email/identity keys. Existing
-- target author rows remain authoritative.
INSERT INTO conf_potential_author
  (conference_id, discovery_job_id, author_name, normalized_name, email,
   organization, country_region, research_keywords, representative_papers,
   source_platform, source_url, topic_similarity, email_confidence,
   overall_score, review_status, contact_status, created_at, updated_at)
SELECT mc.target_id, mdj.target_id, s.author_name, s.normalized_name, s.email,
       s.organization, s.country_region, s.research_keywords,
       s.representative_papers, s.source_platform, s.source_url,
       s.topic_similarity, s.email_confidence, s.overall_score,
       s.review_status, s.contact_status, s.created_at, s.updated_at
FROM legacy_source.conf_potential_author s
JOIN map_conference mc ON mc.source_id = s.conference_id
LEFT JOIN map_discovery_job mdj ON mdj.source_id = s.discovery_job_id
WHERE NOT EXISTS (
  SELECT 1 FROM conf_potential_author t
  WHERE t.conference_id = mc.target_id
    AND ((s.email IS NOT NULL AND BINARY t.email = BINARY s.email)
      OR (BINARY t.normalized_name <=> BINARY s.normalized_name
          AND BINARY t.organization <=> BINARY s.organization))
);

INSERT INTO map_potential_author (source_id, target_id)
SELECT s.id, MIN(t.id)
FROM legacy_source.conf_potential_author s
JOIN map_conference mc ON mc.source_id = s.conference_id
JOIN conf_potential_author t
  ON t.conference_id = mc.target_id
 AND ((s.email IS NOT NULL AND BINARY t.email = BINARY s.email)
   OR (BINARY t.normalized_name <=> BINARY s.normalized_name
       AND BINARY t.organization <=> BINARY s.organization))
GROUP BY s.id;

INSERT INTO conf_author_email_source
  (potential_author_id, email, source_type, source_url, evidence_text,
   confidence, collected_at)
SELECT mpa.target_id, s.email, s.source_type, s.source_url, s.evidence_text,
       s.confidence, s.collected_at
FROM legacy_source.conf_author_email_source s
JOIN map_potential_author mpa ON mpa.source_id = s.potential_author_id
WHERE NOT EXISTS (
  SELECT 1 FROM conf_author_email_source t
  WHERE t.potential_author_id = mpa.target_id
    AND BINARY t.email <=> BINARY s.email AND BINARY t.source_type <=> BINARY s.source_type
    AND BINARY t.source_url <=> BINARY s.source_url
    AND BINARY t.evidence_text <=> BINARY s.evidence_text
    AND t.confidence <=> s.confidence
    AND t.collected_at <=> s.collected_at
);

-- Timeline milestones and legacy task instances (the supplied IEIR2026 dump
-- currently has none, but keeping these steps supports compatible future dumps).
INSERT INTO milestone
  (confere_id, node_code, node_name, pre_node_code, start_date,
   target_end_date, actual_end_date, trigger_event, status, remark,
   is_confirmed, sort_order, is_required, auto_generate_tasks,
   created_at, updated_at)
SELECT mc.target_id, s.node_code, s.node_name, s.pre_node_code, s.start_date,
       s.target_end_date, s.actual_end_date, s.trigger_event, s.status,
       s.remark, s.is_confirmed, s.sort_order, s.is_required,
       s.auto_generate_tasks, s.created_at, s.updated_at
FROM legacy_source.milestone s
JOIN map_conference mc ON mc.source_id = s.confere_id
WHERE NOT EXISTS (
  SELECT 1 FROM milestone t
  WHERE t.confere_id = mc.target_id AND BINARY t.node_code = BINARY s.node_code
);

INSERT INTO map_milestone (source_id, target_id)
SELECT s.id, t.id
FROM legacy_source.milestone s
JOIN map_conference mc ON mc.source_id = s.confere_id
JOIN milestone t ON t.confere_id = mc.target_id AND BINARY t.node_code = BINARY s.node_code;

INSERT INTO conf_task_instance
  (conference_id, milestone_id, task_def_id, node_code, task_code,
   task_name, task_desc, task_type, handler_bean, params, priority, status,
   due_time, jump_url, current_handler_id, current_handler_name, created_by,
   created_at, updated_at)
SELECT mc.target_id, mm.target_id, mtd.target_id, s.node_code, s.task_code,
       s.task_name, s.task_desc, s.task_type, s.handler_bean, s.params,
       s.priority, s.status, s.due_time, s.jump_url,
       COALESCE(CAST(mhu.target_id AS CHAR), s.current_handler_id),
       s.current_handler_name, s.created_by, s.created_at, s.updated_at
FROM legacy_source.conf_task_instance s
JOIN map_conference mc ON mc.source_id = s.conference_id
JOIN map_milestone mm ON mm.source_id = s.milestone_id
JOIN map_task_def mtd ON mtd.source_id = s.task_def_id
LEFT JOIN map_user mhu ON BINARY CAST(mhu.source_id AS CHAR) = BINARY s.current_handler_id
WHERE NOT EXISTS (
  SELECT 1 FROM conf_task_instance t
  WHERE t.conference_id = mc.target_id AND t.task_def_id = mtd.target_id
);

INSERT INTO map_task_instance (source_id, target_id)
SELECT s.id, t.id
FROM legacy_source.conf_task_instance s
JOIN map_conference mc ON mc.source_id = s.conference_id
JOIN map_task_def mtd ON mtd.source_id = s.task_def_id
JOIN conf_task_instance t
  ON t.conference_id = mc.target_id AND t.task_def_id = mtd.target_id;

INSERT INTO conf_task_assignment
  (task_instance_id, conference_id, assignee_type, assignee_user_id,
   assignee_user_name, assignee_role_code, status, assigned_by, assigned_at,
   accepted_at, completed_at, created_at, updated_at)
SELECT mti.target_id, mc.target_id, s.assignee_type,
       COALESCE(CAST(mau.target_id AS CHAR), s.assignee_user_id),
       s.assignee_user_name, s.assignee_role_code, s.status,
       COALESCE(CAST(mbu.target_id AS CHAR), s.assigned_by),
       s.assigned_at, s.accepted_at, s.completed_at, s.created_at, s.updated_at
FROM legacy_source.conf_task_assignment s
JOIN map_conference mc ON mc.source_id = s.conference_id
JOIN map_task_instance mti ON mti.source_id = s.task_instance_id
LEFT JOIN map_user mau ON BINARY CAST(mau.source_id AS CHAR) = BINARY s.assignee_user_id
LEFT JOIN map_user mbu ON BINARY CAST(mbu.source_id AS CHAR) = BINARY s.assigned_by
WHERE NOT EXISTS (
  SELECT 1 FROM conf_task_assignment t
  WHERE t.task_instance_id = mti.target_id
    AND BINARY t.assignee_type <=> BINARY s.assignee_type
    AND BINARY t.assignee_user_id <=> BINARY COALESCE(CAST(mau.target_id AS CHAR), s.assignee_user_id)
    AND BINARY t.assignee_role_code <=> BINARY s.assignee_role_code
    AND BINARY t.status <=> BINARY s.status AND t.assigned_at <=> s.assigned_at
);

INSERT INTO conf_task_action_log
  (task_instance_id, conference_id, operator_id, operator_name,
   operator_role_code, action_type, comment, before_status, after_status,
   created_at)
SELECT mti.target_id, mc.target_id,
       COALESCE(CAST(mou.target_id AS CHAR), s.operator_id), s.operator_name,
       s.operator_role_code, s.action_type, s.comment, s.before_status,
       s.after_status, s.created_at
FROM legacy_source.conf_task_action_log s
JOIN map_conference mc ON mc.source_id = s.conference_id
LEFT JOIN map_task_instance mti ON mti.source_id = s.task_instance_id
LEFT JOIN map_user mou ON BINARY CAST(mou.source_id AS CHAR) = BINARY s.operator_id
WHERE NOT EXISTS (
  SELECT 1 FROM conf_task_action_log t
  WHERE t.conference_id = mc.target_id
    AND t.task_instance_id <=> mti.target_id
    AND BINARY t.operator_id <=> BINARY COALESCE(CAST(mou.target_id AS CHAR), s.operator_id)
    AND BINARY t.action_type <=> BINARY s.action_type AND BINARY t.comment <=> BINARY s.comment
    AND BINARY t.before_status <=> BINARY s.before_status
    AND BINARY t.after_status <=> BINARY s.after_status
    AND t.created_at <=> s.created_at
);

-- Task attachments and business audit logs.
INSERT INTO conf_task_attachment
  (conference_id, task_id, file_name, file_url, file_type, file_size,
   uploaded_by, uploaded_by_name, uploaded_at, process_status, process_type,
   related_batch_id, process_message, process_result, total_count,
   success_count, failed_count, duplicate_count)
SELECT mc.target_id, mt.target_id, s.file_name, s.file_url, s.file_type,
       s.file_size, COALESCE(mu.target_id, s.uploaded_by), s.uploaded_by_name,
       s.uploaded_at, s.process_status, s.process_type, s.related_batch_id,
       s.process_message, s.process_result, s.total_count, s.success_count,
       s.failed_count, s.duplicate_count
FROM legacy_source.conf_task_attachment s
JOIN map_conference mc ON mc.source_id = s.conference_id
JOIN map_task mt ON mt.source_id = s.task_id
LEFT JOIN map_user mu ON mu.source_id = s.uploaded_by
WHERE NOT EXISTS (
  SELECT 1 FROM conf_task_attachment t
  WHERE t.conference_id = mc.target_id AND t.task_id = mt.target_id
    AND BINARY t.file_name = BINARY s.file_name AND BINARY t.file_url = BINARY s.file_url
    AND t.uploaded_at <=> s.uploaded_at
);

INSERT INTO conf_task_log
  (conference_id, task_id, operation_type, old_value, new_value,
   operator_id, operator_name, operation_time, remark)
SELECT mc.target_id, mt.target_id, s.operation_type, s.old_value, s.new_value,
       COALESCE(mu.target_id, s.operator_id), s.operator_name,
       s.operation_time, s.remark
FROM legacy_source.conf_task_log s
JOIN map_conference mc ON mc.source_id = s.conference_id
JOIN map_task mt ON mt.source_id = s.task_id
LEFT JOIN map_user mu ON mu.source_id = s.operator_id
WHERE NOT EXISTS (
  SELECT 1 FROM conf_task_log t
  WHERE t.conference_id = mc.target_id AND t.task_id = mt.target_id
    AND BINARY t.operation_type = BINARY s.operation_type
    AND BINARY t.old_value <=> BINARY s.old_value AND BINARY t.new_value <=> BINARY s.new_value
    AND t.operator_id <=> COALESCE(mu.target_id, s.operator_id)
    AND t.operation_time <=> s.operation_time AND BINARY t.remark <=> BINARY s.remark
);

-- Committee role definitions and invitations.
INSERT INTO conf_committee_role_def
  (role_code, role_name, committee_type, committee_name, role_desc,
   is_required, can_assign_task, sort_order, status, created_at, updated_at)
SELECT s.role_code, s.role_name, s.committee_type, s.committee_name,
       s.role_desc, s.is_required, s.can_assign_task, s.sort_order, s.status,
       s.created_at, s.updated_at
FROM legacy_source.conf_committee_role_def s
WHERE NOT EXISTS (
  SELECT 1 FROM conf_committee_role_def t WHERE BINARY t.role_code = BINARY s.role_code
);

INSERT INTO map_role_def (source_id, target_id)
SELECT s.id, t.id
FROM legacy_source.conf_committee_role_def s
JOIN conf_committee_role_def t ON BINARY t.role_code = BINARY s.role_code;

INSERT INTO conf_member_invitation
  (conference_id, source_task_id, invitee_name, invitee_email,
   invitee_affiliation, role_def_id, role_code, role_name, committee_type,
   committee_name, invitation_token, invitation_status, invited_by,
   invited_by_name, sent_at, accepted_user_id, accepted_at, declined_at,
   declined_reason, expired_at, cancelled_at, last_sent_at, send_count, remark,
   created_at, updated_at)
SELECT mc.target_id, mt.target_id, s.invitee_name, s.invitee_email,
       s.invitee_affiliation, mrd.target_id, s.role_code, s.role_name,
       s.committee_type, s.committee_name, s.invitation_token,
       s.invitation_status, COALESCE(miu.target_id, s.invited_by),
       s.invited_by_name, s.sent_at,
       COALESCE(mau.target_id, s.accepted_user_id), s.accepted_at,
       s.declined_at, s.declined_reason, s.expired_at, s.cancelled_at,
       s.last_sent_at, s.send_count, s.remark, s.created_at, s.updated_at
FROM legacy_source.conf_member_invitation s
JOIN map_conference mc ON mc.source_id = s.conference_id
LEFT JOIN map_task mt ON mt.source_id = s.source_task_id
LEFT JOIN map_role_def mrd ON mrd.source_id = s.role_def_id
LEFT JOIN map_user miu ON miu.source_id = s.invited_by
LEFT JOIN map_user mau ON mau.source_id = s.accepted_user_id
WHERE NOT EXISTS (
  SELECT 1 FROM conf_member_invitation t
  WHERE t.conference_id = mc.target_id AND BINARY t.invitee_email = BINARY s.invitee_email
    AND BINARY t.role_code = BINARY s.role_code
);

INSERT INTO map_invitation (source_id, target_id)
SELECT s.id, t.id
FROM legacy_source.conf_member_invitation s
JOIN map_conference mc ON mc.source_id = s.conference_id
JOIN conf_member_invitation t
  ON t.conference_id = mc.target_id AND BINARY t.invitee_email = BINARY s.invitee_email
 AND BINARY t.role_code = BINARY s.role_code;

INSERT INTO conf_member_role
  (conference_id, user_id, source_invitation_id, role_def_id, role_code,
   role_name, committee_type, committee_name, member_name, member_email,
   affiliation, is_primary, member_status, joined_at, removed_at, remark,
   created_at, updated_at)
SELECT mc.target_id, mu.target_id, mi.target_id, mrd.target_id, s.role_code,
       s.role_name, s.committee_type, s.committee_name, s.member_name,
       s.member_email, s.affiliation, s.is_primary, s.member_status,
       s.joined_at, s.removed_at, s.remark, s.created_at, s.updated_at
FROM legacy_source.conf_member_role s
JOIN map_conference mc ON mc.source_id = s.conference_id
JOIN map_user mu ON mu.source_id = s.user_id
LEFT JOIN map_invitation mi ON mi.source_id = s.source_invitation_id
LEFT JOIN map_role_def mrd ON mrd.source_id = s.role_def_id
WHERE NOT EXISTS (
  SELECT 1 FROM conf_member_role t
  WHERE t.conference_id = mc.target_id AND t.user_id = mu.target_id
    AND BINARY t.role_code = BINARY s.role_code
);

INSERT INTO conf_committee
  (conf_id, email, name, institution, role, invite_status, access_token,
   token_expire_time, create_time, update_time, del_flag)
SELECT mc.target_id, s.email, s.name, s.institution, s.role, s.invite_status,
       s.access_token, s.token_expire_time, s.create_time, s.update_time,
       s.del_flag
FROM legacy_source.conf_committee s
JOIN map_conference mc ON mc.source_id = s.conf_id
WHERE NOT EXISTS (
  SELECT 1 FROM conf_committee t
  WHERE t.conf_id = mc.target_id AND BINARY t.email = BINARY s.email
);

INSERT INTO location
  (confer_id, province, city, country, address, create_time, update_time,
   del_flag)
SELECT mc.target_id, s.province, s.city, s.country, s.address, s.create_time,
       s.update_time, s.del_flag
FROM legacy_source.location s
JOIN map_conference mc ON mc.source_id = s.confer_id
WHERE NOT EXISTS (
  SELECT 1 FROM location t WHERE t.confer_id = mc.target_id
);

INSERT INTO conf_email_suppression
  (conference_id, email, reason, created_at)
SELECT mc.target_id, s.email, s.reason, s.created_at
FROM legacy_source.conf_email_suppression s
JOIN map_conference mc ON mc.source_id = s.conference_id
WHERE NOT EXISTS (
  SELECT 1 FROM conf_email_suppression t
  WHERE t.conference_id = mc.target_id AND BINARY t.email = BINARY s.email
);

-- Historical send logs are inert audit records. The corresponding send queue
-- and SMTP account are intentionally not imported.
INSERT INTO mail_send_log
  (task_id, conference_id, recipient_email, subject, status, error_code,
   error_message, provider_type, sent_at, created_at)
SELECT s.task_id, mc.target_id, s.recipient_email, s.subject, s.status,
       s.error_code, s.error_message, s.provider_type, s.sent_at, s.created_at
FROM legacy_source.mail_send_log s
JOIN map_conference mc ON mc.source_id = s.conference_id
WHERE NOT EXISTS (
  SELECT 1 FROM mail_send_log t
  WHERE t.conference_id = mc.target_id AND t.task_id = s.task_id
    AND BINARY t.recipient_email = BINARY s.recipient_email AND BINARY t.subject = BINARY s.subject
    AND BINARY t.status = BINARY s.status AND t.sent_at <=> s.sent_at
);

COMMIT;

-- Per-run summary. SMTP_ACCOUNT_IMPORTED and SEND_QUEUE_IMPORTED must be zero.
SELECT 'IEIR2026_CONFERENCES' AS metric, COUNT(*) AS value
FROM conferences WHERE BINARY short_name = BINARY @ieir_short_name
UNION ALL SELECT 'LEGACY_TEMPLATE_MAPPED', COUNT(*) FROM map_task_def
UNION ALL SELECT 'IEIR2026_STAGES', COUNT(*)
  FROM conf_stage s JOIN conferences c ON c.id=s.conference_id
  WHERE BINARY c.short_name=BINARY @ieir_short_name
UNION ALL SELECT 'IEIR2026_TASKS', COUNT(*)
  FROM conf_task t JOIN conferences c ON c.id=t.conference_id
  WHERE BINARY c.short_name=BINARY @ieir_short_name
UNION ALL SELECT 'IEIR2026_MEMBERS', COUNT(*)
  FROM conf_member m JOIN conferences c ON c.id=m.conf_id
  WHERE BINARY c.short_name=BINARY @ieir_short_name
UNION ALL SELECT 'IEIR2026_POTENTIAL_AUTHORS', COUNT(*)
  FROM conf_potential_author a JOIN conferences c ON c.id=a.conference_id
  WHERE BINARY c.short_name=BINARY @ieir_short_name
UNION ALL SELECT 'SMTP_ACCOUNT_IMPORTED', COUNT(*)
  FROM conference_mail_account a JOIN conferences c ON c.id=a.conference_id
  WHERE BINARY c.short_name=BINARY @ieir_short_name
UNION ALL SELECT 'SEND_QUEUE_IMPORTED', COUNT(*)
  FROM mail_send_task q JOIN conferences c ON c.id=q.conference_id
  WHERE BINARY c.short_name=BINARY @ieir_short_name;
