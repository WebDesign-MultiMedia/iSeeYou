-- V1: user accounts.
-- UUIDs are stored as BINARY(16) (compact, index-friendly) rather than CHAR(36).
CREATE TABLE users (
    id            BINARY(16)   NOT NULL,
    name          VARCHAR(100) NOT NULL,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    avatar_url    VARCHAR(512) NULL,
    created_at    DATETIME(6)  NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email)
);
