CREATE TABLE department (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL
);

CREATE TABLE user_account (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(190) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    full_name VARCHAR(150) NOT NULL,
    role VARCHAR(20) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE class_section (
    id BIGSERIAL PRIMARY KEY,
    department_id BIGINT NOT NULL REFERENCES department(id),
    academic_year INT NOT NULL,
    section VARCHAR(10) NOT NULL,
    semester INT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_class UNIQUE (department_id, academic_year, section)
);

CREATE TABLE faculty_profile (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES user_account(id),
    department_id BIGINT NOT NULL REFERENCES department(id),
    phone VARCHAR(20)
);

CREATE TABLE student_profile (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES user_account(id),
    roll_number VARCHAR(30) NOT NULL UNIQUE,
    class_section_id BIGINT NOT NULL REFERENCES class_section(id),
    phone VARCHAR(20)
);

CREATE TABLE tutor_assignment (
    id BIGSERIAL PRIMARY KEY,
    class_section_id BIGINT NOT NULL REFERENCES class_section(id),
    faculty_id BIGINT NOT NULL REFERENCES faculty_profile(id),
    effective_from DATE NOT NULL,
    effective_to DATE
);
CREATE INDEX idx_tutor_assign_class ON tutor_assignment(class_section_id);
CREATE INDEX idx_tutor_assign_faculty ON tutor_assignment(faculty_id);

CREATE TABLE backup_tutor_assignment (
    id BIGSERIAL PRIMARY KEY,
    department_id BIGINT NOT NULL REFERENCES department(id),
    faculty_id BIGINT NOT NULL REFERENCES faculty_profile(id),
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE hod_assignment (
    id BIGSERIAL PRIMARY KEY,
    department_id BIGINT NOT NULL REFERENCES department(id),
    faculty_id BIGINT NOT NULL REFERENCES faculty_profile(id),
    active BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE UNIQUE INDEX uq_one_active_hod ON hod_assignment(department_id) WHERE active;

CREATE TABLE gate (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE outpass_request (
    id BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL REFERENCES student_profile(id),
    class_section_id BIGINT NOT NULL REFERENCES class_section(id),
    reason VARCHAR(100) NOT NULL,
    permission_date DATE NOT NULL,
    exit_time TIME NOT NULL,
    expected_return_time TIME,
    description VARCHAR(500),
    escalation VARCHAR(20) NOT NULL DEFAULT 'TUTOR',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    assigned_tutor_names VARCHAR(300),
    decision_owner_id BIGINT REFERENCES user_account(id),
    decision_remark VARCHAR(500),
    decided_at TIMESTAMPTZ,
    revision_count INT NOT NULL DEFAULT 0,
    hod_approver_id BIGINT REFERENCES user_account(id),
    hod_remark VARCHAR(500),
    hod_decided_at TIMESTAMPTZ,
    qr_token VARCHAR(64) UNIQUE,
    approved_at TIMESTAMPTZ,
    used_at TIMESTAMPTZ,
    used_gate_id BIGINT REFERENCES gate(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_req_student ON outpass_request(student_id, created_at DESC);
CREATE INDEX idx_req_class_status ON outpass_request(class_section_id, status);
CREATE INDEX idx_req_date ON outpass_request(permission_date);

CREATE TABLE approval_revision (
    id BIGSERIAL PRIMARY KEY,
    request_id BIGINT NOT NULL REFERENCES outpass_request(id),
    actor_id BIGINT NOT NULL REFERENCES user_account(id),
    action VARCHAR(30) NOT NULL,
    previous_status VARCHAR(20),
    new_status VARCHAR(20) NOT NULL,
    remark VARCHAR(500),
    revision_no INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_rev_request ON approval_revision(request_id);

CREATE TABLE gate_scan (
    id BIGSERIAL PRIMARY KEY,
    request_id BIGINT REFERENCES outpass_request(id),
    token_hint VARCHAR(12),
    gate_id BIGINT REFERENCES gate(id),
    scanned_by_id BIGINT REFERENCES user_account(id),
    result VARCHAR(10) NOT NULL,
    reason VARCHAR(200),
    scanned_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_scan_time ON gate_scan(scanned_at DESC);

CREATE TABLE notification (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES user_account(id),
    title VARCHAR(200) NOT NULL,
    message VARCHAR(500),
    link VARCHAR(200),
    seen BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_notif_user ON notification(user_id, seen, created_at DESC);

CREATE TABLE audit_log (
    id BIGSERIAL PRIMARY KEY,
    actor VARCHAR(190),
    action VARCHAR(60) NOT NULL,
    entity VARCHAR(60),
    entity_id BIGINT,
    details VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Historical records are append-only (immutable approval + audit history)
CREATE FUNCTION forbid_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Table % is append-only', TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_immutable BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION forbid_change();
CREATE TRIGGER trg_revision_immutable BEFORE UPDATE OR DELETE ON approval_revision
    FOR EACH ROW EXECUTE FUNCTION forbid_change();
CREATE TRIGGER trg_scan_immutable BEFORE UPDATE OR DELETE ON gate_scan
    FOR EACH ROW EXECUTE FUNCTION forbid_change();
