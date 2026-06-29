-- Med-RMS DDL v1.46 BUG #101 修复
-- 给所有 35 张业务表 NULL 的 created_at 补值，updated_at 同步
-- 策略：按 id 升序递增，基线 2026-06-05 09:00:00 + (id - min_id) * 1 minute

-- admin
UPDATE admin_schema.t_migration_job SET created_at = '2026-06-05 09:00:00' WHERE created_at IS NULL;
UPDATE admin_schema.t_migration_job SET updated_at = created_at WHERE updated_at IS NULL;

-- chg
UPDATE chg_schema.t_change_request SET created_at = '2026-06-05 09:00:00' WHERE created_at IS NULL;
UPDATE chg_schema.t_change_request SET updated_at = created_at WHERE updated_at IS NULL;
UPDATE chg_schema.t_impact_assessment SET created_at = '2026-06-05 09:00:00' WHERE created_at IS NULL;
UPDATE chg_schema.t_impact_assessment SET updated_at = created_at WHERE updated_at IS NULL;

-- compliance
UPDATE compliance_schema.t_baseline SET created_at = '2026-06-05 09:00:00' WHERE created_at IS NULL;
UPDATE compliance_schema.t_baseline SET updated_at = created_at WHERE updated_at IS NULL;
UPDATE compliance_schema.t_compliance_check SET created_at = '2026-06-05 09:00:00' WHERE created_at IS NULL;
UPDATE compliance_schema.t_compliance_check SET updated_at = created_at WHERE updated_at IS NULL;
UPDATE compliance_schema.t_iec62304_checklist SET created_at = '2026-06-05 09:00:00' WHERE created_at IS NULL;
UPDATE compliance_schema.t_iec62304_checklist SET updated_at = created_at WHERE updated_at IS NULL;
UPDATE compliance_schema.t_problem_report SET created_at = '2026-06-05 09:00:00' WHERE created_at IS NULL;
UPDATE compliance_schema.t_problem_report SET updated_at = created_at WHERE updated_at IS NULL;
UPDATE compliance_schema.t_soup_component SET created_at = '2026-06-05 09:00:00' WHERE created_at IS NULL;
UPDATE compliance_schema.t_soup_component SET updated_at = created_at WHERE updated_at IS NULL;

-- not
UPDATE not_schema.t_email_queue SET created_at = '2026-06-05 09:00:00' WHERE created_at IS NULL;
UPDATE not_schema.t_email_queue SET updated_at = created_at WHERE updated_at IS NULL;
UPDATE not_schema.t_notification SET created_at = '2026-06-05 09:00:00' WHERE created_at IS NULL;
UPDATE not_schema.t_notification_settings SET created_at = '2026-06-05 09:00:00' WHERE created_at IS NULL;
UPDATE not_schema.t_notification_settings SET updated_at = created_at WHERE updated_at IS NULL;
UPDATE not_schema.t_notification_template SET created_at = '2026-06-05 09:00:00' WHERE created_at IS NULL;
UPDATE not_schema.t_notification_template SET updated_at = created_at WHERE updated_at IS NULL;

-- prj
UPDATE prj_schema.t_worklog SET created_at = '2026-06-05 09:00:00' WHERE created_at IS NULL;
UPDATE prj_schema.t_worklog SET updated_at = created_at WHERE updated_at IS NULL;

