# Xếp lịch bằng danh mục đã lưu — chặng 3

Cập nhật 01/11/2026: quy tắc tài khoản đã chuyển sang [tự tạo tài khoản MGV](./tai-khoan-giang-vien-tu-dong.md). Nội dung về hồ sơ lịch không đăng nhập bên dưới mô tả dữ liệu cũ; khi nâng tài khoản vẫn giữ đúng danh tính và lịch đã lưu.

## 1. Cách sử dụng

ADMIN mở **Xếp lịch toàn trường**. Mặc định là **Danh mục đã lưu**; nút **Ba file Excel** giữ luồng cũ.

1. Chọn **Trường từ danh mục**. Nếu chưa có dữ liệu, vào **Quản lý danh mục** để thêm/nhập giảng viên, phân công và phòng theo [hướng dẫn chặng 2](./danh-muc-huong-dan-chang-2.md).
2. Chọn ngày Thứ Hai của tuần 1 và **Bảng giờ tiết**. Có thể mở phần xem giờ để đối chiếu.
3. Mở **Chọn giảng viên**, **Chọn phòng** nếu muốn dùng một tập con. Mặc định chọn mọi GV đủ điều kiện và mọi phòng đang hoạt động.
4. Mở **Trọng số LT/TH của lần xếp** nếu cần điều chỉnh phân công. Mỗi dòng là một bộ GV–môn–khóa sinh viên; trọng số mặc định vẫn được hiển thị để đối chiếu. Thay đổi chỉ áp dụng cho lần chạy, không sửa danh mục gốc.
5. Tải **Kế hoạch mở lớp từ Excel** hoặc chọn **Nhập kế hoạch trực tiếp**. Chỉ cần một file kế hoạch; không cần file giảng viên hoặc phòng.
6. Xác nhận đã đưa đủ kế hoạch toàn trường rồi chọn **Xếp lịch bằng danh mục đã lưu**.
7. Kiểm tra trạng thái **Đã lưu lịch** hoặc **Bản xem trước — chưa lưu**. Chỉ lịch đã lưu đầy đủ mới mở được **Xem TKB**. Có thể xuất Excel của cả kết quả đã lưu và bản xem trước, với tên file phân biệt hai trạng thái.

Chạy lại vẫn giữ file kế hoạch/lựa chọn hiện tại. Khi lưu thành công, danh mục được tải lại để lấy phiên bản mới; không phải nạp lại file GV hoặc phòng.

## 2. Phạm vi cập nhật lịch

Chặng 3 giữ phạm vi ghi của bộ xếp lịch hiện có: thay phần lịch tự động **của toàn trường**, kể cả giảng viên rút khỏi kế hoạch. Việc chọn một tập GV/phòng là chọn tài nguyên cho kế hoạch, không phải chỉ sửa lịch của các GV đó.

Các dòng lịch nhập riêng và lịch của trường khác được giữ lại và dùng để kiểm tra trùng. Nếu không xếp đủ tiết, không ghi lịch mới, không xóa lịch cũ và không giữ lại các hồ sơ lịch tạm được tạo trong lần chạy thất bại.

Không dùng một kế hoạch thiếu các khoa khác để cập nhật riêng một khoa ở giai đoạn này. Bản lịch nháp và phạm vi ghi theo khoa thuộc chặng 4.

## 3. Điều kiện sử dụng danh mục

- Trường, giảng viên, khoa của giảng viên và phân công cần đang hoạt động. Hồ sơ chưa phân khoa vẫn được chọn.
- Phòng phải đang hoạt động và thuộc đúng trường. Có thể cùng mã phòng ở hai trường, nhưng không được chọn phòng của trường khác cho một lần chạy.
- Cần có phân công đúng **mã môn + khóa sinh viên** cho các môn trong kế hoạch. Tên môn/khối lượng/ngày học lấy từ kế hoạch.
- Nếu môn có LT/TH, tập ứng viên được chọn cần có ít nhất một trọng số lớn hơn 0 cho phần tương ứng. Trọng số trong một lần chạy cho phép 0/0 để loại GV khỏi riêng môn đó.
- Thiếu phân công hoặc loại LT/TH sẽ trả lỗi nêu rõ dòng, môn và khóa. Không tự lấy giảng viên của khóa khác và không tự quay sang dữ liệu file cũ.
- Bảng giờ lấy từ lịch đã lưu của trường. Nếu có nhiều cấu hình khác nhau, cần chọn rõ bảng giờ; nếu chưa có cấu hình nào, đưa ra khung 12 tiết hiện có của hệ thống để kiểm tra. Không âm thầm thay giờ đã có.
- Các kiểm tra về số tiết, ngày học, sức chứa/loại phòng, lớp/nhóm, lịch bận và quy tắc LT/TH của thuật toán cũ tiếp tục áp dụng.

