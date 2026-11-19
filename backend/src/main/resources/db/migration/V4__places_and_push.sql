-- V4: saved places (geofences), who is currently inside each, and push tokens.

CREATE TABLE places (
    id         BINARY(16)   NOT NULL,
    family_id  BINARY(16)   NOT NULL,
    name       VARCHAR(60)  NOT NULL,
    lat        DOUBLE       NOT NULL,
    lng        DOUBLE       NOT NULL,
    -- Geofence radius in meters (50-2000, enforced by the API).
    radius_m   INT          NOT NULL,
    created_by BINARY(16)   NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    CONSTRAINT pk_places PRIMARY KEY (id),
    CONSTRAINT fk_places_family FOREIGN KEY (family_id) REFERENCES families (id) ON DELETE CASCADE,
    CONSTRAINT ck_places_lat CHECK (lat BETWEEN -90 AND 90),
    CONSTRAINT ck_places_lng CHECK (lng BETWEEN -180 AND 180),
    CONSTRAINT ck_places_radius CHECK (radius_m BETWEEN 50 AND 2000)
);
CREATE INDEX ix_places_family ON places (family_id);

-- Last known inside/outside state per (place, user). Only transitions produce events, so a
-- member standing at home doesn't trigger "arrived" on every point.
CREATE TABLE place_presence (
    place_id   BINARY(16)  NOT NULL,
    user_id    BINARY(16)  NOT NULL,
    inside     BOOLEAN     NOT NULL,
    changed_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_place_presence PRIMARY KEY (place_id, user_id),
    CONSTRAINT fk_place_presence_place FOREIGN KEY (place_id) REFERENCES places (id) ON DELETE CASCADE,
    CONSTRAINT fk_place_presence_user  FOREIGN KEY (user_id)  REFERENCES users (id)  ON DELETE CASCADE
);
CREATE INDEX ix_place_presence_user ON place_presence (user_id);

-- Expo push tokens. One user may have several devices; a token belongs to one user at a time
-- (signing in as someone else on the same phone moves it).
CREATE TABLE push_tokens (
    token      VARCHAR(255) NOT NULL,
    user_id    BINARY(16)   NOT NULL,
    platform   VARCHAR(10)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    CONSTRAINT pk_push_tokens PRIMARY KEY (token),
    CONSTRAINT fk_push_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_push_tokens_platform CHECK (platform IN ('ios', 'android'))
);
CREATE INDEX ix_push_tokens_user ON push_tokens (user_id);
