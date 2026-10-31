-- Add shared directories without rewriting legacy accounts, schools or schedules.
CREATE TABLE IF NOT EXISTS institutions (
    school_code VARCHAR(50) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    revision BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS departments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    school_code VARCHAR(50) NOT NULL,
    department_code VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    revision BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_department_school_code UNIQUE (school_code, department_code),
    CONSTRAINT fk_department_institution FOREIGN KEY (school_code) REFERENCES institutions(school_code)
);

CREATE TABLE IF NOT EXISTS institution_lecturers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    school_code VARCHAR(50) NOT NULL,
    lecturer_code VARCHAR(50) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    department_id BIGINT NULL,
    lecturer_type VARCHAR(20) NOT NULL,
    account_teacher_id VARCHAR(50) NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    revision BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_lecturer_school_code UNIQUE (school_code, lecturer_code),
    CONSTRAINT uq_lecturer_school_account UNIQUE (school_code, account_teacher_id),
    CONSTRAINT fk_lecturer_institution FOREIGN KEY (school_code) REFERENCES institutions(school_code),
    CONSTRAINT fk_lecturer_department FOREIGN KEY (department_id) REFERENCES departments(id),
    CONSTRAINT fk_lecturer_account FOREIGN KEY (account_teacher_id) REFERENCES teachers(teacher_id)
);

CREATE TABLE IF NOT EXISTS lecturer_assignments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    lecturer_id BIGINT NOT NULL,
    course_code VARCHAR(50) NOT NULL,
    course_name VARCHAR(180) NOT NULL,
    cohort VARCHAR(50) NOT NULL,
    theory_weight INT NOT NULL,
    practice_weight INT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    revision BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_lecturer_course_cohort UNIQUE (lecturer_id, course_code, cohort),
    CONSTRAINT fk_assignment_lecturer FOREIGN KEY (lecturer_id) REFERENCES institution_lecturers(id),
    CONSTRAINT ck_assignment_weights CHECK (theory_weight BETWEEN 0 AND 10000 AND practice_weight BETWEEN 0 AND 10000)
);

-- Conditional additions also support an existing installation replaying its baseline.
SET @exists_column = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'rooms' AND column_name = 'active');
SET @ddl = IF(@exists_column = 0, 'ALTER TABLE rooms ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE', 'SELECT 1');
PREPARE upgrade_statement FROM @ddl;
EXECUTE upgrade_statement;
DEALLOCATE PREPARE upgrade_statement;
SET @exists_column = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'rooms' AND column_name = 'revision');
SET @ddl = IF(@exists_column = 0, 'ALTER TABLE rooms ADD COLUMN revision BIGINT NOT NULL DEFAULT 0', 'SELECT 1');
PREPARE upgrade_statement FROM @ddl;
EXECUTE upgrade_statement;
DEALLOCATE PREPARE upgrade_statement;
