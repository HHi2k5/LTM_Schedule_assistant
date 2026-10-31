CREATE TABLE IF NOT EXISTS scheduling_plans (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    school_code VARCHAR(50) NOT NULL,
    department_id BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL,
    semester_key VARCHAR(80) NOT NULL,
    semester_start_date DATE NOT NULL,
    version_sequence INT NOT NULL DEFAULT 0,
    draft_version_id BIGINT NULL,
    confirmed_version_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_plan_scope UNIQUE (school_code, department_id, semester_key, semester_start_date),
    CONSTRAINT fk_plan_school FOREIGN KEY (school_code) REFERENCES institutions(school_code),
    CONSTRAINT fk_plan_department FOREIGN KEY (department_id) REFERENCES departments(id)
);
CREATE TABLE IF NOT EXISTS scheduling_plan_versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    plan_id BIGINT NOT NULL,
    version_number INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    created_by VARCHAR(50) NOT NULL,
    complete BOOLEAN NOT NULL,
    request_json LONGTEXT NOT NULL,
    configuration_json LONGTEXT NOT NULL,
    result_json LONGTEXT NOT NULL,
    publication_json LONGTEXT NULL,
    confirmed_at DATETIME(6) NULL,
    confirmed_by VARCHAR(50) NULL,
    CONSTRAINT uq_plan_version UNIQUE (plan_id, version_number),
    CONSTRAINT fk_version_plan FOREIGN KEY (plan_id) REFERENCES scheduling_plans(id)
);
CREATE TABLE IF NOT EXISTS scheduling_demands (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    version_id BIGINT NOT NULL,
    demand_index INT NOT NULL,
    course_code VARCHAR(50) NOT NULL,
    cohort VARCHAR(50) NOT NULL,
    snapshot_json LONGTEXT NOT NULL,
    CONSTRAINT uq_version_demand UNIQUE (version_id, demand_index),
    CONSTRAINT fk_demand_version FOREIGN KEY (version_id) REFERENCES scheduling_plan_versions(id)
);
CREATE TABLE IF NOT EXISTS scheduling_entries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    version_id BIGINT NOT NULL,
    demand_id BIGINT NOT NULL,
    lecturer_id BIGINT NOT NULL,
    room_id BIGINT NOT NULL,
    lesson_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    start_period INT NOT NULL,
    num_periods INT NOT NULL,
    schedule_class_id VARCHAR(255) NOT NULL,
    schedule_parent_id VARCHAR(255) NOT NULL,
    class_group VARCHAR(100) NOT NULL,
    session_type VARCHAR(20) NOT NULL,
    student_count INT NOT NULL,
    course_name VARCHAR(255) NOT NULL,
    lecturer_name VARCHAR(100) NOT NULL,
    room_code VARCHAR(50) NOT NULL,
    CONSTRAINT uq_version_class_session UNIQUE (version_id, schedule_class_id, lesson_date, start_period),
    CONSTRAINT fk_entry_version FOREIGN KEY (version_id) REFERENCES scheduling_plan_versions(id),
    CONSTRAINT fk_entry_demand FOREIGN KEY (demand_id) REFERENCES scheduling_demands(id),
    CONSTRAINT fk_entry_lecturer FOREIGN KEY (lecturer_id) REFERENCES institution_lecturers(id),
    CONSTRAINT fk_entry_room FOREIGN KEY (room_id) REFERENCES rooms(id),
    INDEX ix_entry_version_date (version_id, lesson_date),
    INDEX ix_entry_lecturer_date (lecturer_id, lesson_date),
    INDEX ix_entry_room_date (room_id, lesson_date)
);
SET @exists_fk = (SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema = DATABASE() AND table_name = 'scheduling_plans' AND constraint_name = 'fk_plan_draft_version');
SET @ddl = IF(@exists_fk = 0, 'ALTER TABLE scheduling_plans ADD CONSTRAINT fk_plan_draft_version FOREIGN KEY (draft_version_id) REFERENCES scheduling_plan_versions(id)', 'SELECT 1');
PREPARE upgrade_statement FROM @ddl;
EXECUTE upgrade_statement;
DEALLOCATE PREPARE upgrade_statement;
SET @exists_fk = (SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema = DATABASE() AND table_name = 'scheduling_plans' AND constraint_name = 'fk_plan_confirmed_version');
SET @ddl = IF(@exists_fk = 0, 'ALTER TABLE scheduling_plans ADD CONSTRAINT fk_plan_confirmed_version FOREIGN KEY (confirmed_version_id) REFERENCES scheduling_plan_versions(id)', 'SELECT 1');
PREPARE upgrade_statement FROM @ddl;
EXECUTE upgrade_statement;
DEALLOCATE PREPARE upgrade_statement;
SET @exists_column = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'teaching_courses' AND column_name = 'scheduling_plan_id');
SET @ddl = IF(@exists_column = 0, 'ALTER TABLE teaching_courses ADD COLUMN scheduling_plan_id BIGINT NULL', 'SELECT 1');
PREPARE upgrade_statement FROM @ddl;
EXECUTE upgrade_statement;
DEALLOCATE PREPARE upgrade_statement;
SET @exists_column = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'teaching_courses' AND column_name = 'scheduling_entry_id');
SET @ddl = IF(@exists_column = 0, 'ALTER TABLE teaching_courses ADD COLUMN scheduling_entry_id BIGINT NULL', 'SELECT 1');
PREPARE upgrade_statement FROM @ddl;
EXECUTE upgrade_statement;
DEALLOCATE PREPARE upgrade_statement;
SET @exists_fk = (SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema = DATABASE() AND table_name = 'teaching_courses' AND constraint_name = 'fk_course_plan');
SET @ddl = IF(@exists_fk = 0, 'ALTER TABLE teaching_courses ADD CONSTRAINT fk_course_plan FOREIGN KEY (scheduling_plan_id) REFERENCES scheduling_plans(id)', 'SELECT 1');
PREPARE upgrade_statement FROM @ddl;
EXECUTE upgrade_statement;
DEALLOCATE PREPARE upgrade_statement;
SET @exists_fk = (SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema = DATABASE() AND table_name = 'teaching_courses' AND constraint_name = 'fk_course_entry');
SET @ddl = IF(@exists_fk = 0, 'ALTER TABLE teaching_courses ADD CONSTRAINT fk_course_entry FOREIGN KEY (scheduling_entry_id) REFERENCES scheduling_entries(id)', 'SELECT 1');
PREPARE upgrade_statement FROM @ddl;
EXECUTE upgrade_statement;
DEALLOCATE PREPARE upgrade_statement;
SET @exists_index = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'teaching_courses' AND index_name = 'uq_course_entry');
SET @ddl = IF(@exists_index = 0, 'ALTER TABLE teaching_courses ADD CONSTRAINT uq_course_entry UNIQUE (scheduling_entry_id)', 'SELECT 1');
PREPARE upgrade_statement FROM @ddl;
EXECUTE upgrade_statement;
DEALLOCATE PREPARE upgrade_statement;