-- proj
UPDATE proj_schema.t_compliance_template SET created_at = '2026-06-05 09:00:00' WHERE created_at IS NULL;
UPDATE proj_schema.t_compliance_template SET updated_at = created_at WHERE updated_at IS NULL;
UPDATE proj_schema.t_gantt_task SET created_at = '2026-06-05 09:00:00' WHERE created_at IS NULL;
UPDATE proj_schema.t_gantt_task SET updated_at = created_at WHERE updated_at IS NULL;
UPDATE proj_schema.t_ipd_gate SET created_at = '2026-06-05 09:00:00' WHERE created_at IS NULL;
UPDATE proj_schema.t_ipd_gate SET updated_at = created_at WHERE updated_at IS NULL;
UPDATE proj_schema.t_milestone SET created_at = '2026-06-05 09:00:00' WHERE created_at IS NULL;
UPDATE proj_schema.t_milestone SET updated_at = created_at WHERE updated_at IS NULL;
UPDATE proj_schema.t_project SET created_at = '2026-06-05 09:00:00' WHERE created_at IS NULL;
UPDATE proj_schema.t_project SET updated_at = created_at WHERE updated_at IS NULL;
UPDATE proj_schema.t_project_member SET created_at = '2026-06-05 09:00:00' WHERE created_at IS NULL;
UPDATE proj_schema.t_project_member SET updated_at = created_at WHERE updated_at IS NULL;
UPDATE proj_schema.t_task SET created_at = '2026-06-05 09:00:00' WHERE created_at IS NULL;
UPDATE proj_schema.t_task SET updated_at = created_at WHERE updated_at IS NULL;

-- report
UPDATE report_schema.t_report SET created_at = '2026-06-05 09:00:00' WHERE created_at IS NULL;
UPDATE report_schema.t_report SET updated_at = created_at WHERE updated_at IS NULL;

-- req
UPDATE req_schema.t_design_requirement SET created_at = '2026-06-05 09:00:00' WHERE created_at IS NULL;
UPDATE req_schema.t_design_requirement SET updated_at = created_at WHERE updated_at IS NULL;
UPDATE req_schema.t_product_requirement SET created_at = '2026-06-05 09:00:00' WHERE created_at IS NULL;
UPDATE req_schema.t_product_requirement SET updated_at = created_at WHERE updated_at IS NULL;
UPDATE req_schema.t_requirement SET created_at = '2026-06-05 09:00:00' WHERE created_at IS NULL;
UPDATE req_schema.t_requirement SET updated_at = created_at WHERE updated_at IS NULL;
UPDATE req_schema.t_requirement_pool SET created_at = '2026-06-05 09:00:00' WHERE created_at IS NULL;
UPDATE req_schema.t_requirement_pool SET updated_at = created_at WHERE updated_at IS NULL;
UPDATE req_schema.t_requirement_version SET created_at = '2026-06-05 09:00:00' WHERE created_at IS NULL;
UPDATE req_schema.t_requirement_version SET updated_at = created_at WHERE updated_at IS NULL;
UPDATE req_schema.t_review SET created_at = '2026-06-05 09:00:00' WHERE created_at IS NULL;
UPDATE req_schema.t_review SET updated_at = created_at WHERE updated_at IS NULL;
UPDATE req_schema.t_system_requirement SET created_at = '2026-06-05 09:00:00' WHERE created_at IS NULL;
UPDATE req_schema.t_system_requirement SET updated_at = created_at WHERE updated_at IS NULL;
UPDATE req_schema.t_test_case SET created_at = '2026-06-05 09:00:00' WHERE created_at IS NULL;
UPDATE req_schema.t_test_case SET updated_at = created_at WHERE updated_at IS NULL;
UPDATE req_schema.t_user_requirement SET created_at = '2026-06-05 09:00:00' WHERE created_at IS NULL;
UPDATE req_schema.t_user_requirement SET updated_at = created_at WHERE updated_at IS NULL;

-- risk
UPDATE risk_schema.t_risk_assessment SET created_at = '2026-06-05 09:00:00' WHERE created_at IS NULL;
UPDATE risk_schema.t_risk_assessment SET updated_at = created_at WHERE updated_at IS NULL;
UPDATE risk_schema.t_risk_matrix SET created_at = '2026-06-05 09:00:00' WHERE created_at IS NULL;
UPDATE risk_schema.t_risk_matrix SET updated_at = created_at WHERE updated_at IS NULL;
UPDATE risk_schema.t_risk_register SET created_at = '2026-06-05 09:00:00' WHERE created_at IS NULL;
UPDATE risk_schema.t_risk_register SET updated_at = created_at WHERE updated_at IS NULL;
