# Chặng 4 — Bản lịch nháp theo khoa

Cập nhật 01/11/2026: GV mới có [tài khoản tự tạo theo MGV](./tai-khoan-giang-vien-tu-dong.md) khi lưu hồ sơ. GV cũ chưa liên kết vẫn xem trước bằng danh tính nháp; xác nhận sẽ tạo/nâng tài khoản, giữ mã tra cứu và các buổi đã có.

Ngày cập nhật: 31/10/2026. Chặng này bổ sung bản lịch lưu trên server, lịch sử phiên bản và xác nhận theo phạm vi khoa. Bảng lịch tuần toàn khoa, bổ sung thỉnh giảng vào lịch đã xếp và sửa từng buổi thuộc các chặng tiếp theo.

## Cách sử dụng

1. Đăng nhập ADMIN, mở **Bản lịch theo khoa** trong thanh điều hướng.
2. Chọn **Tạo bản lịch**, chọn trường/khoa đang hoạt động, nhập tên, học kỳ/đợt và ngày Thứ Hai của tuần 1. Mỗi trường–khoa–học kỳ–mốc tuần có một bản lịch; muốn sửa thì mở bản lịch đó.
3. Chọn giảng viên của khoa, phòng dùng chung trong trường, bảng giờ và trọng số cho lần xếp. Nhập kế hoạch trực tiếp hoặc tải một file kế hoạch Excel theo mẫu ở chặng 3.
4. Chọn **Xếp và lưu bản nháp mới**. Server lưu nhu cầu, kết quả, buổi học và nguồn dữ liệu của một phiên bản mới. Bản nháp thiếu tiết vẫn được lưu để xem cảnh báo. Bước này chưa tạo hồ sơ lịch cho GV chưa liên kết và chưa thay lịch cá nhân.
5. Xem buổi học trong **Phiên bản đã lưu**, chuyển trang khi có trên 25 buổi. Chọn phiên bản cũ để đối chiếu hoặc xuất Excel. Mở lại trang và chọn bản lịch để tải lại bản nháp, lựa chọn nguồn và kế hoạch đã lưu.
6. Chọn **Kiểm tra bản nháp đã lưu** để xem xung đột theo dữ liệu hiện hành. Khi đã xếp đủ, chọn **Xác nhận bản nháp**. Nếu sửa đầu vào trên màn hình, phải xếp và lưu phiên bản mới trước khi xác nhận.

Kết quả kiểm tra chỉ đúng tại thời điểm kiểm tra. Khi xác nhận, server giữ khóa ghi chung rồi kiểm tra lại giảng viên, phòng, phân công, khối lượng, ngày/giờ và các lịch đang áp dụng. Khoa khác xác nhận trước có thể làm bản nháp hiện tại trùng phòng; khi đó HTTP 409 trả danh sách xung đột và không ghi lịch dở.

## Trạng thái và phiên bản

- **Bản lịch** giữ phạm vi trường/khoa/học kỳ/mốc tuần cùng revision, phiên bản nháp gần nhất và phiên bản đang áp dụng.
- **Phiên bản** giữ nguyên đầu vào, trọng số, nguồn ứng viên, kết quả và buổi học. Tạo nháp mới không sửa nội dung phiên bản cũ. Khi xác nhận, bổ sung thời điểm/người xác nhận và ánh xạ mã lưu lịch thực tế.
- Bản lịch đã xác nhận có thể tạo phiên bản nháp mới. Lịch cá nhân vẫn dùng phiên bản đã xác nhận trước đó cho đến lần xác nhận tiếp theo thành công.
- Chỉ phiên bản nháp mới nhất được xác nhận. Phiên bản lịch sử chỉ được đọc/xuất trong chặng này. Chưa có thao tác khôi phục hoặc xóa bản lịch.
- Excel có sheet `BanLich` ghi tên, khoa, phiên bản và trạng thái; tên file phân biệt phiên bản đang áp dụng với bản nháp/lịch sử. Mã `DRAFT_…` của GV chưa có hồ sơ chỉ biểu diễn danh tính trong nháp; mã tra cứu lịch ổn định được tạo khi xác nhận.

