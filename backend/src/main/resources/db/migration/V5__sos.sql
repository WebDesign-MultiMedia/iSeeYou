-- V5: SOS alerts. One alert goes to every circle the sender belongs to at that moment.

CREATE TABLE sos_alerts (
    id          BINARY(16)  NOT NULL,
    user_id     BINARY(16)  NOT NULL,
    -- Location is optional: an SOS without a GPS fix is still an SOS.
    lat         DOUBLE      NULL,
    lng         DOUBLE      NULL,
    accuracy    DOUBLE      NULL,
    battery     INT         NULL,
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    resolved_at DATETIME(6) NULL,
    resolved_by BINARY(16)  NULL,
    CONSTRAINT pk_sos_alerts PRIMARY KEY (id),
    CONSTRAINT fk_sos_alerts_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_sos_alerts_resolved_by FOREIGN KEY (resolved_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_sos_alerts_lat CHECK (lat IS NULL OR lat BETWEEN -90 AND 90),
    CONSTRAINT ck_sos_alerts_lng CHECK (lng IS NULL OR lng BETWEEN -180 AND 180)
);
-- "Does this user already have an open SOS?" and the retention purge.
CREATE INDEX ix_sos_alerts_user_resolved ON sos_alerts (user_id, resolved_at);
CREATE INDEX ix_sos_alerts_created ON sos_alerts (created_at);

-- Which circles were alerted. Kept even if the sender later leaves, so helpers keep access.
CREATE TABLE sos_alert_circles (
    alert_id  BINARY(16) NOT NULL,
    family_id BINARY(16) NOT NULL,
    CONSTRAINT pk_sos_alert_circles PRIMARY KEY (alert_id, family_id),
    CONSTRAINT fk_sos_alert_circles_alert  FOREIGN KEY (alert_id)  REFERENCES sos_alerts (id) ON DELETE CASCADE,
    CONSTRAINT fk_sos_alert_circles_family FOREIGN KEY (family_id) REFERENCES families (id)   ON DELETE CASCADE
);
CREATE INDEX ix_sos_alert_circles_family ON sos_alert_circles (family_id);
