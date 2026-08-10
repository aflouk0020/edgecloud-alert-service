CREATE TABLE alert_events (
    id CHAR(36) PRIMARY KEY,
    alert_rule_id CHAR(36) NOT NULL,
    alert_rule_name VARCHAR(200) NOT NULL,
    project_id CHAR(36) NOT NULL,
    source_type VARCHAR(16) NOT NULL,
    source_id VARCHAR(128) NOT NULL,
    metric_type VARCHAR(32) NOT NULL,
    observed_value DECIMAL(20, 6) NOT NULL,
    threshold_value DECIMAL(20, 6) NOT NULL,
    comparison_operator VARCHAR(32) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    triggered_at TIMESTAMP(6) NOT NULL,
    last_observed_at TIMESTAMP(6) NOT NULL,
    resolved_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    open_marker TINYINT GENERATED ALWAYS AS (
        CASE WHEN status = 'OPEN' THEN 1 ELSE NULL END
    ),
    CONSTRAINT chk_alert_events_status CHECK (status IN ('OPEN', 'RESOLVED')),
    CONSTRAINT uk_alert_events_open UNIQUE (
        project_id,
        alert_rule_id,
        source_type,
        source_id,
        metric_type,
        open_marker
    )
);

CREATE INDEX idx_alert_events_project_status
    ON alert_events (project_id, status, triggered_at DESC, id ASC);
CREATE INDEX idx_alert_events_project_severity
    ON alert_events (project_id, severity, triggered_at DESC, id ASC);
CREATE INDEX idx_alert_events_project_triggered
    ON alert_events (project_id, triggered_at DESC, id ASC);
CREATE INDEX idx_alert_events_source
    ON alert_events (source_type, source_id);
CREATE INDEX idx_alert_events_rule_status
    ON alert_events (alert_rule_id, status);
