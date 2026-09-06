-- Run in the cloned/target database after 20260907_merge_ieir2026.sql.
SET NAMES utf8mb4;
SET @ieir_short_name := 'IEIR2026';

SELECT 'admin_count' AS metric, COUNT(*) AS actual, 1 AS expected
FROM sys_user WHERE username='admin'
UNION ALL SELECT 'dn_count', COUNT(*), 1
FROM conferences WHERE short_name='dn'
UNION ALL SELECT 'ieir_conference', COUNT(*), 1
FROM conferences WHERE BINARY short_name=BINARY @ieir_short_name
UNION ALL SELECT 'legacy_users_mapped', COUNT(*),
  (SELECT COUNT(*) FROM legacy_source.sys_user)
FROM legacy_source.sys_user s JOIN sys_user t ON BINARY LOWER(t.username)=BINARY LOWER(s.username)
UNION ALL SELECT 'legacy_task_templates', COUNT(*),
  (SELECT COUNT(*) FROM legacy_source.conf_task_def)
FROM legacy_source.conf_task_def s
JOIN legacy_source.conf_stage_def osd ON osd.id=s.stage_def_id
JOIN conf_stage_def nsd ON nsd.stage_code=osd.stage_code
JOIN conf_task_def t ON t.stage_def_id=nsd.id AND BINARY t.task_code=BINARY s.task_code
UNION ALL SELECT 'ieir_stages', COUNT(*),
  (SELECT COUNT(*) FROM legacy_source.conf_stage s
   JOIN legacy_source.conferences c ON c.id=s.conference_id
   WHERE BINARY c.short_name=BINARY @ieir_short_name)
FROM conf_stage s JOIN conferences c ON c.id=s.conference_id
WHERE BINARY c.short_name=BINARY @ieir_short_name
UNION ALL SELECT 'ieir_tasks', COUNT(*),
  (SELECT COUNT(*) FROM legacy_source.conf_task s
   JOIN legacy_source.conferences c ON c.id=s.conference_id
   WHERE BINARY c.short_name=BINARY @ieir_short_name)
FROM conf_task t JOIN conferences c ON c.id=t.conference_id
WHERE BINARY c.short_name=BINARY @ieir_short_name
UNION ALL SELECT 'ieir_members', COUNT(*),
  (SELECT COUNT(*) FROM legacy_source.conf_member s
   JOIN legacy_source.conferences c ON c.id=s.conf_id
   WHERE BINARY c.short_name=BINARY @ieir_short_name)
FROM conf_member m JOIN conferences c ON c.id=m.conf_id
WHERE BINARY c.short_name=BINARY @ieir_short_name
UNION ALL SELECT 'ieir_discovery_jobs', COUNT(*),
  (SELECT COUNT(*) FROM legacy_source.conf_author_discovery_job s
   JOIN legacy_source.conferences c ON c.id=s.conference_id
   WHERE BINARY c.short_name=BINARY @ieir_short_name)
FROM conf_author_discovery_job j JOIN conferences c ON c.id=j.conference_id
WHERE BINARY c.short_name=BINARY @ieir_short_name
UNION ALL SELECT 'ieir_potential_authors', COUNT(*),
  (SELECT COUNT(*) FROM legacy_source.conf_potential_author s
   JOIN legacy_source.conferences c ON c.id=s.conference_id
   WHERE BINARY c.short_name=BINARY @ieir_short_name)
FROM conf_potential_author a JOIN conferences c ON c.id=a.conference_id
WHERE BINARY c.short_name=BINARY @ieir_short_name
UNION ALL SELECT 'ieir_author_email_sources', COUNT(*),
  (SELECT COUNT(*) FROM legacy_source.conf_author_email_source es
   JOIN legacy_source.conf_potential_author pa ON pa.id=es.potential_author_id
   JOIN legacy_source.conferences c ON c.id=pa.conference_id
   WHERE BINARY c.short_name=BINARY @ieir_short_name)
FROM conf_author_email_source es
JOIN conf_potential_author pa ON pa.id=es.potential_author_id
JOIN conferences c ON c.id=pa.conference_id
WHERE BINARY c.short_name=BINARY @ieir_short_name
UNION ALL SELECT 'ieir_task_logs', COUNT(*),
  (SELECT COUNT(*) FROM legacy_source.conf_task_log s
   JOIN legacy_source.conferences c ON c.id=s.conference_id
   WHERE BINARY c.short_name=BINARY @ieir_short_name)
