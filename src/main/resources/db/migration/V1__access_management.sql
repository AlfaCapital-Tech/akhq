CREATE TABLE access_request (
    id              UUID PRIMARY KEY,
    username        VARCHAR(255) NOT NULL,
    topic_name      VARCHAR(255) NOT NULL,
    role            VARCHAR(50)  NOT NULL,
    status          VARCHAR(50)  NOT NULL,
    reason          TEXT,
    reject_reason   TEXT,
    resolved_by     VARCHAR(255),
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    resolved_at     TIMESTAMP
);

CREATE TABLE topic_access (
    id              UUID PRIMARY KEY,
    username        VARCHAR(255) NOT NULL,
    topic_name      VARCHAR(255) NOT NULL,
    role            VARCHAR(50)  NOT NULL,
    granted_by      VARCHAR(255) NOT NULL,
    granted_at      TIMESTAMP NOT NULL DEFAULT now(),
    request_id      UUID REFERENCES access_request(id),
    UNIQUE (username, topic_name, role)
);

CREATE INDEX idx_access_request_status ON access_request(status);
CREATE INDEX idx_access_request_username ON access_request(username);
CREATE INDEX idx_topic_access_username ON topic_access(username);
