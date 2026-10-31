# API danh mục — chặng 1

Các API dưới đây yêu cầu token ADMIN trong header `Authorization: Bearer <token>`. Chặng 1 cung cấp nền tảng backend; giao diện quản lý/import thuộc chặng 2, chọn danh mục thay file khi xếp lịch thuộc chặng 3.

Giao diện quản trị, nhập Excel xem trước/ghi nguyên khối và đối chiếu dữ liệu cũ đã được bổ sung ở [hướng dẫn chặng 2](./danh-muc-huong-dan-chang-2.md).

## Phạm vi và đường dẫn

Tiền tố `R = /api/admin/institutions`. Mã trường trên URL là mã danh mục, tương ứng `school_code` hiện có, ví dụ `PTIT`. ID khoa, hồ sơ GV, phân công và phòng là số do server cấp.

| Đường dẫn | GET | POST | PUT |
|---|---|---|---|
| `R` | Danh sách trường | Tạo trường | — |
| `R/{schoolCode}` | — | — | Sửa trường |
| `R/{schoolCode}/departments` | Danh sách khoa | Tạo khoa | — |
| `R/{schoolCode}/departments/{id}` | — | — | Sửa khoa |
| `R/{schoolCode}/lecturers` | Danh sách GV | Tạo hồ sơ GV | — |
| `R/{schoolCode}/lecturers/{id}` | — | — | Sửa hồ sơ GV |
| `R/{schoolCode}/lecturers/{lecturerId}/assignments` | Phân công mặc định | Tạo phân công | — |
| `R/{schoolCode}/lecturers/{lecturerId}/assignments/{id}` | — | — | Sửa phân công |
| `R/{schoolCode}/rooms` | Danh sách phòng, cả đang/ngừng hoạt động | Tạo phòng | — |
| `R/{schoolCode}/rooms/{id}` | — | — | Sửa phòng |

POST thành công trả HTTP 201 và bản ghi đã lưu. PUT/GET thành công trả HTTP 200. Chưa có API xóa cứng: ngừng sử dụng bằng PUT với `active: false`.

## Dữ liệu ví dụ

Tạo trường trước, sau đó tạo khoa, GV, phân công và phòng. Lấy ID từ response của mỗi bước để dùng ở bước sau.

### Trường

POST `/api/admin/institutions`:

```json
{
  "schoolCode": "PTIT",
  "name": "Học viện Công nghệ Bưu chính Viễn thông"
}
```

### Khoa

POST `/api/admin/institutions/PTIT/departments`:

```json
{
  "departmentCode": "CNTT",
  "name": "Khoa Công nghệ thông tin"
}
```

### Giảng viên

POST `/api/admin/institutions/PTIT/lecturers` (thay `departmentId` bằng ID thật; có thể bỏ qua nếu chưa phân khoa):

```json
{
  "lecturerCode": "GV-001",
  "fullName": "Nguyễn Văn A",
  "departmentId": 1,
  "lecturerType": "STAFF"
}
```

- `lecturerType`: `STAFF` (cơ hữu) hoặc `VISITING` (thỉnh giảng).
- `lecturerCode` là mã hồ sơ trong trường. Chỉ khi biết đúng mã tài khoản đã đăng ký mới truyền thêm `accountTeacherId`, ví dụ `GVAB12`.
- `accountTeacherId` để trống sẽ tự tạo tài khoản cho GV đang hoạt động khi lưu, theo [quy tắc MGV và mật khẩu không dấu](./tai-khoan-giang-vien-tu-dong.md). Tài khoản đã liên kết giữ mật khẩu hiện tại. Không tự ghép theo tên; tài khoản ADMIN không thể được liên kết.
- Cùng một tài khoản có thể được liên kết ở hai trường, nhưng không được gắn hai hồ sơ trong cùng trường.
- Khoa phải thuộc đúng trường trên URL. Việc lưu hồ sơ không thay lịch cá nhân, mật khẩu, vai trò hoặc mốc tuần của tài khoản.

### Phân công môn mặc định

POST `/api/admin/institutions/PTIT/lecturers/1/assignments` (thay `1` bằng ID hồ sơ thật):

```json
{
  "courseCode": "CSDL",
  "courseName": "Cơ sở dữ liệu",
  "cohort": "2024",
  "theoryWeight": 2,
  "practiceWeight": 8
}
```

`cohort` là khóa sinh viên, khác khoa/bộ môn. Trọng số từ 0 đến 10000; 0 nghĩa là không nhận loại LT/TH đó. Phân công đang hoạt động phải có ít nhất một trọng số lớn hơn 0. Mỗi bộ giảng viên–môn–khóa có một bản ghi mặc định; có thể sửa trạng thái/trọng số sau này.

### Phòng

POST `/api/admin/institutions/PTIT/rooms`:

