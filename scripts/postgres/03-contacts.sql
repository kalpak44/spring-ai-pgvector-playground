-- Contacts table
CREATE TABLE IF NOT EXISTS contact (
    id         BIGSERIAL    PRIMARY KEY,
    first_name VARCHAR(100),
    last_name  VARCHAR(100),
    phone      VARCHAR(50),
    email      VARCHAR(255),
    created_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_by VARCHAR(64)
);

CREATE INDEX IF NOT EXISTS idx_contact_name  ON contact (LOWER(first_name), LOWER(last_name));
CREATE INDEX IF NOT EXISTS idx_contact_email ON contact (LOWER(email));
CREATE INDEX IF NOT EXISTS idx_contact_phone ON contact (phone);

-- Per-user notes on contacts
CREATE TABLE IF NOT EXISTS contact_note (
    id              BIGSERIAL PRIMARY KEY,
    contact_id      BIGINT    NOT NULL REFERENCES contact(id) ON DELETE CASCADE,
    author_username VARCHAR(64) NOT NULL,
    author_display_name VARCHAR(128),
    body            TEXT      NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_contact_note_contact ON contact_note (contact_id);