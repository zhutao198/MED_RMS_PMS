-- Med-RMS DDL v1.46 BUG #98 修复
-- t_email_queue 和 t_notification_settings 表与实体字段不同步，补齐缺失列

-- t_email_queue 补充字段
ALTER TABLE not_schema.t_email_queue ADD COLUMN IF NOT EXISTS cc_address VARCHAR(200);
ALTER TABLE not_schema.t_email_queue ADD COLUMN IF NOT EXISTS body TEXT;
ALTER TABLE not_schema.t_email_queue ADD COLUMN IF NOT EXISTS error_message TEXT;
ALTER TABLE not_schema.t_email_queue ADD COLUMN IF NOT EXISTS scheduled_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE not_schema.t_email_queue ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN DEFAULT FALSE;
ALTER TABLE not_schema.t_email_queue ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

-- t_notification_settings 补充字段
ALTER TABLE not_schema.t_notification_settings ADD COLUMN IF NOT EXISTS in_app_enabled BOOLEAN DEFAULT TRUE;
ALTER TABLE not_schema.t_notification_settings ADD COLUMN IF NOT EXISTS sms_enabled BOOLEAN DEFAULT FALSE;
ALTER TABLE not_schema.t_notification_settings ADD COLUMN IF NOT EXISTS wechat_enabled BOOLEAN DEFAULT FALSE;
ALTER TABLE not_schema.t_notification_settings ADD COLUMN IF NOT EXISTS email_address VARCHAR(200);
ALTER TABLE not_schema.t_notification_settings ADD COLUMN IF NOT EXISTS phone_number VARCHAR(50);
ALTER TABLE not_schema.t_notification_settings ADD COLUMN IF NOT EXISTS wechat_open_id VARCHAR(200);
ALTER TABLE not_schema.t_notification_settings ADD COLUMN IF NOT EXISTS digest_mode VARCHAR(20) DEFAULT 'INSTANT';