## Phạm vi ghi lịch

[SchedulingPlanService](../server/src/main/java/com/service/SchedulingPlanService.java) xác nhận đúng các buổi đã lưu, không chạy lại thuật toán rồi âm thầm thay phương án.

[ScheduleService](../server/src/main/java/com/service/ScheduleService.java) thay riêng các dòng lịch cá nhân gắn với ID bản lịch này. Dòng của bản lịch khác và lịch nhập cá nhân giữ nguyên ID và thời gian. Mỗi dòng được chiếu gắn với một buổi gốc để đối chiếu.

- Xác nhận, liên kết/tạo hồ sơ lịch GV, cập nhật lịch cá nhân và con trỏ phiên bản nằm trong cùng một giao dịch. Lỗi ở cuối giao dịch rollback tất cả.
- Khóa ghi SQL dùng chung với luồng cũ và cập nhật danh mục để tránh hai khoa cùng chiếm một phòng khi xác nhận đồng thời. Revision chặn ghi đè bản nháp bởi màn hình cũ; phiên bản cạnh tranh bị rollback khi vi phạm revision/unique.
- Luồng xếp lại toàn trường cũ bị chặn sau khi trường có bản lịch khoa đã xác nhận. API nhập/xóa lịch cá nhân cũng chặn thay toàn bộ trường nếu phần lịch đó chứa buổi thuộc bản lịch khoa. Việc chỉnh phần này đi qua bản nháp và xác nhận.
- Mốc tuần của GV vẫn là mốc dùng chung theo kiến trúc lịch cá nhân hiện tại. Nếu còn lịch ngoài phạm vi có mốc tuần khác, hoặc bảng giờ cùng trường khác với lịch được giữ lại, phải đối chiếu trước; server không tự đổi mốc hay bảng giờ của lịch còn giữ.
- Danh tính liên kết phải tường minh để kiểm tra cùng người giữa nhiều trường. Những hồ sơ chưa được liên kết không thể được suy là cùng người chỉ vì giống họ tên.

## API ADMIN

Prefix: `/api/admin/scheduling-plans`. Tất cả API yêu cầu Bearer token ADMIN.

| Method / đường dẫn | Nội dung |
|---|---|
| `GET /?schoolCode=...` | Danh sách bản lịch, tùy chọn lọc trường |
| `POST /` | Tạo bản lịch; trả HTTP 201 với chi tiết |
| `GET /{id}` | Chi tiết, nháp gần nhất và danh sách phiên bản |
| `GET /{id}/options` | Nguồn GV/phân công đúng khoa, phòng dùng chung và bảng giờ; snapshot gắn với khoa |
| `POST /{id}/generate-preview` | Multipart: `revision`, `planFile`, `configuration` JSON; tạo phiên bản nháp |
| `POST /{id}/validate` | JSON `{"revision": n}`; trả mảng xung đột, rỗng khi hợp lệ |
| `POST /{id}/confirm` | JSON `{"revision": n}`; kiểm tra lại và xác nhận nguyên khối, trả chi tiết mới |
| `GET /{id}/versions/{versionId}` | Đọc phiên bản trong đúng bản lịch |
| `GET /{id}/versions/{versionId}/entries?page=0` | Buổi học có phân trang 25 dòng, sắp theo ngày/giờ/ID |

Tạo bản lịch:

```json
{
  "schoolCode": "PTIT",
  "departmentId": 1,
  "name": "Kế hoạch khoa CNTT học kỳ 1",
  "semester": "HK1-2026",
  "semesterStartDate": "2026-08-10"
}
```

Cấu hình nháp dùng hợp đồng lựa chọn danh mục ở chặng 3, nhưng `replaceSchoolSchedule` bắt buộc `false`. Nguồn khoa được lấy qua API của bản lịch; không dùng snapshot của luồng toàn trường. Không nhận các dòng buổi học do client tự chế để xác nhận.

Xung đột nghiệp vụ khi xác nhận trả HTTP 409:

