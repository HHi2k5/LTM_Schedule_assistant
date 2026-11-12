-- Bộ môn, danh mục môn học, loại phòng; thêm loại giảng viên, bộ môn của giảng viên và môn dạy thay.
CREATE TABLE IF NOT EXISTS departments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    school_code VARCHAR(50) NOT NULL DEFAULT 'PTIT',
    code VARCHAR(20) NOT NULL,
    name VARCHAR(100) NOT NULL,
    CONSTRAINT uq_department_school_code UNIQUE (school_code, code)
);

CREATE TABLE IF NOT EXISTS room_types (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    school_code VARCHAR(50) NOT NULL DEFAULT 'PTIT',
    code VARCHAR(30) NOT NULL,
    name VARCHAR(100) NOT NULL,
    usage_kind VARCHAR(20) NOT NULL,
    CONSTRAINT uq_room_type_school_code UNIQUE (school_code, code)
);

CREATE TABLE IF NOT EXISTS subjects (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    school_code VARCHAR(50) NOT NULL DEFAULT 'PTIT',
    department_id BIGINT NOT NULL,
    course_code VARCHAR(50) NOT NULL,
    course_name VARCHAR(180) NOT NULL,
    theory_room_type VARCHAR(30) NULL,
    practice_room_type VARCHAR(30) NULL,
    CONSTRAINT uq_subject_school_code UNIQUE (school_code, course_code),
    CONSTRAINT fk_subject_department FOREIGN KEY (department_id) REFERENCES departments(id)
);

SET @exists_column = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'rooms' AND column_name = 'note');
SET @ddl = IF(@exists_column = 0, 'ALTER TABLE rooms ADD COLUMN note VARCHAR(500) NULL', 'SELECT 1');
PREPARE upgrade_statement FROM @ddl;
EXECUTE upgrade_statement;
DEALLOCATE PREPARE upgrade_statement;

SET @exists_column = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'teachers' AND column_name = 'department_id');
SET @ddl = IF(@exists_column = 0, 'ALTER TABLE teachers ADD COLUMN department_id BIGINT NULL', 'SELECT 1');
PREPARE upgrade_statement FROM @ddl;
EXECUTE upgrade_statement;
DEALLOCATE PREPARE upgrade_statement;

SET @exists_column = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'teachers' AND column_name = 'teacher_type');
SET @ddl = IF(@exists_column = 0, 'ALTER TABLE teachers ADD COLUMN teacher_type VARCHAR(20) DEFAULT ''GIANG_VIEN''', 'SELECT 1');
PREPARE upgrade_statement FROM @ddl;
EXECUTE upgrade_statement;
DEALLOCATE PREPARE upgrade_statement;

SET @exists_column = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'teacher_subjects' AND column_name = 'assignment_kind');
SET @ddl = IF(@exists_column = 0, 'ALTER TABLE teacher_subjects ADD COLUMN assignment_kind VARCHAR(20) DEFAULT ''PRIMARY''', 'SELECT 1');
PREPARE upgrade_statement FROM @ddl;
EXECUTE upgrade_statement;
DEALLOCATE PREPARE upgrade_statement;

UPDATE teachers SET teacher_type = 'GIANG_VIEN' WHERE teacher_type IS NULL OR teacher_type = '';
UPDATE teacher_subjects SET assignment_kind = 'PRIMARY' WHERE assignment_kind IS NULL OR assignment_kind = '';

-- Loại phòng mặc định cho mọi đơn vị đang có phòng (và PTIT).
INSERT IGNORE INTO room_types (school_code, code, name, usage_kind)
SELECT s.school_code, t.code, t.name, t.usage_kind
FROM (SELECT DISTINCT school_code FROM rooms UNION SELECT 'PTIT') s
CROSS JOIN (
    SELECT 'LY_THUYET' AS code, 'Phòng lý thuyết' AS name, 'THEORY' AS usage_kind
    UNION ALL SELECT 'MAY_CHIEU', 'Phòng máy chiếu', 'THEORY'
    UNION ALL SELECT 'PHONG_MAY', 'Phòng máy', 'PRACTICE'
    UNION ALL SELECT 'THUC_HANH', 'Phòng thực hành', 'PRACTICE'
) t;

-- Chuyển loại phòng cũ (THEORY_LARGE/MEDIUM/STANDARD, LAB_PRACTICE) sang mã loại phòng mới.
UPDATE rooms SET room_type = 'THUC_HANH'
WHERE is_lab = 1 AND room_type NOT IN ('LY_THUYET', 'MAY_CHIEU', 'PHONG_MAY', 'THUC_HANH');
UPDATE rooms SET room_type = 'LY_THUYET'
WHERE is_lab = 0 AND room_type NOT IN ('LY_THUYET', 'MAY_CHIEU', 'PHONG_MAY', 'THUC_HANH');