```json
{
  "roomCode": "101-A2",
  "roomName": "Phòng 101 A2",
  "roomType": "THEORY",
  "capacity": 60,
  "building": "A2"
}
```

`roomType`: `THEORY` hoặc `PRACTICE`. API cũng nhận các tên loại cũ `THEORY_STANDARD`, `THEORY_MEDIUM`, `THEORY_LARGE`, `LAB_PRACTICE`; server chuẩn hóa lại loại phòng theo sức chứa, đồng bộ `isLab`. Sức chứa từ 1 đến 10000.

## Cập nhật và tránh ghi đè

Mỗi response có `revision`. PUT gửi **đủ thông tin bản ghi**, kèm `revision` vừa đọc và `active` rõ ràng. Các trường quan hệ nullable bị bỏ qua trong PUT được hiểu là bỏ liên kết, không phải giữ nguyên; ví dụ muốn giữ khoa/tài khoản thì gửi lại cả hai trường đó.

Ví dụ ngừng sử dụng phòng, với revision thực lấy từ GET:

```json
{
  "roomCode": "101-A2",
  "roomName": "Phòng 101 A2",
  "roomType": "THEORY",
  "capacity": 60,
  "building": "A2",
  "active": false,
  "revision": 0
}
```

- Khi có thay đổi, response trả revision mới. Nếu bản ghi đã thay đổi từ lần đọc, HTTP 409; tải lại để đối chiếu trước khi thử lại.
- Mã trường/khoa/GV/phòng và bộ môn–khóa của phân công không đổi qua PUT. Nếu cần mã khác, ngừng sử dụng bản ghi cũ và tạo bản ghi mới.
- Các mã được bỏ khoảng trắng hai đầu, chuyển thành chữ hoa; chỉ nhận A–Z, chữ số, dấu chấm, gạch dưới, gạch ngang hoặc gạch chéo. Riêng mã trường không nhận gạch chéo vì nằm trên URL.
- Mã khoa/GV/phòng có thể trùng giữa hai trường; không được trùng trong cùng trường.
- Ngừng sử dụng phòng giữ dữ liệu cũ và loại phòng khỏi tất cả luồng xếp lịch mới, kể cả khi file nhập lại chứa phòng đó. Không tự di chuyển các buổi đã xếp trước đó.

## Lỗi và tương thích

| HTTP | Ý nghĩa |
|---|---|
| 400 | Dữ liệu sai/thiếu, liên kết loại tài khoản không hợp lệ, thay mã, thiếu revision/trạng thái khi cập nhật |
| 401 | Chưa đăng nhập hoặc token không hợp lệ |
| 403 | Không phải ADMIN |
| 404 | Trường/khoa/GV/phòng/phân công không tồn tại trong phạm vi URL |
| 409 | Trùng mã, liên kết tài khoản trùng trong trường hoặc cập nhật revision cũ |

Schema V4 chỉ bổ sung bảng danh mục và cột trạng thái/revision cho phòng; không tự nhập lại dữ liệu giảng viên từ lịch cũ, không sửa các migration trước. Phòng cũ mặc định đang hoạt động. Tạo danh mục trường với đúng mã cũ sẽ xem và quản lý được các phòng đã lưu của trường đó.

Chưa ép FK từ phòng cũ sang danh mục trường khi dữ liệu cũ chưa đối chiếu. Cấu hình giờ chung, nhập Excel danh mục và dùng GV từ DB thay file sẽ bổ sung ở các chặng sau. Đến chặng 3, luồng ba file hiện tại vẫn được giữ tương thích; chỉ danh sách phòng khả dụng có thêm điều kiện đang hoạt động.

API dùng khóa ghi chung hiện có và revision để chống ghi đè. Danh sách ở chặng đầu chưa phân trang; bổ sung phân trang/tìm kiếm server khi nối UI danh mục nếu kích thước thực tế cần thiết.

## Kiểm thử

- [CatalogIntegrationTest](../server/src/test/java/com/service/CatalogIntegrationTest.java): API thật + H2, round-trip DB, đúng phạm vi trường/khoa, unique, liên kết tài khoản, DTO không lộ credentials, ngừng hoạt động và revision.
- [MySqlCatalogMigrationTest](../server/src/test/java/com/service/MySqlCatalogMigrationTest.java): nâng từ V3 lên V4 trên MySQL tạm, giữ tài khoản/lịch/phòng cũ và kiểm tra unique/FK/trọng số.
- [WeeklyScheduleEngineTest](../server/src/test/java/com/service/WeeklyScheduleEngineTest.java): phòng ngừng hoạt động không được sử dụng ở cả thuật toán tuần và luồng cũ.

Chạy bộ test mặc định với `server/mvnw.cmd -o test`; bật biến môi trường `RUN_MYSQL_TESTS=true` để chạy thêm các test MySQL/Testcontainers. Database phát triển/production không được dùng bởi các test này.
