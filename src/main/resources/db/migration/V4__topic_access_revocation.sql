ALTER TABLE topic_access ADD COLUMN revoked_by VARCHAR(255);
ALTER TABLE topic_access ADD COLUMN revoked_at TIMESTAMP;

DROP INDEX topic_access_unique_topic;
DROP INDEX topic_access_unique_prefix;

CREATE UNIQUE INDEX topic_access_unique_topic
    ON topic_access (username, topic_name, role) WHERE prefix IS NULL AND revoked_at IS NULL;
CREATE UNIQUE INDEX topic_access_unique_prefix
    ON topic_access (username, prefix, role) WHERE topic_name IS NULL AND revoked_at IS NULL;
