SET @exists_column = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'scheduling_plan_versions' AND column_name = 'visiting_change_json');
SET @ddl = IF(@exists_column = 0, 'ALTER TABLE scheduling_plan_versions ADD COLUMN visiting_change_json LONGTEXT NULL', 'SELECT 1');
PREPARE upgrade_statement FROM @ddl;
EXECUTE upgrade_statement;
DEALLOCATE PREPARE upgrade_statement;
CREATE TABLE IF NOT EXISTS lecturer_busy_slots (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    version_id BIGINT NOT NULL,
    lecturer_id BIGINT NOT NULL,
    schedule_teacher_id VARCHAR(50) NULL,
    busy_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    reason VARCHAR(255) NOT NULL,
    CONSTRAINT uq_version_busy UNIQUE (version_id, lecturer_id, busy_date, start_time, end_time),
    CONSTRAINT fk_busy_version FOREIGN KEY (version_id) REFERENCES scheduling_plan_versions(id),
    CONSTRAINT fk_busy_lecturer FOREIGN KEY (lecturer_id) REFERENCES institution_lecturers(id),
    CONSTRAINT fk_busy_teacher FOREIGN KEY (schedule_teacher_id) REFERENCES teachers(teacher_id),
    CONSTRAINT ck_busy_times CHECK (start_time < end_time),
    INDEX ix_busy_teacher_date (schedule_teacher_id, busy_date),
    INDEX ix_busy_version_date (version_id, busy_date)
);
