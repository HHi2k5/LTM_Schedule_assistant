# Schedule Assistant

Ứng dụng quản lý lịch giảng dạy cá nhân và xếp lịch toàn trường từ Excel.

## Chạy bằng Docker

Cần Docker Desktop đang chạy Linux containers. Tại thư mục gốc:

```powershell
docker compose up -d --build
docker compose ps
```

Mở http://localhost:8088 khi các dịch vụ báo healthy. Docker chạy frontend Nginx, backend Java 21 và MySQL; dữ liệu được lưu trong volume `mysql_data`.

Có thể sao chép `docker.env.example` thành `.env` để cấu hình cổng, mật khẩu database và `JWT_SECRET`. Đặt khóa JWT ổn định để giữ phiên đăng nhập sau khi backend khởi động lại. Các giá trị mật khẩu trong file mẫu chỉ phục vụ môi trường local.

Tài khoản đăng ký mới có vai trò TEACHER. Để dùng chức năng xếp lịch toàn trường, gán ADMIN cho tài khoản quản lý trong database:

```sql
UPDATE teachers SET role = 'ADMIN' WHERE username = 'ten_quan_ly';
```

Xem log và dừng ứng dụng:

```powershell
docker compose logs -f backend
docker compose down
```

Lệnh dừng trên giữ lại volume dữ liệu.

## Chạy từ mã nguồn

Backend cần Java 21 và MySQL. Cấu hình kết nối tại `server/src/main/resources/application-dev.yml`, rồi chạy:

```powershell
cd server
.\mvnw.cmd spring-boot:run
```

Frontend cần Node.js 20.19+ hoặc 22.12+:

```powershell
cd frontend
npm.cmd ci
npm.cmd run dev
```

Frontend dùng http://localhost:5173 và chuyển tiếp `/api` tới backend tại http://localhost:8080. Sao chép `frontend/.env.example` thành `frontend/.env.local` nếu cần chỉnh proxy. Trên macOS/Linux dùng `./mvnw` và `npm`.

## Chia sẻ bản Docker qua ngrok

```powershell
ngrok http 8088
```

Giữ Docker và ngrok cùng chạy để truy cập bằng URL HTTPS được cấp.