## 4. Mã giảng viên, tài khoản và hồ sơ lịch

Ba loại mã được phân biệt:

| Mã | Ý nghĩa |
|---|---|
| Mã GV trong trường | Mã nghiệp vụ ở danh mục, ví dụ `STAFF01` |
| Mã tài khoản | Mã người dùng đã đăng ký, ví dụ `GVAB12`; dùng khi liên kết để giảng viên xem đúng lịch của mình |
| Mã tra cứu lịch | Danh tính backend dùng để lưu lịch cá nhân; bằng mã tài khoản khi có liên kết, hoặc mã hồ sơ lịch độc lập khi chưa có tài khoản |

- Có tài khoản liên kết: lưu lịch vào đúng tài khoản, không lấy mã GV trong trường để đoán một tài khoản khác.
- Chưa có tài khoản: tạo một hồ sơ lịch độc lập có mã riêng ổn định, không username/password và không cấp quyền đăng nhập. Mã GV giống một tài khoản khác không có nghĩa là cùng người.
- Khi đã có lịch, không tự đổi/gộp/bỏ liên kết sang một tài khoản khác. Cần đối chiếu lịch trước; nếu không, có thể mất dấu lịch bận hoặc gắn lịch nhầm người.
- Nếu phát hiện mã GV trùng mã một hồ sơ có lịch cũ trong trường nhưng chưa được liên kết rõ, giảng viên đó chưa được chọn cho tới khi đối chiếu.

### Nối hồ sơ lịch cũ chưa có tài khoản

**Đối chiếu dữ liệu cũ → Chuẩn bị nhập GV** hiện bổ sung cột tùy chọn **Mã hồ sơ lịch cũ** cho những hồ sơ chưa có username. Sau khi kiểm tra và xác nhận nhập, danh mục giữ đúng danh tính cũ, nên lịch bận trước đó vẫn được tính.

Cũng có thể nhập mã này trong biểu mẫu thêm GV khi đã xác định chính xác hồ sơ. Server kiểm tra hồ sơ có dữ liệu thuộc trường và là hồ sơ giảng viên. Với hồ sơ đã đăng ký tài khoản, phải liên kết qua mã tài khoản; không dùng cột lịch cũ để bỏ qua bước này.

Một hồ sơ lịch chỉ gắn một GV trong cùng trường. Có thể gắn cùng một người ở nhiều trường sau khi xác định rõ danh tính, để kiểm tra lịch bận xuyên trường.

## 5. Dữ liệu thay đổi trong lúc chuẩn bị

Nguồn danh mục có snapshot gắn với phiên bản trường/khoa/GV/phân công/phòng và bảng giờ. Nếu dữ liệu cơ sở thay đổi trước lúc chạy, server trả 409, không tự dùng bản cũ và không tự gửi lại yêu cầu.

Chọn **Tải lại dữ liệu xếp lịch**, kiểm tra lại các lựa chọn và trọng số, rồi chủ động xếp lại. File kế hoạch và các chỉnh sửa trọng số riêng vẫn được giữ nếu bản ghi tương ứng còn tồn tại; GV/phòng không còn hợp lệ sẽ được bỏ khỏi tập chọn.

Server lấy lại danh mục và lịch bận dưới khóa ghi trước khi tính/ghi. Snapshot không thay thế việc kiểm tra lịch bận hiện hành.

## 6. Xuất Excel và xem lịch

