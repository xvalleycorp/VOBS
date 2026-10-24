-- Schema của service consent trên PostgreSQL (14 trở lên). Chạy lại nhiều lần không sao.

CREATE TABLE IF NOT EXISTS consent (
    consent_id        text        PRIMARY KEY,
    client_id         text        NOT NULL,
    -- Chưa có khi khách hàng chưa duyệt hay từ chối
    customer_id       text,
    scopes            text[]      NOT NULL,
    account_ids       text[]      NOT NULL,
    created_at        timestamptz NOT NULL,
    expires_at        timestamptz NOT NULL,
    status            text        NOT NULL,
    status_changed_at timestamptz NOT NULL
);

CREATE INDEX IF NOT EXISTS consent_customer_idx ON consent (customer_id, created_at DESC);

-- Log kiểm toán nối chuỗi hash: mỗi bản ghi chứa hash của bản ghi trước
CREATE TABLE IF NOT EXISTS consent_audit_log (
    sequence      bigint      PRIMARY KEY,
    at            timestamptz NOT NULL,
    actor         text        NOT NULL,
    action        text        NOT NULL,
    subject       text        NOT NULL,
    previous_hash char(64)    NOT NULL,
    hash          char(64)    NOT NULL UNIQUE
);

-- Chỉ ghi thêm: chặn sửa và xóa từng bản ghi
CREATE OR REPLACE FUNCTION consent_audit_log_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'consent_audit_log chỉ được ghi thêm';
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE TRIGGER consent_audit_log_append_only
    BEFORE UPDATE OR DELETE ON consent_audit_log
    FOR EACH ROW EXECUTE FUNCTION consent_audit_log_append_only();
