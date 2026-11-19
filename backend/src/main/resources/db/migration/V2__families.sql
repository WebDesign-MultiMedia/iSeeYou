-- V2: family circles and their memberships.

CREATE TABLE families (
    id          BINARY(16)   NOT NULL,
    name        VARCHAR(100) NOT NULL,
    -- Short human-typeable code used to join (see InviteCodeGenerator).
    invite_code VARCHAR(16)  NOT NULL,
    created_by  BINARY(16)   NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    CONSTRAINT pk_families PRIMARY KEY (id),
    CONSTRAINT uk_families_invite_code UNIQUE (invite_code),
    CONSTRAINT fk_families_created_by FOREIGN KEY (created_by) REFERENCES users (id)
);

CREATE TABLE family_members (
    id              BINARY(16)  NOT NULL,
    family_id       BINARY(16)  NOT NULL,
    user_id         BINARY(16)  NOT NULL,
    role            VARCHAR(10) NOT NULL,
    -- Every member can pause sharing; others then see them as "paused" (step 5).
    sharing_enabled BOOLEAN     NOT NULL DEFAULT TRUE,
    joined_at       DATETIME(6) NOT NULL,
    CONSTRAINT pk_family_members PRIMARY KEY (id),
    -- A user belongs to a given circle at most once.
    CONSTRAINT uk_family_members_family_user UNIQUE (family_id, user_id),
    CONSTRAINT fk_family_members_family FOREIGN KEY (family_id) REFERENCES families (id) ON DELETE CASCADE,
    CONSTRAINT fk_family_members_user   FOREIGN KEY (user_id)   REFERENCES users (id)    ON DELETE CASCADE,
    CONSTRAINT ck_family_members_role CHECK (role IN ('ADMIN', 'MEMBER'))
);

-- "Which circles am I in?" is the hottest query; the unique key above already covers family_id lookups.
CREATE INDEX ix_family_members_user ON family_members (user_id);
