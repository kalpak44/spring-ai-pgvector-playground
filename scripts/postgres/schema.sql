-- =================================================
-- Extensions
-- =================================================

CREATE EXTENSION IF NOT EXISTS vector;


-- =================================================
-- Users
-- =================================================

CREATE TABLE IF NOT EXISTS users (
    id            BIGSERIAL    PRIMARY KEY,
    first_name    VARCHAR(64),
    last_name     VARCHAR(64),
    username      VARCHAR(64)  NOT NULL,
    password_hash VARCHAR(255),
    invite_token  VARCHAR(64)  UNIQUE,
    role          VARCHAR(16)  NOT NULL
                      CONSTRAINT users_role_check
                          CHECK (role IN ('ADMIN', 'USER')),
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    language      VARCHAR(8)   NOT NULL DEFAULT 'en',
    timezone      VARCHAR(64)  NOT NULL DEFAULT 'Europe/Sofia',
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT users_username_unique UNIQUE (username)
);


-- =================================================
-- Chat
-- =================================================

CREATE TABLE IF NOT EXISTS chat (
    id         BIGSERIAL    PRIMARY KEY,
    title      VARCHAR(255),
    user_id    BIGINT       REFERENCES users(id),
    created_at TIMESTAMP(6)
);

CREATE TABLE IF NOT EXISTS chat_entry (
    id         BIGSERIAL    PRIMARY KEY,
    content    TEXT,
    role       VARCHAR(255)
                   CONSTRAINT chat_entry_role_check
                       CHECK (role IN ('USER', 'ASSISTANT')),
    chat_id    BIGINT       REFERENCES chat(id),
    created_at TIMESTAMP(6)
);


-- =================================================
-- RAG
-- =================================================

CREATE TABLE IF NOT EXISTS loaded_document (
    id            SERIAL       PRIMARY KEY,
    filename      VARCHAR(255) NOT NULL,
    content_hash  VARCHAR(64)  NOT NULL,
    document_type VARCHAR(10)  NOT NULL,
    chunk_count   INTEGER,
    loaded_at     TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT unique_document UNIQUE (filename, content_hash)
);

CREATE INDEX IF NOT EXISTS idx_loaded_documents_filename
    ON loaded_document(filename);

CREATE TABLE IF NOT EXISTS vector_store (
    id        VARCHAR(255) PRIMARY KEY,
    content   TEXT,
    metadata  JSON,
    embedding VECTOR(1024)
);

CREATE INDEX IF NOT EXISTS vector_store_hnsw_index
    ON vector_store USING hnsw (embedding vector_cosine_ops);


-- =================================================
-- Google OAuth tokens
-- =================================================

CREATE TABLE IF NOT EXISTS user_google_tokens (
    user_id        BIGINT       PRIMARY KEY REFERENCES users(id),
    access_token   TEXT         NOT NULL,
    refresh_token  TEXT,
    token_expiry   TIMESTAMP,
    google_email   VARCHAR(255),
    granted_scopes TEXT,
    connected_at   TIMESTAMP    DEFAULT NOW()
);


-- =================================================
-- Board Module
-- =================================================

