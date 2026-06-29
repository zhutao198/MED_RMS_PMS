-- DDL 138: 通知多渠道（v1.46 P1-后端-4）
-- 2026-06-08 zhutao
--
-- 目标：除站内信（IN_APP）外，支持 EMAIL / DINGTALK / WECHAT_WORK / FEISHU 推送。
-- 设计：
--   t_notification_channel - 系统级频道配置（webhook/AppKey/AppSecret 等）
--   t_im_queue             - IM 推送日志（与 t_email_queue 对齐，便于 SLA/重试/对账）
--
-- 初始化 4 个内置频道：EMAIL / DINGTALK / WECHAT_WORK / FEISHU，webhook 留空，
-- 由运维在「系统管理 → 通知渠道」后台填入；启用开关默认 EMAIL=TRUE，其余 FALSE。

CREATE TABLE IF NOT EXISTS not_schema.t_notification_channel (
    id               BIGSERIAL    PRIMARY KEY,
    channel_code     VARCHAR(40)  NOT NULL UNIQUE,
    channel_name     VARCHAR(100) NOT NULL,
    channel_type     VARCHAR(20)  NOT NULL,                    -- EMAIL/DINGTALK/WECHAT_WORK/FEISHU
    webhook_url      VARCHAR(500),
    app_key          VARCHAR(200),
    app_secret       VARCHAR(500),
    is_enabled       BOOLEAN      NOT NULL DEFAULT FALSE,
    rate_limit_per_min INTEGER     NOT NULL DEFAULT 60,
    description      VARCHAR(500),
    is_deleted       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_notification_channel_type
    ON not_schema.t_notification_channel(channel_type) WHERE is_deleted = FALSE;

-- 预置 4 个内置频道
INSERT INTO not_schema.t_notification_channel
    (channel_code, channel_name, channel_type, is_enabled, rate_limit_per_min, description)
VALUES
    ('EMAIL_DEFAULT',  '系统邮件',     'EMAIL',       TRUE,  120, '站内信之外的标准邮件通道（SMTP 由运维配置）'),
    ('DINGTALK_GROUP', '钉钉群机器人', 'DINGTALK',    FALSE, 20,  '钉钉自定义机器人 Webhook（加签模式见 secret）'),
    ('WECHAT_WORK',    '企业微信',     'WECHAT_WORK', FALSE, 30,  '企业微信自建应用，需配置 corpId/agentId'),
    ('FEISHU',         '飞书机器人',   'FEISHU',      FALSE, 30,  '飞书自定义机器人 Webhook（加签见 secret）')
ON CONFLICT (channel_code) DO NOTHING;

-- IM 队列（与 t_email_queue 结构对齐：status/retryCount/sentAt/errorMessage）
CREATE TABLE IF NOT EXISTS not_schema.t_im_queue (
    id               BIGSERIAL    PRIMARY KEY,
    channel_code     VARCHAR(40)  NOT NULL,                    -- 关联 t_notification_channel.channel_code
    target           VARCHAR(200) NOT NULL,                    -- 用户 openId / unionId / 手机号
    target_type      VARCHAR(20)  NOT NULL DEFAULT 'USER',     -- USER/GROUP
    title            VARCHAR(200),
    content          TEXT         NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING',  -- PENDING/SENT/FAILED
    retry_count      INTEGER      NOT NULL DEFAULT 0,
    error_message    TEXT,
    related_user_id  BIGINT,
    related_type     VARCHAR(40),                              -- REVIEW_REJECTED/TRACE_BROKEN/RISK_ALERT/...
    related_id       BIGINT,
    sent_at          TIMESTAMP,
    is_deleted       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_im_queue_status ON not_schema.t_im_queue(status, retry_count)
    WHERE is_deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_im_queue_channel ON not_schema.t_im_queue(channel_code, created_at DESC)
    WHERE is_deleted = FALSE;

SELECT 'DDL 138: notification channels + IM queue ready' AS status;
