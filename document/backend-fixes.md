# Bản sửa backend theo kế hoạch rà soát

Ngày: 26/10/2026. Áp dụng cho `backend-audit-plan.md`.

Cập nhật 27/10/2026: đã bỏ chức năng ADMIN cấp tài khoản giảng viên ở cả frontend và backend. Giảng viên tự đăng ký; chức năng đổi mật khẩu cá nhân vẫn hoạt động.

API lịch tuần hiện trả thêm `schools`, gồm thông tin trường và toàn bộ bảng `periods` (`period`, `start`, `end`) theo số tiết. Bảng giờ vẫn được trả khi tuần không có buổi dạy; không suy ra giờ tiết từ các lớp đang học. Frontend dùng dữ liệu này để hiện đủ số tiết ở cột trái sau tải lại trang, kể cả lịch do ADMIN xếp và tiết không có lớp. Không thay đổi schema database.

Kiểm chứng bản sửa cột tiết ngày 27/10/2026: 34 test backend liên quan lịch/quyền/persistence, 21 test dữ liệu frontend và 2 lượt browser test desktop/mobile đều qua. Đã build và cập nhật Docker; API và giao diện thật hiện đủ tiết 1–12 với một lớp ở tiết 4–6, kể cả sau tải lại trang. Tài khoản và lịch tạm đã được xóa sau kiểm tra.

## Hành vi sau khi sửa

| Mã | Bản sửa |
|---|---|
| BE-01 | Giảng viên tự đăng ký và nhận mã GV ngẫu nhiên từ server. Không nhận hồ sơ đã có lịch bằng mã tự nhập. Không có chức năng ADMIN cấp tài khoản hoặc liên kết hồ sơ chưa có tài khoản qua API. |
| BE-02 | Từ chối lịch nhập rỗng và dòng thiếu dữ liệu. Kết quả xếp không đầy đủ trả `saved=false`, `status=NOT_SAVED`; không ghi đè lịch cũ. |
| BE-03 | Từ chối mốc tuần khác khi GV đã có lịch; yêu cầu mốc Thứ Hai. |
| BE-04 | Loại JOIN lặp theo định danh entity, giữ hai buổi của hai trường để phát hiện xung đột. |
| BE-05 | Import cá nhân bắt buộc có `TietHoc`, giữ giờ trong file. Kiểm tra số nguyên, ngày, thứ, tuần và dải tiết ở server; không đoán giờ khi thiếu tiết. |
| BE-06 | Quota có thể bằng 0; tổng quota đúng số lớp. Giữ quota chỉ định từ file cũ, chỉ chia đều khi không có quota. |
| BE-07 | Chỉ chọn ca có đủ từng tiết trong bảng giờ. Ca ưu tiên sai có thể được thay bằng ca khác hợp lệ, không tạo slot vượt bảng tiết. |
| BE-08 | Entry point Excel dùng `rollbackFor=Exception.class`; đọc kế hoạch/GV trước khi ghi phòng. Kết quả xếp một phần rollback cả cập nhật phòng của tác vụ. |
| BE-09 | Nạp lịch đã lưu ngoài phạm vi thay thế; khóa ghi trong DB dùng chung giữa các instance đến hết transaction. |
| BE-10 | Tìm/cập nhật phòng theo trường + mã phòng; constraint cho phép hai trường có cùng mã phòng. API đọc phòng nhận `schoolId`. |
| BE-11 | Xung đột dùng ngày thực từ mốc tuần và giờ thực từng trường; tuần không giao nhau không khóa nhầm giờ. |
| BE-12 | Advice trả JSON lỗi thống nhất: 400/401/403/404/409/413/500; lỗi nội bộ được log, không lộ SQL trong response. |
| BE-13 | Flyway V1 cho DB mới, V2 nâng cấp cột tài khoản/ràng buộc phòng. Prod dùng biến môi trường, kiểm tra schema và yêu cầu JWT secret/CORS origins. |

`ScheduleWriteLock` dùng một dòng mutex trong DB để tuần tự hóa đăng ký và ghi lịch/phòng. Cách này ưu tiên tính đúng, đồng thời giới hạn throughput ghi lịch. Khóa Java trong một process không được dùng thay khóa DB.

Kiểm thử MySQL thực phát hiện deadlock khi `INSERT IGNORE` lấy khóa chia sẻ rồi hai transaction cùng nâng lên `FOR UPDATE`. Đã đổi bước tạo/lấy dòng mutex sang `INSERT ... ON DUPLICATE KEY UPDATE` để lấy khóa ghi ngay. Test ghi lịch/đăng ký đồng thời đã qua trên MySQL và H2; test cũng chờ worker kết thúc trước khi chuyển tình huống để không để thao tác nền ảnh hưởng test tiếp theo.

Phạm vi thay thế vẫn là toàn bộ lịch một trường thuộc các GV có quota > 0 trong yêu cầu. Lịch GV ngoài phạm vi được giữ và được tính là đã chiếm phòng. Kết quả một phần chỉ là bản xem trước; frontend không báo đã lưu hoặc chuyển sang lịch đã lưu.

## Đăng ký và danh tính giảng viên

Giảng viên tự đăng ký qua `POST /api/schedule/auth/register`, gửi `username`, `password`, `fullName`. Server tạo mã GV mới và trả trong response; không cho nhận hồ sơ có sẵn chỉ bằng mã GV. Người dùng xem/sao chép mã của mình trong Cài đặt để gửi cho trường khi phân công lịch.

