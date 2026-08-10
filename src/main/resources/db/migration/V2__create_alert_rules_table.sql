CREATE TABLE alert_rules (
    id CHAR(36) PRIMARY KEY,
    project_id CHAR(36) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(2000),
    metric_type VARCHAR(32) NOT NULL,
    threshold_value DECIMAL(20, 6) NOT NULL,
    comparison_operator VARCHAR(32) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    enabled BOOLEAN NOT NULL,
    device_id VARCHAR(128),
    service_id CHAR(36),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT chk_alert_rules_target CHECK (device_id IS NULL OR service_id IS NULL)
);

CREATE INDEX idx_alert_rules_project ON alert_rules (project_id);
CREATE INDEX idx_alert_rules_project_enabled ON alert_rules (project_id, enabled);
CREATE INDEX idx_alert_rules_project_updated ON alert_rules (project_id, updated_at);