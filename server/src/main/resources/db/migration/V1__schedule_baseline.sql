-- 1. Bảng giảng viên
CREATE TABLE IF NOT EXISTS teachers (
    teacher_id VARCHAR(50) PRIMARY KEY,
    full_name VARCHAR(100),
    username VARCHAR(50) UNIQUE,
    password VARCHAR(255),
    role VARCHAR(20) DEFAULT 'TEACHER',
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

-- 4. Bảng các môn/lớp dạy của giảng viên (Bổ sung class_group và source_type)
CREATE TABLE IF NOT EXISTS teaching_courses (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    school_id BIGINT NOT NULL,
    course_code VARCHAR(50),
    course_name VARCHAR(255) NOT NULL,
    class_group VARCHAR(20) DEFAULT '01',
    classroom VARCHAR(100),
    day_of_week INT NOT NULL,          -- 2: Thứ 2, ..., 8: Chủ nhật
    start_period INT NOT NULL,
    num_periods INT NOT NULL,
    week_schedule VARCHAR(100) NOT NULL, -- vd: "--------9012345678--"
    source_type VARCHAR(20) DEFAULT 'EXCEL_UPLOAD', -- 'EXCEL_UPLOAD' hoặc 'AUTO_GENERATED'
    FOREIGN KEY (school_id) REFERENCES schools(id) ON DELETE CASCADE
);

-- 5. Bảng 35 phòng học cố định của trường (7 tầng x 5 phòng)
CREATE TABLE IF NOT EXISTS fixed_classrooms (
    room_name VARCHAR(50) PRIMARY KEY,
    floor_number INT NOT NULL,
    is_active BOOLEAN DEFAULT TRUE
);
CREATE TABLE IF NOT EXISTS rooms (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 room_code VARCHAR(50) NOT NULL,
 room_name VARCHAR(100),
 room_type VARCHAR(30) NOT NULL,
 capacity INT NOT NULL,
 is_lab BOOLEAN NOT NULL,
 building VARCHAR(50),
 school_code VARCHAR(50) NOT NULL,
 CONSTRAINT uq_room_school_code UNIQUE (school_code, room_code)
);
CREATE TABLE IF NOT EXISTS schedule_write_mutex (id INTEGER PRIMARY KEY);
INSERT IGNORE INTO schedule_write_mutex (id) VALUES (1);
