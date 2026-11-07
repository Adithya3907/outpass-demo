-- Longer reason text (custom reasons), plus a validity window per request.
ALTER TABLE outpass_request ALTER COLUMN reason TYPE VARCHAR(255);

ALTER TABLE outpass_request ADD COLUMN valid_from TIMESTAMPTZ;
ALTER TABLE outpass_request ADD COLUMN expires_at TIMESTAMPTZ;

UPDATE outpass_request
   SET valid_from = (permission_date + exit_time) AT TIME ZONE 'Asia/Kolkata';
UPDATE outpass_request
   SET expires_at = valid_from + INTERVAL '75 minutes';

ALTER TABLE outpass_request ALTER COLUMN valid_from SET NOT NULL;
ALTER TABLE outpass_request ALTER COLUMN expires_at SET NOT NULL;

CREATE INDEX idx_req_expiry ON outpass_request(status, expires_at);
CREATE INDEX idx_req_student_window ON outpass_request(student_id, valid_from, expires_at);
