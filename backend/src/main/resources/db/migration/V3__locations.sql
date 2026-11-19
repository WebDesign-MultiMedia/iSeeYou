-- V3: location points reported by members' devices.
-- High-volume, append-only table: BIGINT auto-increment keys keep inserts cheap and the
-- clustered index compact. Rows older than 7 days are purged (step 7).
CREATE TABLE locations (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    user_id     BINARY(16)  NOT NULL,
    lat         DOUBLE      NOT NULL,
    lng         DOUBLE      NOT NULL,
    -- Horizontal accuracy radius in meters, as reported by the OS.
    accuracy    DOUBLE      NULL,
    -- Battery level 0-100, so family can tell "phone died" from "stopped moving".
    battery     INT         NULL,
    -- When the device took the fix (may be earlier than receipt if it was offline).
    recorded_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_locations PRIMARY KEY (id),
    CONSTRAINT fk_locations_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_locations_lat CHECK (lat BETWEEN -90 AND 90),
    CONSTRAINT ck_locations_lng CHECK (lng BETWEEN -180 AND 180),
    CONSTRAINT ck_locations_battery CHECK (battery IS NULL OR battery BETWEEN 0 AND 100)
);

-- Serves both "latest point per member" and the time-based purge.
CREATE INDEX ix_locations_user_recorded ON locations (user_id, recorded_at);
CREATE INDEX ix_locations_recorded ON locations (recorded_at);
