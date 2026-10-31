# Chặng 6 — Thỉnh giảng sau khi xếp lịch

Ngày cập nhật: 31/10/2026. Mở **Bản lịch theo khoa**, chọn phiên bản nháp mới nhất rồi chọn **Bổ sung thỉnh giảng**. Có thể bắt đầu từ bản lịch đang áp dụng; thay đổi được giữ trong phiên bản nháp mới cho đến khi xác nhận.

## Luồng sử dụng

1. Chọn thỉnh giảng đang hoạt động và thuộc khoa. Nếu chưa có hồ sơ, chọn **Tạo hồ sơ thỉnh giảng và phân công**, nhập mã, họ tên và chọn các môn. Hồ sơ, tài khoản mới và các phân công được lưu cùng giao dịch. Có thể liên kết tài khoản đã có; bỏ trống sẽ [tự tạo tài khoản MGV](./tai-khoan-giang-vien-tu-dong.md), mật khẩu ban đầu là họ tên không dấu viết liền, chữ thường + MGV. Chưa thay lịch cho tới khi lưu/xác nhận bản lịch.
2. Chọn **Nhận/chuyển lớp đã có** hoặc **Bổ sung lớp/nhóm mới**. Nhận lại lớp chuyển toàn bộ buổi của từng đơn vị LT/TH được chọn, không tạo thêm nhu cầu. Lớp mới phải khai báo môn, khóa, tên lớp/nhóm, LT/TH, sĩ số, tín chỉ, khoảng ngày và tổng tiết riêng. Nhóm TH có thể chọn lớp LT cha cùng môn/khóa để kiểm tra lịch sinh viên.
3. Nhận lớp có thể **Giữ giờ và phòng hiện tại** hoặc **Thay bằng lịch dạy cố định**. Lịch cố định thay toàn bộ buổi trong các lớp/nhóm đã chọn. Nhập một ngày, hoặc chọn thứ và khoảng ngày để lặp hằng tuần. Lớp/nhóm mới dùng lịch cố định.
4. Khai báo lịch bận ngoài trường bằng ngày và giờ thực, hỗ trợ chính xác tới giây. Khoảng nhiều ngày phải chọn thứ lặp. Danh sách nhập thay lịch bận của GV đã chọn trong phiên bản nháp này; lịch bận của GV khác được giữ. Muốn đổi riêng lịch bận của GV đang nhận lớp, chọn lại lớp của người đó và giữ giờ.
5. Chọn **Xem trước thay đổi thỉnh giảng**. Xem bảng GV/khối lượng trước–sau, các buổi trước/sau, số buổi ngoài phạm vi giữ nguyên và báo cáo xung đột. Sửa bất cứ đầu vào nào sẽ bỏ kết quả xem trước; phải xem trước lại trước khi lưu.
6. Chọn **Lưu đề xuất vào bản nháp** để tạo phiên bản mới trên server. Có thể giữ nháp còn thiếu tiết hoặc trùng lịch để tiếp tục sửa. Việc này chưa cập nhật lịch cá nhân hay lịch bận dùng chung.
7. Xem bảng toàn khoa và dùng **Kiểm tra bản nháp đã lưu** / **Xác nhận bản nháp**. Chỉ bản nháp đủ tiết, đúng cấu hình và không có xung đột chặn được xác nhận. Khi đó lịch cá nhân và lịch bận của phiên bản mới được áp dụng cùng một giao dịch.

Nếu gặp 409, đầu vào trên form được giữ lại, nút lưu đề xuất cũ bị bỏ. Tải lại nguồn hoặc mở lại bản lịch, đối chiếu rồi xem trước lại. Không tự gửi lại lệnh lưu.

## Các quy tắc quan trọng