- Bảng kết quả hiển thị **Mã GV trong trường**, kèm mã tài khoản nếu đã liên kết.
- **Xem TKB** sử dụng đúng mã tra cứu lịch; giảng viên có tài khoản vẫn đọc lịch của mình theo quyền sở hữu hiện có.
- Các sheet `MonHoc`, `TietHoc`, phân công và khối lượng giữ hợp đồng cũ. Cột `Mã GV` trong sheet lịch tiếp tục chứa mã tra cứu để tương thích.
- Kết quả dùng danh mục có thêm sheet `DanhTinhGiangVien`: mã GV trong trường, mã tài khoản và mã tra cứu. Hồ sơ không có tài khoản để trống cột tài khoản.

## 7. API và cấu trúc dữ liệu

Chỉ ADMIN được truy cập:

| API | Nội dung |
|---|---|
| `GET /api/admin/institutions/{schoolCode}/scheduling-options` | Trường, snapshot, GV kèm điều kiện chọn, phân công đang hoạt động, phòng khả dụng và các bảng giờ |
| `POST /api/schedule/auto-generate-catalog` | Multipart chỉ gồm `planFile`, `schoolId`, `semesterStartDate`, `configuration` JSON |

Ví dụ `configuration`:

```json
{
  "snapshot": "<SHA-256 vừa tải>",
  "lecturerIds": [1, 2],
  "roomIds": [10, 11],
  "weights": [{ "assignmentId": 5, "theoryWeight": 9, "practiceWeight": 0 }],
  "periodChoiceId": "<id bảng giờ từ scheduling-options>",
  "replaceSchoolSchedule": true
}
```

Danh sách IDs phải không rỗng, không lặp và thuộc đúng nguồn đang hoạt động của trường. Override chỉ nhận ID phân công thuộc GV đã chọn, không nhận các môn/ID/giảng viên do client tự thêm. Tên trường và tên GV lấy từ database.

Response giữ `AutoScheduleResponse` hiện có và thêm `catalogContext`: nguồn snapshot, tập phòng, bảng giờ và ánh xạ mã GV–tài khoản–hồ sơ lịch. Snapshot đầu vào môn học đã lưu cũng giữ danh tính danh mục và trọng số thực tế của lần chạy.

## 8. Di trú và tương thích

- [V5](../server/src/main/resources/db/migration/V5__catalog_schedule_identity.sql) bổ sung `institution_lecturers.schedule_teacher_id`, FK tới hồ sơ lịch và unique theo trường–hồ sơ lịch. Không sửa migration V1–V4, không tạo login hay đổi mật khẩu/quyền của tài khoản cũ.
- Mẫu GV và DTO thêm trường tùy chọn `existingScheduleTeacherId` để nối dữ liệu đã đối chiếu; các payload cũ vẫn hợp lệ.
- Luồng ba file và ba bảng nhập tay vẫn dùng endpoint cũ. Bộ đọc kế hoạch đã tách khỏi nguồn ứng viên, nhưng kiểm tra file/khối lượng/ngày của luồng cũ được giữ.
- Đã sửa việc cập nhật quan hệ hai chiều giảng viên–trường khi lưu lịch mới để đọc lại được lịch/bảng giờ trong cùng giao dịch.
- Chưa chạy migration hay nhập danh mục vào database phát triển/production trong quá trình làm mã. Khi triển khai backend cần nâng schema qua V5 theo cấu hình Flyway của môi trường.

## 9. Kiểm chứng

- 175 ca backend đã chạy đạt: hồi quy toàn bộ với MySQL và ca bổ sung lưu/rollback nguồn danh mục trên MySQL tạm. API multipart được kiểm tra cả thiếu file, sai vai trò và lưu thành công vào đúng tài khoản.
- 39 unit test frontend và production build đạt. 70 ca trình duyệt desktop/mobile đã qua sau khi chỉnh fixture đồng hồ; kiểm thử UI dùng API giả lập, kiểm thử backend dùng database thật trong môi trường test.
- Các bộ test chính: [CatalogSchedulingTest](../server/src/test/java/com/service/CatalogSchedulingTest.java), [CatalogSchedulingRollbackTest](../server/src/test/java/com/service/CatalogSchedulingRollbackTest.java), [MySqlCatalogSchedulingTest](../server/src/test/java/com/service/MySqlCatalogSchedulingTest.java), [MySqlCatalogMigrationTest](../server/src/test/java/com/service/MySqlCatalogMigrationTest.java) và [kiểm thử trình duyệt](../frontend/tests/catalog-scheduling.spec.js).
