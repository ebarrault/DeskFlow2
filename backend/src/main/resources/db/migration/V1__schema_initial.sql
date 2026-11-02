-- Schéma initial du helpdesk (PostgreSQL)

CREATE TABLE users (
    id                 BIGSERIAL PRIMARY KEY,
    email              VARCHAR(255) NOT NULL UNIQUE,
    name               VARCHAR(80)  NOT NULL,
    password_hash      VARCHAR(100) NOT NULL,
    role               VARCHAR(20)  NOT NULL DEFAULT 'USER' CHECK (role IN ('USER', 'TECHNICIAN', 'ADMIN')),
    active             BOOLEAN      NOT NULL DEFAULT TRUE,
    two_factor_secret  VARCHAR(64),
    two_factor_enabled BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE tickets (
    id                BIGSERIAL PRIMARY KEY,
    title             VARCHAR(150) NOT NULL,
    description       TEXT         NOT NULL,
    category          VARCHAR(50),
    status            VARCHAR(20)  NOT NULL DEFAULT 'OPEN'
                      CHECK (status IN ('OPEN', 'IN_PROGRESS', 'WAITING', 'RESOLVED', 'CLOSED')),
    priority          VARCHAR(20)  NOT NULL DEFAULT 'MEDIUM'
                      CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    requester_id      BIGINT       NOT NULL REFERENCES users (id),
    assignee_id       BIGINT       REFERENCES users (id),
    response_due_at   TIMESTAMPTZ  NOT NULL,
    resolution_due_at TIMESTAMPTZ  NOT NULL,
    first_response_at TIMESTAMPTZ,
    resolved_at       TIMESTAMPTZ,
    closed_at         TIMESTAMPTZ,
    sla_breached      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_tickets_status ON tickets (status);
CREATE INDEX idx_tickets_assignee ON tickets (assignee_id);
CREATE INDEX idx_tickets_requester ON tickets (requester_id);

CREATE TABLE comments (
    id         BIGSERIAL PRIMARY KEY,
    ticket_id  BIGINT      NOT NULL REFERENCES tickets (id) ON DELETE CASCADE,
    author_id  BIGINT      NOT NULL REFERENCES users (id),
    body       TEXT        NOT NULL,
    internal   BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_comments_ticket ON comments (ticket_id);

CREATE TABLE attachments (
    id            BIGSERIAL PRIMARY KEY,
    ticket_id     BIGINT       NOT NULL REFERENCES tickets (id) ON DELETE CASCADE,
    uploader_id   BIGINT       NOT NULL REFERENCES users (id),
    original_name VARCHAR(255) NOT NULL,
    stored_name   VARCHAR(100) NOT NULL UNIQUE,
    mime_type     VARCHAR(100) NOT NULL,
    size          BIGINT       NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE ticket_events (
    id         BIGSERIAL PRIMARY KEY,
    ticket_id  BIGINT      NOT NULL REFERENCES tickets (id) ON DELETE CASCADE,
    actor_id   BIGINT      REFERENCES users (id),
    type       VARCHAR(30) NOT NULL,
    from_value VARCHAR(255),
    to_value   VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_events_ticket ON ticket_events (ticket_id);
