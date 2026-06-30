-- Boards: columns, tickets, and comments
-- Hibernate creates these via ddl-auto=update; this file is for reference/manual provisioning.

CREATE TABLE IF NOT EXISTS board_column (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(128) NOT NULL,
    position   INTEGER      NOT NULL DEFAULT 0,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ticket (
    id                    BIGSERIAL PRIMARY KEY,
    title                 VARCHAR(255) NOT NULL,
    description           TEXT,
    column_id             BIGINT       NOT NULL REFERENCES board_column(id) ON DELETE CASCADE,
    assignee_username     VARCHAR(64),
    assignee_display_name VARCHAR(128),
    priority              VARCHAR(16),
    created_by            VARCHAR(64),
    created_at            TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ticket_comment (
    id                  BIGSERIAL PRIMARY KEY,
    ticket_id           BIGINT    NOT NULL REFERENCES ticket(id) ON DELETE CASCADE,
    author_username     VARCHAR(64) NOT NULL,
    author_display_name VARCHAR(128),
    body                TEXT        NOT NULL,
    created_at          TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ticket_column   ON ticket(column_id);
CREATE INDEX IF NOT EXISTS idx_comment_ticket  ON ticket_comment(ticket_id);