- GV phải có phân công môn–khóa đang hoạt động và trọng số dương cho đúng LT hoặc TH. Hồ sơ của khoa khác, cơ hữu, ngừng hoạt động hoặc thiếu phân công không được dùng làm thỉnh giảng trong đề xuất.
- Chuyển lớp không tăng tổng tiết. Giờ đã xếp được thay theo lớp/nhóm, không cộng lịch cố định vào lịch cũ. Tổng tiết cố định vượt nhu cầu trả lỗi; thiếu tiết có thể lưu nháp nhưng không xác nhận.
- Thêm lớp mới là thao tác riêng, tăng nhu cầu đúng tổng tiết đã khai báo. Không cho tạo trùng môn–khóa–tên lớp/nhóm–LT/TH đã có. Cùng môn/khóa phải giữ tên và tín chỉ của kế hoạch. Nhóm TH cùng lớp cha được kiểm tra liên quan với LT và các tổ khác.
- Ngày cố định phải nằm trong 100 tuần của bản lịch, dùng đúng tiết và phòng của trường. Phòng sai loại/sức chứa, ngày ngoài khoảng học, trùng GV/phòng/lớp và vi phạm LT/TH được đưa vào báo cáo chặn xác nhận.
- Lịch bận chỉ chặn thời gian của GV. Nó không chiếm phòng/lớp và không bị xem là một buổi LT hoặc TH. Hai khoảng tiếp giáp giờ không tính là trùng.
- Lịch bận nháp chỉ dùng kiểm tra trong chính bản nháp. Sau xác nhận, nó được dùng khi kiểm tra và xếp lịch ở các bản lịch/trường khác có cùng danh tính GV. Khi xác nhận phiên bản tiếp theo đã bỏ lịch bận, lịch bận cũ ngừng áp dụng; dữ liệu lịch sử vẫn giữ.
- Một khoảng bận mới giao với buổi của bản lịch khác đang áp dụng cũng bị chặn để tránh làm lịch đã công bố mất hợp lệ. Lịch bận có thể mô tả lại lịch cá nhân bên ngoài đã nhập; lịch cá nhân đó vẫn trực tiếp chặn các buổi của bản nháp.
- Giảng viên có tài khoản hoặc hồ sơ lịch đã liên kết dùng chung danh tính khi kiểm tra nhiều trường. Hồ sơ chưa liên kết không bị ghép theo họ tên. Liên kết danh tính không được đổi tùy tiện khi còn lịch bận đã áp dụng, kể cả khi người đó đã chuyển hết lớp sang GV khác.
- Các buổi ngoài những lớp/nhóm được chọn không đổi ngày, giờ, phòng hay người dạy. Bản lịch đang áp dụng và các khoa khác được giữ đến khi lần xác nhận mới thành công.
- Bản nháp có thay đổi thỉnh giảng/lịch cố định không được chạy lại qua nút xếp tự động cũ để tránh làm mất các ràng buộc đã nhập. Dùng lại phần thỉnh giảng để chuyển lớp, thay giờ cố định hoặc đổi lịch bận. Chỉnh từng buổi, khóa buổi, xếp lại tự động cục bộ và khôi phục dùng phần **Điều chỉnh lịch**, xem [hướng dẫn chặng 7](./dieu-chinh-cuc-bo-va-khoi-phuc-chang-7.md). Buổi đã khóa cần mở khóa trước khi đổi giờ hoặc đổi người.

## API ADMIN

Prefix: `/api/admin/scheduling-plans/{id}`.

| API | Nội dung |
|---|---|
| `GET /visiting-options` | Revision và ID nháp cơ sở, snapshot danh mục, thỉnh giảng đủ điều kiện, phân công, phòng, bảng giờ, lớp/nhóm và lịch bận đã lưu |
| `POST /visiting-lecturers` | Lưu hồ sơ VISITING cùng phân công trong một giao dịch; trả 201; chưa tạo tài khoản hay đổi lịch |
| `POST /visiting-lecturers/preview` | Đọc thuần, tạo so sánh và báo cáo xung đột; chưa ghi phiên bản, danh tính lịch hoặc lịch cá nhân |
| `POST /visiting-lecturers/commit` | Kiểm tra lại đề xuất rồi tạo phiên bản nháp mới; chưa công bố |

Tạo hồ sơ:

```json
{
  "revision": 2,
  "lecturerCode": "TG01",
  "fullName": "Nguyễn Văn A",
  "accountTeacherId": null,
  "assignments": [{"courseCode": "NET", "courseName": "Networking", "cohort": "2024", "theoryWeight": 1, "practiceWeight": 1}]
}
```

Đề xuất nhận lớp và giữ giờ:

```json
{
  "revision": 2,
  "baseVersionId": 10,
  "snapshot": "<snapshot từ visiting-options>",
  "lecturerId": 7,
  "action": "RECEIVE",
  "timing": "KEEP",
  "classIds": ["<classId từ visiting-options>"],
  "newClass": null,
  "sessions": [],
  "busySlots": [{
    "startDate": "2026-08-10", "endDate": "2026-11-23", "dayOfWeek": 2,
    "startTime": "07:05:30", "endTime": "09:00:00", "reason": "Lịch bận ngoài trường"
  }]
}
```

`dayOfWeek`: 2 = Thứ Hai, …, 8 = Chủ Nhật; null chỉ một ngày và hai ngày đầu/cuối phải trùng nhau. Khoảng không chứa thứ đã chọn trả lỗi, không âm thầm xóa lịch bận.