Chức năng ADMIN cấp tài khoản đã gỡ. URL cũ `/api/schedule/auth/provision` không còn controller/service xử lý; quy tắc mặc định từ chối trả 403 cho phiên đã đăng nhập, 401 khi chưa đăng nhập. Không có API thay thế để quản lý tự tạo tài khoản giảng viên.

Tài khoản và lịch đã tồn tại được giữ nguyên. File xếp lịch cần dùng đúng mã trên tài khoản giảng viên. Hồ sơ phân công tự sinh khi bỏ trống file GV không tự có tài khoản và không tự gắn vào tài khoản đăng ký sau.

ADMIN và TEACHER đổi mật khẩu của chính mình bằng `POST /api/schedule/auth/change-password` với `currentPassword`, `newPassword`. API xác định người dùng từ token, kiểm tra mật khẩu hiện tại và lưu BCrypt; phiên JWT đã cấp vẫn có hiệu lực đến hết hạn.

## Excel và vận hành

- Import cá nhân cần `MonHoc` (9 cột như mẫu) và `TietHoc` (`period`, `start`, `end`). Giờ dạng `HH:mm` hoặc ô giờ Excel. Thiếu bảng giờ sẽ báo lỗi; tải mẫu hiện tại để điền giờ thực của trường.
- Nhập trường thứ hai dùng cùng mốc tuần của GV. Server từ chối đổi mốc âm thầm.
- Không tự sinh 35 phòng khi DB trống; cần file phòng hoặc phòng đã lưu thuộc trường.
- Mỗi file tối đa 10 MB, tổng request tối đa 32 MB cho bộ ba file.
- `CORS_ORIGINS` là danh sách origin/pattern cách nhau bằng dấu phẩy, gồm localhost/domain ngrok thực sự dùng.
- Prod cần `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` (Base64, ít nhất 32 byte ngẫu nhiên), `CORS_ORIGINS`.

## Migration

Migration nằm trong `server/src/main/resources/db/migration/`; mặc định **tắt**, chưa chạy lên DB dev/prod trong lượt sửa này.

1. Chạy test MySQL trên container riêng trước khi triển khai.
2. Sao lưu DB đích, kiểm tra username trùng và mã trường của phòng cũ. V2 gán phòng chưa có mã trường về `PTIT`; chỉnh dữ liệu trước nếu quy ước thực tế khác.
3. Cấu hình đúng DB đích và bật `DB_MIGRATIONS_ENABLED=true` cho lần khởi động được quản trị viên cho phép. Flyway baseline version 0 trên schema cũ rồi chạy V1/V2; không xóa lịch.
4. Prod dùng `ddl-auto: validate`. Không dùng Hibernate update thay migration constraint phòng: unique toàn cục cũ cần được V2 gỡ.

Khóa ghi cần bảng `schedule_write_mutex`; migration tạo bảng/dòng khóa. Dev Hibernate update có thể tạo bảng từ entity, nhưng vẫn cần migration để nâng cấp constraint phòng cũ.

## Kiểm chứng

Kết quả đã đối chiếu: **65 test backend khác nhau đã qua, gồm 8 test MySQL**. Lượt cuối chạy lại **8 test MySQL + 7 test H2: 15/15 qua, không skip**; các nhóm khác đã qua ở lượt trước. **14 test frontend và production build thành công**. Kiểm tra whitespace bằng `git diff --check` không báo lỗi.

Test mặc định dùng H2 riêng (`application-test.yml`), không dùng DB dev. Integration test kiểm tra lưu thật, không mất lịch, rollback sau ghi, `IOException` sau INSERT phòng, hai lần xếp đồng thời, đăng ký username đồng thời và unique phòng theo trường.

```powershell
cd server
.\mvnw.cmd test
```

Bộ MySQL Testcontainers chỉ bật khi có Docker daemon:

Dự án dùng Testcontainers 1.21.4 để tương thích Docker Engine mới; xem [release chính thức](https://github.com/testcontainers/testcontainers-java/releases/tag/1.21.4). Khi `RUN_MYSQL_TESTS=true`, không kết nối được Docker sẽ làm test thất bại, không tự skip.

```powershell
$env:RUN_MYSQL_TESTS = 'true'
.\mvnw.cmd '-Dtest=MySqlScheduleTest' test
```

Nếu IDE cũng đang biên dịch vào `target`, có thể thêm `-Dschedule.build.directory=<thư-mục-tạm-riêng>` để Maven không dùng chung class files với IDE. Không đặt `build.directory` vì đó là tên biểu thức model của Maven.

Bộ này dùng MySQL container riêng, bật Flyway/schema validation và kiểm tra nâng cấp cột tài khoản/ràng buộc phòng trên dữ liệu cũ. Đã chạy thành công trên MySQL 8.0.40 qua Docker Engine 29.8.0 ngày 26/10/2026. Database dev/prod không được dùng hoặc nâng cấp trong lượt kiểm thử; container được Testcontainers dọn sau khi chạy.

Frontend: `npm.cmd test` và `npm.cmd run build`.

Chưa commit/push. `application-dev.yml` giữ nguyên thay đổi cục bộ trước lượt sửa.
