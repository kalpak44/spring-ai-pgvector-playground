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