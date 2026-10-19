# Schedule Assistant

Ứng dụng nhập và tổng hợp lịch giảng dạy có sẵn từ Excel hoặc JSON. Yêu cầu nghiệp vụ: `document/requirement.md`.

## Chạy frontend React

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

- Nhập lịch bằng file Excel (.xlsx) hoặc JSON (.json), có mẫu tải về, kiểm tra dữ liệu và xem trước khi lưu.
- JSON tự điền giảng viên, trường, mốc tuần và màu hiển thị theo cấu trúc backend.
- Lịch tuần, chuyển tuần, danh sách, tìm kiếm và lọc theo trường từ dữ liệu backend.
- Tổng hợp nhiều trường, chi tiết buổi dạy, cảnh báo trùng giờ và vị trí theo phút thực tế.
- Xuất lịch tuần dưới dạng .ics; nhập với múi giờ Việt Nam.
- Giao diện responsive, điều hướng di động, modal hỗ trợ bàn phím.

## Phạm vi

Frontend dùng React, Vite và Lucide. Excel được đọc bằng ExcelJS; Excel và JSON dùng chung kiểm tra giờ, tiết và tuần trước khi gửi JSON tới `POST /api/schedule/upload-school`. Lịch tuần được tải từ backend; localStorage chỉ nhớ mã giảng viên và bảng tiết tham chiếu trên thiết bị. Font Be Vietnam Pro tải từ Google Fonts, có font hệ thống dự phòng.

Chạy backend ở cổng 8080 và MySQL theo cấu hình server để lưu/xem lịch. Frontend đã tích hợp API lịch; đăng nhập, phân quyền, nghỉ/dạy bù và validation đầy đủ phía backend cần bổ sung. Xem [hướng dẫn frontend](frontend/README.md) để biết định dạng file và cách cấu hình API.