FROM conf_task_log l JOIN conferences c ON c.id=l.conference_id
WHERE BINARY c.short_name=BINARY @ieir_short_name
UNION ALL SELECT 'ieir_attachments', COUNT(*),
  (SELECT COUNT(*) FROM legacy_source.conf_task_attachment s
   JOIN legacy_source.conferences c ON c.id=s.conference_id
   WHERE BINARY c.short_name=BINARY @ieir_short_name)
FROM conf_task_attachment a JOIN conferences c ON c.id=a.conference_id
WHERE BINARY c.short_name=BINARY @ieir_short_name
UNION ALL SELECT 'ieir_invitations', COUNT(*),
  (SELECT COUNT(*) FROM legacy_source.conf_member_invitation s
   JOIN legacy_source.conferences c ON c.id=s.conference_id
   WHERE BINARY c.short_name=BINARY @ieir_short_name)
FROM conf_member_invitation i JOIN conferences c ON c.id=i.conference_id
WHERE BINARY c.short_name=BINARY @ieir_short_name
UNION ALL SELECT 'ieir_historical_mail_logs', COUNT(*),
  (SELECT COUNT(*) FROM legacy_source.mail_send_log s
   JOIN legacy_source.conferences c ON c.id=s.conference_id
   WHERE BINARY c.short_name=BINARY @ieir_short_name)
FROM mail_send_log l JOIN conferences c ON c.id=l.conference_id
WHERE BINARY c.short_name=BINARY @ieir_short_name
UNION ALL SELECT 'ieir_smtp_accounts', COUNT(*), 0
FROM conference_mail_account a JOIN conferences c ON c.id=a.conference_id
WHERE BINARY c.short_name=BINARY @ieir_short_name
UNION ALL SELECT 'ieir_send_queue', COUNT(*), 0
FROM mail_send_task q JOIN conferences c ON c.id=q.conference_id
WHERE BINARY c.short_name=BINARY @ieir_short_name;

-- Every returned orphan count must be zero.
SELECT 'orphan_stage_conference' AS check_name, COUNT(*) AS orphan_count
FROM conf_stage s LEFT JOIN conferences c ON c.id=s.conference_id
WHERE c.id IS NULL
UNION ALL SELECT 'orphan_stage_definition', COUNT(*)
FROM conf_stage s LEFT JOIN conf_stage_def d ON d.id=s.stage_def_id
WHERE d.id IS NULL
UNION ALL SELECT 'orphan_task_conference', COUNT(*)
FROM conf_task t LEFT JOIN conferences c ON c.id=t.conference_id
WHERE c.id IS NULL
UNION ALL SELECT 'orphan_task_stage', COUNT(*)
FROM conf_task t LEFT JOIN conf_stage s ON s.id=t.stage_id
WHERE s.id IS NULL
UNION ALL SELECT 'orphan_task_definition', COUNT(*)
FROM conf_task t LEFT JOIN conf_task_def d ON d.id=t.task_def_id
WHERE t.task_def_id IS NOT NULL AND d.id IS NULL
UNION ALL SELECT 'orphan_member_contact', COUNT(*)
FROM conf_member m LEFT JOIN conf_contact_pool p ON p.id=m.contact_id
WHERE p.id IS NULL
UNION ALL SELECT 'orphan_author_job', COUNT(*)
FROM conf_potential_author a
LEFT JOIN conf_author_discovery_job j ON j.id=a.discovery_job_id
WHERE a.discovery_job_id IS NOT NULL AND j.id IS NULL
UNION ALL SELECT 'orphan_author_email_source', COUNT(*)
FROM conf_author_email_source s
LEFT JOIN conf_potential_author a ON a.id=s.potential_author_id
WHERE s.potential_author_id IS NOT NULL AND a.id IS NULL
UNION ALL SELECT 'orphan_task_log', COUNT(*)
FROM conf_task_log l LEFT JOIN conf_task t ON t.id=l.task_id
WHERE t.id IS NULL
UNION ALL SELECT 'orphan_invitation_task', COUNT(*)
FROM conf_member_invitation i LEFT JOIN conf_task t ON t.id=i.source_task_id
WHERE i.source_task_id IS NOT NULL AND t.id IS NULL;

-- Show the records whose exact values must remain unchanged across the clone
-- rehearsal and production merge. Compare these with the pre-migration capture.
SELECT id, username, password, email, phone, real_name, delete_time,
       create_time, update_time, del_flag
FROM sys_user WHERE username='admin';
SELECT * FROM conferences WHERE short_name='dn';