```json
{
  "status": "ERROR",
  "message": "Bản nháp chưa thể xác nhận. Kiểm tra thiếu tiết, thay đổi dữ liệu và xung đột lịch.",
  "conflicts": [{
    "type": "ROOM_CONFLICT",
    "severity": "BLOCKING",
    "entryIds": [123],
    "date": "2026-08-10",
    "startTime": "07:00:00",
    "endTime": "09:30:00",
    "resource": "GV01 / 101",
    "message": "Phòng đã được dùng bởi lịch hiện hành hoặc buổi khác trong nháp."
  }]
}
```

Các loại khác gồm `WORKLOAD`, `LECTURER`, `IDENTITY`, `ASSIGNMENT`, `ROOM_CONFIGURATION`, `TIME_CONFIGURATION`, `TEACHER_CONFLICT`, `CLASS_CONFLICT`, `SESSION_DAY`, `SEMESTER_ANCHOR`, `PERIOD_CONFIGURATION`, `EXISTING_DATA`, `CONFIGURATION`. Báo cáo giới hạn khoảng 200 mục để xử lý từng đợt. Xung đột revision/unique cũng trả 409 nhưng không có danh sách buổi; cần mở lại bản lịch. API kiểm tra và xác nhận luôn dùng bản nháp mới nhất của revision được gửi.

## Database và triển khai

[Migration V6](../server/src/main/resources/db/migration/V6__department_schedule_plans.sql) thêm bốn bảng `scheduling_plans`, `scheduling_plan_versions`, `scheduling_demands`, `scheduling_entries`, cùng hai khóa tham chiếu nullable trên `teaching_courses`. Dữ liệu lịch cũ giữ nguyên và hai cột mới để null. Không chuyển lịch cũ thành bản lịch khoa một cách tự động.

Mã hiện tại chưa chạy migration vào database phát triển hoặc production. Khi triển khai qua quy trình hiện có, cần sao lưu database và để Flyway nâng schema trước khi dùng trang mới.

Giới hạn: engine tối đa 5.000 lớp/nhóm sau tách, 100.000 tiết/lần; bản nháp thêm giới hạn 50.000 buổi và 10.000 dòng lịch đã gộp. Đây là giới hạn bảo vệ, chưa phải kết quả đo hiệu năng với dữ liệu toàn trường thực tế.

## Kết quả kiểm thử

- Backend: 202 ca riêng biệt đạt trong trạng thái cuối. Suite đầy đủ ban đầu 198 ca, sau đó sửa assertion nhận ngoại lệ cạnh tranh khóa MySQL và thêm 4 ca; lần chạy tập trung cuối 65 ca đạt. Các báo cáo còn lại từ suite đầy đủ đều đạt, không có ca bỏ qua.
- [Hợp đồng bản lịch](../server/src/test/java/com/service/SchedulingPlanContract.java) chạy trên H2 và MySQL: nháp không ghi lịch cá nhân, mở lại từ DB, xác nhận, lịch sử, giữ khoa khác, cập nhật phòng, thiếu khối lượng, phạm vi khoa, revision cũ, chặn API ghi cũ, phân trang và lịch ngoài trường mới phát sinh.
- [Kiểm thử MySQL](../server/src/test/java/com/service/MySqlSchedulingPlanTest.java) xác minh hai khoa xác nhận cùng phòng và hai người ghi nháp cùng revision. [Kiểm thử rollback](../server/src/test/java/com/service/SchedulingPlanAtomicityTest.java) gây lỗi sau khi ghi phần chiếu lịch để kiểm tra rollback cả giao dịch.
- Frontend: 41 unit test, 80 ca Playwright desktop/mobile; production build thành công. [Kiểm thử trang mới](../frontend/tests/scheduling-plans.spec.js) kiểm tra tạo nháp một file, tải lại, lịch sử, xác nhận/xung đột, thay đổi đầu vào, phân trang, Excel và 409.
- Đã kiểm tra migration/Hibernate trên MySQL container dùng riêng cho test. Browser dùng API giả lập; chưa chạy migration hay nhập dữ liệu vào database của người dùng.

Chặng 5 đã bổ sung bảng tuần, phân công và xuất theo bộ lọc ngay trong trang bản lịch. Xem [hướng dẫn bảng toàn khoa](./bang-toan-khoa-chang-5.md).
