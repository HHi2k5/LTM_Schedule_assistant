# Schedule Assistant

Web quản lý lịch giảng dạy, nhập/xuất Excel và xếp lịch theo vai trò **TEACHER / ADMIN**. Frontend dùng React + Vite, backend dùng Java 21 + Spring Boot 3.3, dữ liệu lưu trong MySQL. Khi admin lưu, xếp lại hoặc xóa lịch, trang giảng viên tự cập nhật qua SSE mà không cần F5.

## 1. Lấy source code

```powershell
git clone --branch main https://github.com/HHi2k5/LTM_Schedule_assistant.git
cd LTM_Schedule_assistant
```

Các lệnh dưới đây dùng PowerShell trên Windows. Trên macOS/Linux, thay `Copy-Item` bằng `cp`, `npm.cmd` bằng `npm`, `npx.cmd` bằng `npx`, `mvnw.cmd` bằng `./mvnw`. Cách đặt biến môi trường được ghi riêng ở phần backend.

## 2. Chạy toàn bộ bằng Docker

Máy cần **Git** và **Docker Desktop đang chạy Linux containers**, có Docker Compose v2. Không cần cài Java, Node.js hay MySQL riêng. Lần build đầu cần Internet để tải image và thư viện.

### Bước 1: Kiểm tra Docker

Chạy tại thư mục gốc, nơi có [compose.yaml](compose.yaml):

```powershell
docker version
docker compose version
```

Nếu chưa kết nối được Docker Engine, mở Docker Desktop và chờ Engine khởi động xong.

### Bước 2: Tạo cấu hình local

```powershell
Copy-Item docker.env.example .env
```

Chỉ sao chép khi chưa có `.env`; nếu đã có thì sửa file hiện tại. File này không được commit. Các biến trong [docker.env.example](docker.env.example):

| Biến | Ý nghĩa |
| --- | --- |
| `APP_PORT` | Cổng web trên máy, mặc định `8088`. |
| `DB_PASSWORD` | Mật khẩu user MySQL `schedule` trong Docker. |
| `DB_ROOT_PASSWORD` | Mật khẩu root MySQL để quản trị database Docker. |
| `JWT_SECRET` | Khóa Base64 của ít nhất 32 byte ngẫu nhiên. Có thể để trống khi chạy local; restart backend sẽ yêu cầu đăng nhập lại. |

Thay mật khẩu mẫu trước khi khởi tạo database nếu dùng dữ liệu thật. Muốn giữ phiên đăng nhập qua lần restart, tạo khóa bằng PowerShell rồi điền kết quả vào `JWT_SECRET` trong `.env`:

```powershell
$jwtBytes = New-Object byte[] 32
$jwtRandom = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$jwtRandom.GetBytes($jwtBytes)
$jwtRandom.Dispose()
[Convert]::ToBase64String($jwtBytes)
```

Trên macOS/Linux có thể dùng `openssl rand -base64 32`.

### Bước 3: Build và khởi động

```powershell
docker compose up -d --build
docker compose ps
```

Chờ `db`, `backend`, `frontend` đều **healthy**, sau đó mở **http://localhost:8088**. Nếu đổi `APP_PORT`, dùng cổng tương ứng. Lần đầu hoặc máy ít RAM có thể mất vài phút. Theo dõi backend bằng:

```powershell
docker compose logs -f backend
```

Nhấn `Ctrl+C` để ngừng xem log; container vẫn chạy. Kiểm tra sức khỏe backend:

```powershell
docker compose exec -T backend curl --fail --silent http://localhost:8080/actuator/health
```

Kết quả mong đợi: `{"status":"UP"}`.

Docker tự tạo database `teacher_schedule_db` và cập nhật bảng bằng Hibernate với profile `docker`. **Không cần import SQL thủ công.** Database mới chưa có tài khoản/lịch, không tự lấy dữ liệu từ MySQL trên máy. Backend và MySQL chỉ giao tiếp trong mạng Docker, không mở cổng 8080/3306 ra host. Trình duyệt truy cập web qua Nginx ở cổng 8088.

### Bước 4: Tạo tài khoản và cấp ADMIN

1. Mở web, chọn **Đăng ký**, nhập thông tin và mật khẩu ít nhất 8 ký tự. Tài khoản mới luôn là `TEACHER`; mã GV do server cấp.
2. Để dùng phần xếp lịch, đăng ký một tài khoản quản lý rồi mở MySQL Docker:

   ```powershell
   docker compose exec db mysql -u root -p teacher_schedule_db
   ```

3. Nhập `DB_ROOT_PASSWORD` đã dùng khi tạo database. Kiểm tra username rồi chạy SQL; thay `quanly` bằng username thực tế:

   ```sql
   SELECT teacher_id, username, role FROM teachers WHERE username = 'quanly';
   UPDATE teachers SET role = 'ADMIN' WHERE username = 'quanly';
   exit;
   ```

