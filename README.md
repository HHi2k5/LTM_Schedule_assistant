# Schedule Assistant

Ứng dụng nhập lịch giảng dạy từ Excel, tổng hợp lịch cá nhân và xếp lịch toàn trường theo vai trò. Yêu cầu nghiệp vụ: `document/requirement.md`.

## Chạy toàn bộ bằng Docker Desktop

Mở Docker Desktop rồi chạy `docker compose up -d --build` tại thư mục gốc. Truy cập http://localhost:8088. Xem [hướng dẫn Docker](DOCKER.md) để cấu hình, xem log và chia sẻ qua ngrok.

## Chạy frontend React

Phân quyền TEACHER/ADMIN, cấp quyền quản lý và tải Excel kết quả: [hướng dẫn sử dụng](document/phan-quyen-va-xuat-excel.md). Giảng viên tự đăng ký tài khoản; ADMIN không cấp tài khoản giảng viên.

Yêu cầu Node.js 20.19+ hoặc 22.12+.

```powershell
cd frontend
npm.cmd install
npm.cmd run dev
```

Mở địa chỉ Vite hiển thị trên terminal, mặc định http://127.0.0.1:5173.
Trên macOS/Linux dùng `npm` thay cho `npm.cmd`.

```powershell
npm.cmd run build
npm.cmd run preview
```

## Chức năng

- Giảng viên nhập Excel (.xlsx), xem trước rồi lưu lịch của chính mình.
- Quản lý xếp lịch toàn trường và tải Excel kết quả gồm cấu trúc mẫu cùng mã/tên giảng viên.
- Đăng nhập bằng token; quyền TEACHER/ADMIN và quyền sở hữu lịch được kiểm tra tại backend.
- Lịch tuần, chuyển tuần, danh sách, tìm kiếm và lọc theo trường từ dữ liệu backend.
- Tổng hợp nhiều trường, chi tiết buổi dạy, cảnh báo trùng giờ và vị trí theo phút thực tế.
- Xuất lịch tuần dưới dạng .ics; nhập với múi giờ Việt Nam.
- Giao diện responsive, điều hướng di động, modal hỗ trợ bàn phím.

## Phạm vi

Frontend dùng React, Vite, Lucide và ExcelJS. File lịch cá nhân được gửi tới `POST /api/schedule/upload-excel-teacher`; phân công toàn trường gửi tới `POST /api/schedule/auto-generate-excel`. Lịch tuần lấy từ backend; localStorage chỉ nhớ lựa chọn và bảng tiết tham chiếu. Token ở sessionStorage, quyền kiểm tra theo tài khoản trên server. Font Be Vietnam Pro tải từ Google Fonts, có font hệ thống dự phòng.

Chạy backend ở cổng 8080 và MySQL theo cấu hình server, hoặc dùng Docker ở cổng 8088. Nghỉ/dạy bù chưa nằm trong phạm vi hiện tại. Xem [hướng dẫn frontend](frontend/README.md) để biết định dạng file và cách cấu hình API.
