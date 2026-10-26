# Phân quyền và tải Excel lịch đã xếp

## Sử dụng

- Giảng viên (`TEACHER`): nhập Excel và quản lý lịch của chính mã GV đã đăng nhập. Không có trang/nút xếp lịch toàn trường.
- Quản lý (`ADMIN`): xếp lịch bằng Excel phân công hoặc bảng nhập nhanh, xem lịch theo mã GV, tải kết quả Excel. Không có trang/nút nhập lịch cá nhân.
- Đăng ký trên giao diện luôn tạo TEACHER. Quyền ADMIN được cấp bởi người quản trị database, không chọn trên form đăng ký.
- Các phiên đăng nhập cũ lưu bằng localStorage không còn hợp lệ; đăng nhập lại sau cập nhật.

## Cấp quyền quản lý

Đăng ký tài khoản bình thường trước. Người quản trị kết nối đúng database đang chạy, kiểm tra username rồi cấp quyền cho chính tài khoản cần dùng:

```sql
SELECT teacher_id, username, role FROM teachers WHERE username = 'ten_dang_nhap_quan_ly';
UPDATE teachers SET role = 'ADMIN' WHERE username = 'ten_dang_nhap_quan_ly';
```

Thay chuỗi ví dụ bằng username thật; kiểm tra đúng một tài khoản trước khi UPDATE. Sau đó đăng xuất/đăng nhập lại để giao diện nhận quyền mới. Đổi lại `TEACHER` để thu hồi quyền quản lý; backend đọc role từ database ở mỗi request nên token cũ không giữ được quyền đã bị thu hồi.

Với Docker có thể mở MySQL bằng:

```powershell
docker compose exec db mysql -u root -p teacher_schedule_db
```

Nhập mật khẩu root của database Docker (giá trị `DB_ROOT_PASSWORD` đã dùng khi tạo volume; mặc định trong Compose là `root_local_password`). MySQL local và database Docker là hai nguồn riêng; cấp quyền đúng nơi ứng dụng đang kết nối.

Không có tài khoản hoặc mật khẩu ADMIN được tạo tự động. Cần rà soát các tài khoản ADMIN đã tạo bằng form đăng ký ở phiên bản cũ nếu không muốn giữ các quyền đó.

## Nhập phân công và xuất Excel

Mẫu phân công có năm cột theo thứ tự:

```text
Môn học | Giảng viên (Mã GV - Họ tên) | Số lớp mở | Mã MH (tùy chọn) | Số tín chỉ (tùy chọn)
```

File ba cột cũ vẫn sử dụng được. Thiếu mã môn thì server sinh mã; thiếu số tín chỉ thì để trống, không suy từ số tiết. Với cùng mã môn, tên môn và tín chỉ phải thống nhất; mỗi giảng viên chỉ có một dòng phân công cho môn đó. Muốn đổi số lớp của giảng viên thì sửa dòng cũ.

Sau khi xếp thành công, chọn **Tải Excel lịch đã xếp** ở vùng kết quả. File có:

- Sheet `MonHoc`: `Mã MH`, `Tên môn học`, `Nhóm tổ`, `Số tín chỉ`, `Thứ`, `Tiết bắt đầu`, `Số tiết`, `Phòng`, `Thời gian học:`, `Mã GV`, `Tên GV`.
- Sheet `TietHoc`: `period`, `start`, `end`, lấy bảng giờ đã dùng khi xếp lịch.
- Mỗi giai đoạn đảo tiết là bản ghi riêng; nếu có tuần nghỉ ở giữa thì tách khoảng ngày để không xuất thêm buổi học.

Excel chỉ gồm lớp đã xếp được. Cảnh báo thiếu tín chỉ hoặc lớp chưa xếp hiển thị ở kết quả. Nút tải dùng mốc tuần và bảng tiết từ lần xếp đó, không lấy các giá trị vừa sửa trên form.

Kết quả tải nằm trong phiên trang hiện tại; reload hoặc đăng xuất sẽ xóa kết quả đang hiển thị. Chưa có lịch sử tác vụ và chức năng nhập lại hàng loạt file nhiều giảng viên.

## Phiên đăng nhập và cấu hình

Backend cấp JWT hạn tám giờ; frontend gửi Authorization cho cả JSON và upload Excel. API từ chối request thiếu/token sai/hết hạn với `401`, sai quyền với `403`.
Token lưu ở sessionStorage (hoặc bộ nhớ nếu trình duyệt chặn storage); localStorage chỉ giữ lựa chọn và bảng tiết, không quyết định quyền.

Đặt `JWT_SECRET` bằng chuỗi Base64 của ít nhất 32 byte ngẫu nhiên để giữ phiên qua lần restart backend. Không đặt biến này thì backend tự tạo khóa mới khi khởi động; mọi người cần đăng nhập lại sau restart. Không commit khóa thật. Trong Docker đặt biến này trong `.env`; khi chạy Maven đặt vào môi trường terminal.

Ví dụ tạo khóa và đặt cho lần chạy Maven hiện tại:

```powershell
$jwtKeyBytes = New-Object byte[] 32
$jwtRandom = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$jwtRandom.GetBytes($jwtKeyBytes)
$jwtRandom.Dispose()
$env:JWT_SECRET = [Convert]::ToBase64String($jwtKeyBytes)
cd server
.\mvnw.cmd spring-boot:run
```

Tài khoản mới lưu mật khẩu BCrypt. Tài khoản SHA-256 từ phiên bản cũ vẫn đăng nhập được và được nâng cấp hash khi đăng nhập thành công, không cần đặt lại mật khẩu.

## Chạy và kiểm tra

```powershell
# Frontend
cd frontend
npm.cmd test
npm.cmd run build

# Backend, ở thư mục server
.\mvnw.cmd test

# Docker, ở thư mục gốc
docker compose up -d --build
```

Với Vite/ngrok, để `VITE_API_BASE_URL=` và giữ proxy `/api`. Docker/Nginx chuyển tiếp Authorization; không cần mở cổng backend/MySQL ra ngoài để đăng nhập hoặc tải Excel.

Nếu cổng 8080 đã được ứng dụng khác sử dụng, chạy backend bằng `.\mvnw.cmd spring-boot:run '-Dspring-boot.run.arguments=--server.port=8081'` và đặt `BACKEND_PROXY_TARGET=http://127.0.0.1:8081` trong `frontend/.env.local`. Trình duyệt vẫn mở frontend cổng 5173; Vite sẽ chuyển API đến cổng mới. Thông báo “backend đang chạy phiên bản cũ” nghĩa là cần kiểm tra đúng backend mà proxy đang gọi, không phải đổi mật khẩu tài khoản.
