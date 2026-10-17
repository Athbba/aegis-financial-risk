-- Short-lived attempt counters, separate from immutable financial/audit records.
CREATE TABLE auth_attempt_window (
    bucket_key varchar(64) PRIMARY KEY,
    attempts integer NOT NULL CHECK (attempts > 0),
    expires_at timestamptz NOT NULL
);
CREATE INDEX auth_attempt_expiry ON auth_attempt_window(expires_at);
