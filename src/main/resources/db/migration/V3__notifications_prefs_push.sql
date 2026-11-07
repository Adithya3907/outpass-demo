-- Notification categories, per-user preferences, browser/phone push subscriptions, app settings (VAPID keys)
ALTER TABLE notification ADD COLUMN type VARCHAR(30) NOT NULL DEFAULT 'INFO';

CREATE TABLE notification_pref (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    type VARCHAR(30) NOT NULL,
    push_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    email_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_pref UNIQUE (user_id, type)
);

CREATE TABLE push_subscription (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    endpoint TEXT NOT NULL UNIQUE,
    p256dh VARCHAR(200) NOT NULL,
    auth VARCHAR(100) NOT NULL,
    user_agent VARCHAR(250),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_push_user ON push_subscription(user_id);

CREATE TABLE app_setting (
    setting_key VARCHAR(100) PRIMARY KEY,
    setting_value TEXT NOT NULL
);
