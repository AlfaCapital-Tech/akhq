ALTER TABLE access_request ADD COLUMN cluster VARCHAR(255);
ALTER TABLE access_request ADD COLUMN last_reminded_at TIMESTAMP;
