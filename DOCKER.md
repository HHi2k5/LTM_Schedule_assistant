# Chạy bằng Docker Desktop

Docker Compose chạy frontend (Nginx), backend (Java 21) và MySQL trong ba container.
Máy chạy chỉ cần Docker Desktop với Linux containers; không cần cài Node.js, Java hoặc MySQL riêng.

## Khởi động

Mở Docker Desktop, chờ Engine chạy, rồi mở terminal tại thư mục gốc dự án:

```powershell
docker compose up -d --build
docker compose ps
```

Lần đầu cần Internet để tải image và dependencies. Mở http://localhost:8088 sau khi các container báo healthy.
Đăng ký tài khoản mới trên giao diện. Database Docker ban đầu trống, không tự lấy dữ liệu từ MySQL trên máy.
Backend tự tạo/cập nhật bảng bằng Hibernate với profile `docker`.

Frontend gọi `/api` cùng origin; Nginx chuyển tiếp tới backend trong mạng Docker.
Backend và MySQL không mở cổng ra máy host. Cấu hình dev và `.env.local` không được đưa vào image.

## Cấu hình tùy chọn

Mặc định dùng cổng 8088 và mật khẩu dành cho chạy local trong `compose.yaml`.
Để đổi trước lần khởi tạo database đầu tiên, sao chép file mẫu rồi chỉnh `.env`:

```powershell
Copy-Item docker.env.example .env
docker compose up -d --build
```

Nếu đã có `.env`, chỉnh file đó thay vì ghi đè. Đổi mật khẩu trong `.env` sau khi database đã khởi tạo không tự đổi mật khẩu trong MySQL.

## Xem log và dừng

```powershell
docker compose logs -f backend
docker compose down
```

`down` giữ dữ liệu trong volume `mysql_data`. Không thêm `-v` nếu muốn giữ dữ liệu.
Sau khi sửa code, chạy lại `docker compose up -d --build`.

## Chia sẻ bằng ngrok

Giữ Docker Desktop và stack đang chạy, mở terminal khác:

```powershell
ngrok http 8088
```

Gửi URL HTTPS ở dòng Forwarding. Bản Docker dùng Nginx nên không cần thêm tên miền vào Vite `allowedHosts`.
Nếu đã đổi `APP_PORT`, dùng cổng đó thay cho 8088.

## Chạy trên máy khác

Gửi repository có các Dockerfile và `compose.yaml` cho người đó. Trên máy có Docker Desktop, chạy cùng lệnh khởi động ở thư mục gốc.
Mỗi máy có database Docker riêng; tài khoản và lịch không tự đồng bộ giữa hai máy.