CREATE TABLE IF NOT EXISTS board (
    id          BIGSERIAL    PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    owner_id    BIGINT       NOT NULL REFERENCES users(id),
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_board_owner_id
    ON board(owner_id);


CREATE TABLE IF NOT EXISTS board_member (
    id        BIGSERIAL   PRIMARY KEY,
    board_id  BIGINT      NOT NULL REFERENCES board(id),
    user_id   BIGINT      NOT NULL REFERENCES users(id),
    role      VARCHAR(16) NOT NULL DEFAULT 'MEMBER'
                  CONSTRAINT board_member_role_check
                      CHECK (role IN ('OWNER', 'MEMBER', 'VIEWER')),
    joined_at TIMESTAMP   NOT NULL DEFAULT NOW(),

    CONSTRAINT board_member_unique UNIQUE (board_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_board_member_board_id ON board_member(board_id);
CREATE INDEX IF NOT EXISTS idx_board_member_user_id  ON board_member(user_id);


CREATE TABLE IF NOT EXISTS board_column (
    id         BIGSERIAL    PRIMARY KEY,
    board_id   BIGINT       NOT NULL REFERENCES board(id),
    name       VARCHAR(128) NOT NULL,
    position   INTEGER      NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_board_column_board_id
    ON board_column(board_id);


CREATE TABLE IF NOT EXISTS card (
    id                BIGSERIAL    PRIMARY KEY,
    column_id         BIGINT       NOT NULL REFERENCES board_column(id),
    title             VARCHAR(255) NOT NULL,
    description       TEXT,
    position          INTEGER      NOT NULL,
    priority          VARCHAR(16)
                          CONSTRAINT card_priority_check
                              CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    color             VARCHAR(32),
    deadline          TIMESTAMP,
    created_by        BIGINT       REFERENCES users(id),
    created_at        TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_card_column_id
    ON card(column_id);


CREATE TABLE IF NOT EXISTS card_assignment (
    id          BIGSERIAL PRIMARY KEY,
    card_id     BIGINT    NOT NULL REFERENCES card(id),
    user_id     BIGINT    NOT NULL REFERENCES users(id),
    assigned_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT card_assignment_unique UNIQUE (card_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_card_assignment_card_id
    ON card_assignment(card_id);


CREATE TABLE IF NOT EXISTS card_watcher (
    id            BIGSERIAL PRIMARY KEY,
    card_id       BIGINT    NOT NULL REFERENCES card(id),
    user_id       BIGINT    NOT NULL REFERENCES users(id),
    subscribed_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT card_watcher_unique UNIQUE (card_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_card_watcher_card_id
    ON card_watcher(card_id);


CREATE TABLE IF NOT EXISTS card_comment (
    id         BIGSERIAL PRIMARY KEY,
    card_id    BIGINT    NOT NULL REFERENCES card(id),
    author_id  BIGINT    REFERENCES users(id),
    content    TEXT      NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_card_comment_card_id
    ON card_comment(card_id);


CREATE TABLE IF NOT EXISTS board_notification (
    id          BIGSERIAL   PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users(id),
    event_type  VARCHAR(64) NOT NULL,
    entity_type VARCHAR(32) NOT NULL
                    CONSTRAINT board_notification_entity_type_check
                        CHECK (entity_type IN ('BOARD', 'CARD', 'COMMENT')),
    entity_id   BIGINT      NOT NULL,
    payload     JSONB,
    read        BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_board_notification_user_id
    ON board_notification(user_id);
CREATE INDEX IF NOT EXISTS idx_board_notification_user_read
    ON board_notification(user_id, read);


CREATE TABLE IF NOT EXISTS board_activity (
    id          BIGSERIAL   PRIMARY KEY,
    board_id    BIGINT      NOT NULL REFERENCES board(id),
    actor_id    BIGINT      REFERENCES users(id),
    event_type  VARCHAR(64) NOT NULL,
    entity_type VARCHAR(32) NOT NULL
                    CONSTRAINT board_activity_entity_type_check
                        CHECK (entity_type IN ('BOARD', 'COLUMN', 'CARD', 'COMMENT')),
    entity_id   BIGINT      NOT NULL,
    payload     JSONB,
    created_at  TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_board_activity_board_id
    ON board_activity(board_id);


-- =================================================
-- Knowledge Base — Chunking Profiles
-- =================================================

    CREATE TABLE IF NOT EXISTS chunking_profile (
        id             BIGSERIAL    PRIMARY KEY,
        name           VARCHAR(255) NOT NULL,
        description    VARCHAR(1000),
        strategy       VARCHAR(32)  NOT NULL,
        chunk_size     INTEGER,
        chunk_overlap  INTEGER,
        separator      VARCHAR(255),
        created_at     TIMESTAMP    NOT NULL DEFAULT NOW()
    );

    INSERT INTO chunking_profile (name, description, strategy, chunk_size, chunk_overlap)
    VALUES ('Default', 'General purpose — 200 tokens with 20-token overlap', 'FIXED_TOKENS', 200, 20)
    ON CONFLICT DO NOTHING;


-- =================================================
-- Knowledge Base — Data Sources
-- =================================================

CREATE TABLE IF NOT EXISTS data_source (
    id              BIGSERIAL     PRIMARY KEY,
    name            VARCHAR(255)  NOT NULL,
    connector_url   VARCHAR(1024) NOT NULL,
    connector_name  VARCHAR(255),
    config          JSONB         NOT NULL DEFAULT '{}',
    status          VARCHAR(32)   NOT NULL DEFAULT 'NEVER_SYNCED'
                        CONSTRAINT data_source_status_check
                            CHECK (status IN ('NEVER_SYNCED', 'SYNCING', 'IDLE', 'ERROR')),
    last_synced_at  TIMESTAMP,
    chunk_count     INTEGER       NOT NULL DEFAULT 0,
    error_message   TEXT,
    created_at      TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_data_source_status
    ON data_source(status);

ALTER TABLE data_source
    ADD COLUMN IF NOT EXISTS chunking_profile_id BIGINT REFERENCES chunking_profile(id);

ALTER TABLE loaded_document
    ADD COLUMN IF NOT EXISTS data_source_id BIGINT REFERENCES data_source(id);


-- =================================================
-- Knowledge Base — Hybrid Search (FTS on vector_store)
-- =================================================

ALTER TABLE vector_store
    ADD COLUMN IF NOT EXISTS fts tsvector
        GENERATED ALWAYS AS (to_tsvector('english', coalesce(content, ''))) STORED;

CREATE INDEX IF NOT EXISTS idx_vector_store_fts
    ON vector_store USING GIN(fts);