4. Đăng xuất và đăng nhập lại tài khoản đó để giao diện nhận quyền mới.

Không có tài khoản ADMIN hay mật khẩu ADMIN mặc định. Giảng viên tự đăng ký; ADMIN không tạo tài khoản giảng viên trên giao diện. Khi phân công, dùng đúng mã GV của tài khoản đã đăng ký để giảng viên xem được lịch.

### Dừng, chạy lại và cập nhật

```powershell
# Dừng dịch vụ, giữ dữ liệu MySQL
docker compose down

# Chạy lại
docker compose up -d

# Sau khi lấy code mới
git pull --ff-only origin main
docker compose up -d --build
```

Dữ liệu nằm trong volume `mysql_data`. Không thêm `-v` vào lệnh `down` nếu muốn giữ tài khoản và lịch. Thay mật khẩu trong `.env` sau khi database đã được tạo không tự đổi mật khẩu bên trong MySQL; cần đổi bằng MySQL và cập nhật cấu hình tương ứng.

Sau khi nâng cấp frontend, tải lại trang một lần để nhận mã mới. Các thay đổi lịch sau đó được cập nhật trực tiếp.

## 3. Chạy từng phần để phát triển

Cần **JDK 21**, **Node.js 22.12+ và npm**, **MySQL 8.x**. Maven Wrapper có sẵn, không bắt buộc cài Maven. Mở hai terminal cho backend và frontend.

### 3.1. Chuẩn bị MySQL local

Kết nối MySQL local bằng tài khoản quản trị, tạo database và user có quyền cập nhật schema. Thay mật khẩu mẫu `your_local_password` bằng mật khẩu của bạn:

```sql
CREATE DATABASE IF NOT EXISTS teacher_schedule_db
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'schedule'@'localhost' IDENTIFIED BY 'your_local_password';
GRANT ALL PRIVILEGES ON teacher_schedule_db.* TO 'schedule'@'localhost';
```

Nếu user đã tồn tại, dùng mật khẩu hiện có hoặc đổi bằng `ALTER USER`. Profile `dev` dùng Hibernate `update` để tạo/cập nhật bảng, không cần chạy script SQL cũ. Database Docker mặc định không mở cổng ra host; phần này dùng MySQL local riêng.

### 3.2. Chạy backend

Kiểm tra `java -version` và `JAVA_HOME` cùng trỏ tới JDK 21. Từ thư mục gốc, mở terminal PowerShell:

```powershell
cd server
$env:SPRING_PROFILES_ACTIVE = 'dev'
$env:DB_URL = 'jdbc:mysql://127.0.0.1:3306/teacher_schedule_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Ho_Chi_Minh'
$env:DB_USERNAME = 'schedule'
$env:DB_PASSWORD = 'your_local_password'
.\mvnw.cmd spring-boot:run
```

Trên macOS/Linux, tại thư mục `server`:

```bash
chmod +x mvnw
export SPRING_PROFILES_ACTIVE=dev
export DB_URL='jdbc:mysql://127.0.0.1:3306/teacher_schedule_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Ho_Chi_Minh'
export DB_USERNAME=schedule
export DB_PASSWORD='your_local_password'
./mvnw spring-boot:run
```

Backend chạy tại **http://localhost:8080**, kiểm tra sức khỏe ở `/actuator/health`. Cấu hình [dev](server/src/main/resources/application-dev.yml) trên `main` lấy kết nối từ biến môi trường, không chứa mật khẩu cá nhân. Để giữ JWT qua lần restart, đặt thêm biến môi trường `JWT_SECRET` bằng khóa Base64 đã tạo ở phần Docker.

File `.env` ở thư mục gốc chỉ được Docker Compose đọc; Maven không tự đọc file đó. Phải đặt biến môi trường trong terminal chạy backend như trên.

Nếu cổng 8080 bận:

```powershell
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.arguments=--server.port=8081'
```

### 3.3. Chạy frontend

Mở terminal mới từ thư mục gốc:

```powershell
cd frontend
npm.cmd ci
Copy-Item .env.example .env.local
npm.cmd run dev
```

Chỉ sao chép `.env.local` khi chưa có. Mở **http://127.0.0.1:5173**. Giữ `VITE_API_BASE_URL=` trống để Vite chuyển `/api` tới backend qua proxy. Nếu backend chạy cổng 8081, sửa `BACKEND_PROXY_TARGET=http://127.0.0.1:8081` trong `.env.local`, rồi restart Vite.

Đăng ký và cấp ADMIN giống Docker, nhưng kết nối đúng database MySQL local để cấp quyền.

## 4. Sử dụng và kiểm tra realtime