- `action=RECEIVE`: 1–100 ID lớp/nhóm hiện hành; `newClass=null`.
- `action=NEW`: `classIds=[]`, `timing=FIXED`; `newClass` gồm `courseCode`, `courseName`, `cohort`, `classGroup`, `sessionType`, `studentCount`, `credits`, `studyStartDate`, `studyEndDate`, `totalPeriods`, `parentClassId` tùy chọn cho TH.
- `timing=FIXED`: gửi các buổi ngày cụ thể trong `sessions`: `classId`, `date`, `roomId`, `startPeriod`, `numPeriods`. Với lớp mới dùng `classId="$NEW"`; server tạo ID ổn định theo đề xuất. UI mở rộng khoảng lặp thành ngày cụ thể trước khi gửi.
- `busySlots` là các khoảng khai báo, bắt buộc là mảng, có thể rỗng để bỏ lịch bận của người đã chọn trong nháp. UI gộp lại các ngày bận liên tiếp đúng tuần khi mở lại, giữ nguyên các tuần nghỉ/gap.

Preview trả `previewToken`, revision/ID cơ sở, thay đổi khối lượng, buổi trước/sau, lịch bận ngày cụ thể, xung đột và cờ `complete`/`canConfirm`. Lưu nháp gửi:

```json
{"input": {"...": "nguyên input đã xem trước"}, "previewToken": "<token trả về>"}
```

Server không nhận buổi/khối lượng do client tự sửa từ response. Khi commit, giữ khóa SQL chung, dựng lại đề xuất từ dữ liệu server và so token với revision, snapshot, người thao tác, kết quả và xung đột. Nguồn đổi hoặc lịch mới gây khác biệt trả 409. API xác nhận chặng 4 tiếp tục kiểm tra lại toàn bộ lịch trước khi công bố.

Xung đột mới: `BUSY_CONFLICT`, `BUSY_PUBLISHED_CONFLICT`, `BUSY_CONFIGURATION`; các loại GV/phòng/lớp/khối lượng/cấu hình cũ vẫn áp dụng. Báo cáo tối đa khoảng 200 mục; cần xử lý và kiểm tra lại từng đợt.

## Database và giới hạn

[Migration V7](../server/src/main/resources/db/migration/V7__visiting_lecturer_busy_slots.sql) thêm metadata thay đổi nullable trên phiên bản và bảng `lecturer_busy_slots`. Các buổi bận gắn phiên bản/GV, có ngày/giờ/lý do và danh tính lưu lịch được gắn khi xác nhận. FK giữ quan hệ và CHECK chặn khoảng giờ sai. Chỉ busy của phiên bản đang được con trỏ xác nhận trỏ tới được áp dụng chung.

- Tối đa 100 phân công khi tạo hồ sơ, 100 lớp/nhóm và 2.000 buổi trong phạm vi một đề xuất.
- Tối đa 100 khoảng bận và 2.000 ngày bận sau mở rộng cho một đề xuất; tối đa 10.000 ngày bận trong một phiên bản.
- Các giới hạn 5.000 lớp/nhóm, 100.000 tiết, 50.000 buổi và 10.000 dòng lịch gộp của bản lịch tiếp tục áp dụng.
- Phiên bản cũ thiếu metadata sĩ số/quan hệ nhóm được đối chiếu từ buổi đã lưu hoặc nhu cầu/nhóm gốc, không đổi nội dung lịch sử.

Mã được kiểm tra bằng H2 và MySQL container riêng. Chưa áp dụng migration hoặc ghi dữ liệu vào database phát triển/production đang dùng; cấu hình phát triển có sẵn được giữ nguyên.

## Kiểm chứng

- Tổng trạng thái backend cuối: **246 ca riêng biệt đạt**. Suite đầy đủ 242 ca cần sửa một assertion về loại ngoại lệ unique constraint; sau đó thêm hai ca cho lịch đã công bố và nhóm TH phiên bản cũ, chạy lại tập trung 25 ca đều đạt. Không có ca bỏ qua trong kết quả cuối.
- [Hợp đồng thỉnh giảng](../server/src/test/java/com/service/VisitingLecturerContract.java) chạy 11 ca trên H2 và cùng 11 ca trên [MySQL](../server/src/test/java/com/service/MySqlVisitingLecturerTest.java): chuyển lớp không tăng tiết, giữ lịch đang áp dụng, lớp mới, giờ cố định, lịch bận/xung đột, trường khác, bỏ busy sau xác nhận phiên bản mới, revision/token/danh mục cũ, API ADMIN và dữ liệu nhóm TH cũ.
- [Ba ca rollback](../server/src/test/java/com/service/VisitingLecturerAtomicityTest.java) dùng giao dịch thực: lỗi ở dòng phân công thứ hai, lỗi sau khi đã ghi phiên bản/buổi/busy, và lỗi sau khi cập nhật lịch cá nhân/busy/danh tính nhưng trước kết thúc xác nhận.
- Frontend: **52 unit test và 100 ca Playwright desktop/mobile đạt**; production build thành công. Các ca mới kiểm tra tạo hồ sơ, nhận lớp, lịch cố định lặp, cảnh báo lịch bận, lưu nháp, đổi input và 409 không tự gửi lại. Đã rà ảnh desktop/mobile và kiểm tra không tràn ngang trang.
