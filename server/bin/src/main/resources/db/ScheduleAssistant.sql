CREATE DATABASE IF NOT EXISTS teacher_schedule_db 
CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE teacher_schedule_db;

-- 1. Bảng giảng viên
CREATE TABLE IF NOT EXISTS teachers (
    teacher_id VARCHAR(50) PRIMARY KEY,
    full_name VARCHAR(100),
    semester_start_date DATE NOT NULL
);

-- 2. Bảng trường học
CREATE TABLE IF NOT EXISTS schools (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    school_code VARCHAR(50) NOT NULL,
    school_name VARCHAR(255) NOT NULL,
    color_code VARCHAR(20) DEFAULT '#2563eb',
    teacher_id VARCHAR(50) NOT NULL,
    UNIQUE KEY uq_teacher_school (teacher_id, school_code),
    FOREIGN KEY (teacher_id) REFERENCES teachers(teacher_id) ON DELETE CASCADE
);

-- 3. Bảng cấu hình khung giờ từng tiết của trường
CREATE TABLE IF NOT EXISTS school_periods (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    school_id BIGINT NOT NULL,
    period_number INT NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    FOREIGN KEY (school_id) REFERENCES schools(id) ON DELETE CASCADE
);

-- 4. Bảng các môn/lớp dạy của giảng viên
CREATE TABLE IF NOT EXISTS teaching_courses (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    school_id BIGINT NOT NULL,
    course_code VARCHAR(50),
    course_name VARCHAR(255) NOT NULL,
    classroom VARCHAR(100),
    day_of_week INT NOT NULL,          -- 2: Thứ 2, ..., 8: Chủ nhật
    start_period INT NOT NULL,
    num_periods INT NOT NULL,
    week_schedule VARCHAR(100) NOT NULL, -- vd: "--------9012345678--"
    FOREIGN KEY (school_id) REFERENCES schools(id) ON DELETE CASCADE
);