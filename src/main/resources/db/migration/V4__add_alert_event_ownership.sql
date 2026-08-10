ALTER TABLE alert_events DROP CONSTRAINT chk_alert_events_status;
ALTER TABLE alert_events DROP CONSTRAINT uk_alert_events_open;
ALTER TABLE alert_events DROP COLUMN open_marker;

ALTER TABLE alert_events ADD COLUMN owner_user_id CHAR(36);
ALTER TABLE alert_events ADD COLUMN owner_display_name VARCHAR(200);
ALTER TABLE alert_events ADD COLUMN acknowledged_at TIMESTAMP(6);
ALTER TABLE alert_events ADD COLUMN ownership_changed_at TIMESTAMP(6);
ALTER TABLE alert_events ADD COLUMN open_marker TINYINT GENERATED ALWAYS AS (
        CASE WHEN status IN ('OPEN', 'ACKNOWLEDGED') THEN 1 ELSE NULL END
    );

ALTER TABLE alert_events ADD CONSTRAINT chk_alert_events_status
    CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'RESOLVED'));
ALTER TABLE alert_events ADD CONSTRAINT uk_alert_events_open UNIQUE (
        project_id,
        alert_rule_id,
        source_type,
        source_id,
        metric_type,
        open_marker
    );

CREATE INDEX idx_alert_events_project_owner
    ON alert_events (project_id, owner_user_id, triggered_at DESC, id ASC);

CREATE TABLE alert_event_ownership_history (
    id CHAR(36) PRIMARY KEY,
    alert_event_id CHAR(36) NOT NULL,
    project_id CHAR(36) NOT NULL,
    actor_user_id CHAR(36) NOT NULL,
    owner_user_id CHAR(36),
    owner_display_name VARCHAR(200),
    action VARCHAR(24) NOT NULL,
    changed_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT chk_alert_ownership_action CHECK (action IN ('ACKNOWLEDGED', 'RELEASED')),
    CONSTRAINT fk_alert_ownership_event FOREIGN KEY (alert_event_id) REFERENCES alert_events (id)
);

CREATE INDEX idx_alert_ownership_event_changed
    ON alert_event_ownership_history (alert_event_id, changed_at ASC, id ASC);
CREATE INDEX idx_alert_ownership_project_event_changed
    ON alert_event_ownership_history (project_id, alert_event_id, changed_at ASC, id ASC);
