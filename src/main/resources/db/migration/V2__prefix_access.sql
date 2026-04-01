ALTER TABLE access_request ALTER COLUMN topic_name DROP NOT NULL;
ALTER TABLE access_request ADD COLUMN prefix VARCHAR(255);

ALTER TABLE topic_access ALTER COLUMN topic_name DROP NOT NULL;
ALTER TABLE topic_access ADD COLUMN prefix VARCHAR(255);
ALTER TABLE topic_access DROP CONSTRAINT topic_access_username_topic_name_role_key;
CREATE UNIQUE INDEX topic_access_unique_topic
    ON topic_access (username, topic_name, role) WHERE prefix IS NULL;
CREATE UNIQUE INDEX topic_access_unique_prefix
    ON topic_access (username, prefix, role) WHERE topic_name IS NULL;
