-- A roster entry can schedule without creating a login; retain an explicit legacy identity.
SET @exists_column = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'institution_lecturers' AND column_name = 'schedule_teacher_id');
SET @ddl = IF(@exists_column = 0, 'ALTER TABLE institution_lecturers ADD COLUMN schedule_teacher_id VARCHAR(50) NULL', 'SELECT 1');
PREPARE upgrade_statement FROM @ddl;
EXECUTE upgrade_statement;
DEALLOCATE PREPARE upgrade_statement;
SET @exists_index = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'institution_lecturers' AND index_name = 'uq_lecturer_school_schedule');
SET @ddl = IF(@exists_index = 0, 'ALTER TABLE institution_lecturers ADD CONSTRAINT uq_lecturer_school_schedule UNIQUE (school_code, schedule_teacher_id)', 'SELECT 1');
PREPARE upgrade_statement FROM @ddl;
EXECUTE upgrade_statement;
DEALLOCATE PREPARE upgrade_statement;
SET @exists_fk = (SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema = DATABASE() AND table_name = 'institution_lecturers' AND constraint_name = 'fk_lecturer_schedule_teacher');
SET @ddl = IF(@exists_fk = 0, 'ALTER TABLE institution_lecturers ADD CONSTRAINT fk_lecturer_schedule_teacher FOREIGN KEY (schedule_teacher_id) REFERENCES teachers(teacher_id)', 'SELECT 1');
PREPARE upgrade_statement FROM @ddl;
EXECUTE upgrade_statement;
DEALLOCATE PREPARE upgrade_statement;
