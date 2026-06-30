-- Users table
CREATE TABLE IF NOT EXISTS users (
    id            BIGSERIAL PRIMARY KEY,
    first_name    VARCHAR(64),
    last_name     VARCHAR(64),
    username      VARCHAR(64)  NOT NULL,
    password_hash VARCHAR(255),
    invite_token  VARCHAR(64)  UNIQUE,
    role          VARCHAR(16)  NOT NULL
                      CONSTRAINT users_role_check
                          CHECK (role IN ('ADMIN', 'USER')),
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT users_username_unique UNIQUE (username)
);

-- System settings table (key/value store for app-wide config)
CREATE TABLE IF NOT EXISTS system_settings (
    key   VARCHAR(64) PRIMARY KEY,
    value TEXT
);