- **TEACHER**: nhập lịch Excel cá nhân, xem lịch tuần/trang Hôm nay, xuất lịch và đổi mật khẩu.
- **ADMIN**: quản lý phòng và phân công giảng viên, tải mẫu Excel, nhập kế hoạch và xếp lịch toàn khoa, bổ sung thỉnh giảng, xem lịch và tải Excel kết quả.
- Luồng xếp lịch ba file tiếp tục xử lý lớp khác khi một lớp thiếu GV hoặc không xếp đủ. Kết quả hiển thị các lớp chưa xếp và cho tải báo cáo Excel; nếu có lớp hoàn tất thì lưu phần hoàn tất, nếu tất cả thất bại thì giữ lịch cũ.
- Tải mẫu đầu vào trực tiếp trên giao diện. Không cần file Excel có sẵn để khởi động ứng dụng.

Kiểm tra realtime bằng hai trình duyệt hoặc một cửa sổ thường và một cửa sổ ẩn danh: đăng nhập ADMIN ở một bên, GV ở bên còn lại. Admin lưu/xếp lại/xóa lịch của đúng mã GV; lịch bên GV tự cập nhật, giữ tuần và bộ lọc. Khi mất mạng, giao diện tự nối lại và đọc lại lịch. JWT hết hạn thì cần đăng nhập lại.

Nginx đã cấu hình SSE. Thông báo hiện phục vụ một backend như Compose; nếu chạy nhiều bản sao backend cần pub/sub dùng chung. Xem [chi tiết realtime](document/realtime-schedule.md) và [định dạng Excel](frontend/README.md).

## 5. Build và kiểm thử

Chạy trong thư mục `server`:

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
```

File JAR được tạo tại `server/target/schedule-assistant-service.jar`. Khi chạy JAR, vẫn cần biến môi trường database và profile ở phần 3.2:

```powershell
java -jar target/schedule-assistant-service.jar
```

Các test backend hiện có dùng H2 riêng ở chế độ MySQL, không ghi database đang chạy. Chạy các lệnh frontend trong thư mục `frontend` sau `npm.cmd ci`:

```powershell
node --test --test-concurrency=1 src/*.test.js
npm.cmd run build
npx.cmd playwright install chromium
npm.cmd run test:browser -- tests/realtime.spec.js tests/partial-scheduling.spec.js --workers=1
```

Playwright tự bật Vite ở cổng 4179; cần để cổng này trống. Nhóm realtime và xếp lịch một phần dùng dữ liệu test riêng trên desktop/mobile. Chạy toàn bộ browser test bằng `npm.cmd run test:browser -- --workers=1`.

Frontend build nằm trong `frontend/dist`. Để chạy bản build đầy đủ có API, dùng Docker/Nginx ở phần 2. `npm.cmd run preview` chỉ phục vụ file tĩnh, không cấu hình proxy API như Vite dev.

## 6. Lỗi thường gặp

| Hiện tượng | Cách kiểm tra |
| --- | --- |
| Không kết nối được Docker | Mở Docker Desktop, chọn Linux containers, chờ Engine sẵn sàng và chạy `docker version`. |
| Cổng 8088 đã dùng | Đổi `APP_PORT` trong `.env`, chạy lại `docker compose up -d`, mở cổng mới. |
| Backend đang `starting`, web chưa lên | Xem `docker compose logs -f backend` và `docker compose logs --tail 80 db`; chờ backend hoàn tất khởi động. |
| MySQL báo `Access denied` | Đối chiếu mật khẩu thực với biến môi trường. Volume đã tồn tại giữ mật khẩu cũ dù `.env` thay đổi. |
| Maven báo phiên bản Java không hợp lệ | Kiểm tra `java -version`, `JAVA_HOME`, mở terminal mới sau khi sửa đường dẫn JDK. |
| Frontend không gọi được API | Kiểm tra backend, `BACKEND_PROXY_TARGET`, để `VITE_API_BASE_URL` trống khi dùng proxy; restart Vite sau khi đổi env. |
| Đăng ký xong không có menu xếp lịch | Tài khoản mới là TEACHER; cấp ADMIN trong đúng database rồi đăng nhập lại. |
| Đổi lịch nhưng GV không thấy | Kiểm tra đúng mã GV/tuần, trạng thái kết nối trực tiếp, backend đang chạy bản mới; tải lại một lần sau nâng cấp frontend. |

## 7. Chia sẻ web đang chạy

Với Docker đang hoạt động, mở terminal khác và chạy `ngrok http 8088` nếu đã cài/cấu hình ngrok. Chia sẻ URL HTTPS mà ngrok cấp. Nếu đổi `APP_PORT`, dùng cổng mới. Giữ máy, Docker và ngrok hoạt động trong thời gian chia sẻ.

Xem thêm [hướng dẫn Docker](DOCKER.md), [hướng dẫn frontend](frontend/README.md) và [quyền truy cập, tài khoản, Excel](document/phan-quyen-va-xuat-excel.md).
