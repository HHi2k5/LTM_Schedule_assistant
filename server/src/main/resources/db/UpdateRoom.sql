-- Tạo bảng lưu trữ đối tượng Phòng học (Rooms)
CREATE TABLE IF NOT EXISTS rooms (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_code VARCHAR(50) NOT NULL UNIQUE,      -- Mã phòng (VD: 101-A2, 601-LAB-A2)
    room_name VARCHAR(100),                     -- Tên hiển thị
    room_type VARCHAR(30) NOT NULL,             -- THEORY_LARGE, THEORY_MEDIUM, THEORY_STANDARD, LAB_PRACTICE
    capacity INT NOT NULL DEFAULT 60,           -- Sức chứa tối đa (VD: 30, 55, 90, 150)
    is_lab BOOLEAN NOT NULL DEFAULT FALSE,      -- TRUE = Phòng thực hành (LAB), FALSE = Phòng lý thuyết
    building VARCHAR(50) DEFAULT 'A2',          -- Tòa nhà
    school_code VARCHAR(50) DEFAULT 'PTIT'      -- Mã trường
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Dữ liệu phòng mẫu khởi tạo sẵn (nếu chưa upload file phòng thì hệ thống dùng ngay danh sách này)
INSERT IGNORE INTO rooms (room_code, room_name, room_type, capacity, is_lab, building, school_code) VALUES
('101-A2', 'Giảng đường lớn 101-A2', 'THEORY_LARGE', 150, FALSE, 'A2', 'PTIT'),
('102-A2', 'Giảng đường lớn 102-A2', 'THEORY_LARGE', 150, FALSE, 'A2', 'PTIT'),
('103-A2', 'Giảng đường lớn 103-A2', 'THEORY_LARGE', 150, FALSE, 'A2', 'PTIT'),
('201-A2', 'Phòng lý thuyết 201-A2', 'THEORY_MEDIUM', 100, FALSE, 'A2', 'PTIT'),
('202-A2', 'Phòng lý thuyết 202-A2', 'THEORY_MEDIUM', 100, FALSE, 'A2', 'PTIT'),
('203-A2', 'Phòng lý thuyết 203-A2', 'THEORY_MEDIUM', 100, FALSE, 'A2', 'PTIT'),
('301-A2', 'Phòng lý thuyết 301-A2', 'THEORY_STANDARD', 60, FALSE, 'A2', 'PTIT'),
('302-A2', 'Phòng lý thuyết 302-A2', 'THEORY_STANDARD', 60, FALSE, 'A2', 'PTIT'),
('303-A2', 'Phòng lý thuyết 303-A2', 'THEORY_STANDARD', 60, FALSE, 'A2', 'PTIT'),
('304-A2', 'Phòng lý thuyết 304-A2', 'THEORY_STANDARD', 60, FALSE, 'A2', 'PTIT'),
('305-A2', 'Phòng lý thuyết 305-A2', 'THEORY_STANDARD', 60, FALSE, 'A2', 'PTIT'),
('601-LAB-A2', 'Phòng máy 601-A2', 'LAB_PRACTICE', 30, TRUE, 'A2', 'PTIT'),
('602-LAB-A2', 'Phòng máy 602-A2', 'LAB_PRACTICE', 30, TRUE, 'A2', 'PTIT'),
('603-LAB-A2', 'Phòng máy 603-A2', 'LAB_PRACTICE', 30, TRUE, 'A2', 'PTIT'),
('604-LAB-A2', 'Phòng máy 604-A2', 'LAB_PRACTICE', 30, TRUE, 'A2', 'PTIT'),
('605-LAB-A2', 'Phòng máy 605-A2', 'LAB_PRACTICE', 30, TRUE, 'A2', 'PTIT'),
('701-LAB-A2', 'Phòng máy 701-A2', 'LAB_PRACTICE', 30, TRUE, 'A2', 'PTIT'),
('702-LAB-A2', 'Phòng máy 702-A2', 'LAB_PRACTICE', 30, TRUE, 'A2', 'PTIT');