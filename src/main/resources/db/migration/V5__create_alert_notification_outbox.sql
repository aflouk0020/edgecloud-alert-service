CREATE TABLE alert_notification_outbox (
    id CHAR(36) PRIMARY KEY,
    source_event_id CHAR(36) NOT NULL,
    alert_event_id CHAR(36) NOT NULL,
    project_id CHAR(36) NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    rule_name VARCHAR(200) NOT NULL,
    metric_type VARCHAR(64) NOT NULL,
    source_type VARCHAR(64) NOT NULL,
    source_id VARCHAR(128) NOT NULL,
    observed_value DECIMAL(30,10),
    threshold_value DECIMAL(30,10),
    occurred_at TIMESTAMP(6) NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP(6),
    last_attempt_at TIMESTAMP(6),
    published_at TIMESTAMP(6),
    processing_started_at TIMESTAMP(6),
    failure_category VARCHAR(64),
    failure_message VARCHAR(1000),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_alert_notification_source UNIQUE (source_event_id),
    CONSTRAINT uk_alert_notification_transition UNIQUE (alert_event_id, event_type, occurred_at),
    CONSTRAINT chk_alert_notification_event_type
        CHECK (event_type IN ('OPENED', 'ACKNOWLEDGED', 'RESOLVED')),
    CONSTRAINT chk_alert_notification_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'RETRY_SCHEDULED', 'PUBLISHED', 'FAILED')),
    CONSTRAINT chk_alert_notification_attempt_count CHECK (attempt_count >= 0),
    CONSTRAINT fk_alert_notification_event FOREIGN KEY (alert_event_id) REFERENCES alert_events (id)
);

CREATE INDEX idx_alert_notification_due
    ON alert_notification_outbox (status, next_attempt_at, id);
CREATE INDEX idx_alert_notification_processing
    ON alert_notification_outbox (processing_started_at);
CREATE INDEX idx_alert_notification_event
    ON alert_notification_outbox (alert_event_id);
CREATE INDEX idx_alert_notification_project_created
    ON alert_notification_outbox (project_id, created_at);
