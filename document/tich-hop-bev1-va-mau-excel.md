# Tích hợp BEv1: định dạng kế hoạch mở lớp

Nhánh nguồn: `origin/BEv1`, commit `2fee522`.

## Mẫu Excel hiện hành

11 cột theo thứ tự:

1. STT
2. Mã môn
3. Tên học phần
4. Loại lớp (LT / TH)
5. Sĩ số
6. Khóa
7. Số lớp mở
8. Tuần bắt đầu — từ ngày
9. Tuần kết thúc — đến ngày
10. Số tín chỉ
11. Số tiết

Mỗi dòng là một loại lớp. Ví dụ cùng môn NET, khóa 2024: dòng LT mở 2 lớp, 50 sinh viên/lớp, 30 tiết; dòng TH mở 4 nhóm, 25 sinh viên/nhóm, 15 tiết. Hai dòng có thể có khoảng ngày riêng. Ngày nhận `dd/MM/yyyy`, `yyyy-MM-dd` hoặc ô ngày Excel thực.

`Số tiết` là tổng khối lượng của mỗi lớp/nhóm trong dòng, không phải số tiết của một buổi. Phòng LAB nhỏ hơn sĩ số vẫn làm phát sinh nhóm theo sức chứa thực tế. Mỗi nhóm phải học đủ khối lượng TH; hệ thống không bỏ tiết để ép vào phòng.

Mẫu trước đây dùng hai cột `Tổng tiết LT`, `Tổng tiết TH` vẫn được đọc. Khi nhập trực tiếp một dòng gồm cả LT và TH, frontend tạo hai dòng trong file gửi lên, giữ nguyên khối lượng của cả hai phần; phần bằng 0 được bỏ khỏi file.

## Quy tắc dữ liệu

- Loại lớp phải xác định rõ LT hoặc TH; chấp nhận tên tương đương lý thuyết/thực hành, THEORY/PRACTICE/LAB. Thiếu hoặc nhập `LT/TH` cho một dòng sẽ báo lỗi.
- Số tiết phải là số nguyên 1–1000, tín chỉ 1–30. Không tự gán 30 tiết hay 3 tín chỉ khi bỏ trống.
- Giữ kiểm tra cột trùng, giới hạn kích thước file/số dòng, ngày học và phân công đúng môn–khóa.
- Hai dòng LT và TH của cùng môn/khóa được xử lý riêng. Các dòng cùng loại vẫn phải có khối lượng, tên môn, tín chỉ và trọng số nhất quán.

## Phạm vi ghép nhánh

- Đưa định dạng LT/TH vào mẫu tải xuống, đọc file backend, luồng ba file và luồng dùng danh mục/bản lịch khoa.
- Dùng tiếp danh mục `institution_lecturers`, `lecturer_assignments`, `rooms` để giữ liên kết tài khoản, khoa, phiên bản và kiểm tra trùng. Chức năng lưu sẵn GV/phòng của BEv1 đã được đáp ứng bởi các danh mục này.
- Bảng `teacher_subjects` và CRUD riêng trên BEv1 không được đưa vào để tránh hai nguồn phân công độc lập. Migration V4 của nhánh đó cũng trùng số với migration danh mục hiện có, nên không thêm vào lịch sử migration.
- Giữ bộ kiểm thử hiện tại, tài khoản tự cấp, việc khóa tự đăng ký và bản sửa trang Cài đặt.

Các trường hợp kiểm thử bổ sung bao gồm đọc mẫu mới/mẫu cũ, hai loại cùng môn có sĩ số/số lớp/ngày riêng, từ chối dữ liệu thiếu/sai, xếp đủ giờ, dùng danh mục đã lưu và xác nhận lịch cá nhân.
