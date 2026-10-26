# Frontend lịch giảng dạy

Chạy tại thư mục frontend:

```powershell
npm.cmd install
npm.cmd run dev
```

Bật backend và MySQL trước. Trong `.env.local` để `VITE_API_BASE_URL=`; Vite chuyển tiếp `/api` tới `http://127.0.0.1:8080`. Khi dùng ngrok chạy `ngrok http 5173` và thêm đúng hostname vào `server.allowedHosts`. File `.env.local` không được commit.

## Tài khoản và quyền

Đăng ký công khai tạo giảng viên (TEACHER). Giảng viên chỉ nhập/xem/xóa lịch của mình; quản lý (ADMIN) dùng trang xếp lịch toàn trường và tải Excel kết quả. Backend kiểm tra token và quyền, không dựa vào role trong localStorage.

Xem [hướng dẫn cấp quyền quản lý, phiên đăng nhập và xuất Excel](../document/phan-quyen-va-xuat-excel.md). Sau khi nâng cấp từ phiên bản cũ cần đăng nhập lại.

## Nhập lịch cá nhân

Tải mẫu Excel trong trang Nhập lịch dạy. File `.xlsx` tối đa 10 MB, gồm chín cột theo thứ tự: Mã MH, Tên môn học, Nhóm tổ, Số tín chỉ, Thứ, Tiết bắt đầu, Số tiết, Phòng, Thời gian học. Mốc tuần 1 phải là Thứ Hai. Đọc file, kiểm tra rồi xác nhận để gửi file trực tiếp tới `/api/schedule/upload-excel-teacher`.

Nhập lại cùng mã trường sẽ thay lịch của trường đó thuộc giảng viên đang đăng nhập. Lịch trường khác được giữ. Lịch tuần, thống kê và cảnh báo trùng giờ lấy từ backend. Bảng tiết tham chiếu sau import được nhớ trên thiết bị; localStorage không phải nguồn lịch chính.

## Xếp lịch và tải kết quả

Quản lý tải mẫu phân công hoặc nhập nhanh môn học, giảng viên, số lớp; mã môn và số tín chỉ là tùy chọn. Sau khi xếp, chọn Tải Excel lịch đã xếp để nhận sheet MonHoc gồm chín cột lịch và hai cột Mã GV, Tên GV, cùng sheet TietHoc. File chỉ gồm lớp đã xếp được; thiếu tín chỉ thì để trống có thông báo.

## Kiểm tra

```powershell
npm.cmd test
npm.cmd run build
```

Kiểm thử bao gồm quyền điều hướng, token qua JSON/FormData, phản hồi 401/403, file XLSX đọc lại, giai đoạn đảo tiết và khoảng tuần nghỉ. Kiểm thử API quyền và thuật toán nằm ở backend.

Để chạy toàn bộ frontend/backend/database trong Docker, xem [DOCKER.md](../DOCKER.md).
