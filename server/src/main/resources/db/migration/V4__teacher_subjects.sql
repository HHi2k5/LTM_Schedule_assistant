CREATE TABLE IF NOT EXISTS teacher_subjects (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    school_code VARCHAR(50) NOT NULL DEFAULT 'PTIT',
    teacher_id VARCHAR(50) NOT NULL,
    teacher_name VARCHAR(100) NOT NULL,
    course_code VARCHAR(50) NOT NULL,
    course_name VARCHAR(180),
    cohort VARCHAR(50) NOT NULL DEFAULT '*',
    theory_weight INT NOT NULL DEFAULT 1,
    practice_weight INT NOT NULL DEFAULT 1,
    CONSTRAINT uq_teacher_subject_school UNIQUE (school_code, teacher_id, course_code, cohort)
);