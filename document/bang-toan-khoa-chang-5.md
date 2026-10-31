# Chặng 5 — Lịch tuần và phân công toàn khoa

Ngày cập nhật: 31/10/2026. Trang ADMIN **Bản lịch theo khoa** có bảng toàn khoa ngay trong phiên bản đang xem. Dữ liệu lấy từ buổi học và khối lượng đã lưu trên server.

## Sử dụng

1. Mở **Bản lịch theo khoa**, chọn bản lịch và phiên bản cần xem. Thanh ngữ cảnh ghi trường, khoa, học kỳ, số phiên bản, trạng thái và khoảng ngày của tuần.
2. Chọn tuần 1–100 hoặc dùng nút tuần trước/sau/hiện tại. Bảng tuần được tính từ ngày Thứ Hai tuần 1 của chính bản lịch, không dùng tuần đang mở ở lịch cá nhân.
3. Chuyển giữa **Lịch tuần toàn khoa**, **Phân công toàn khoa** và **Danh sách buổi**. Ba tab giữ chung tuần và bộ lọc.
4. Kết hợp lọc GV, môn, khóa sinh viên, lớp/nhóm, phòng, LT/TH, loại GV và chỉ buổi có xung đột. Tìm kiếm áp dụng cho mã/tên môn, khóa, lớp và mã/tên GV. Chọn **Xóa bộ lọc toàn khoa** để giữ tuần/tab hiện tại và bỏ các điều kiện còn lại.
5. Lịch tuần có hàng theo GV hoặc lớp/nhóm; các buổi cùng ô được xếp thành danh sách, không che nhau. Chọn buổi để mở ngày, giờ thực tế, tiết, GV, loại, lớp/khóa, phòng/sĩ số và cảnh báo. Có thể dùng Enter để mở, Escape để đóng và trả focus về buổi vừa chọn.
6. **Xuất toàn khoa theo bộ lọc** tải lại dữ liệu từ server cho đúng ID phiên bản, tuần và bộ lọc. File xuất bao gồm mọi trang kết quả, không chỉ trang đang thấy.

Trên desktop, lịch có tiêu đề/cột tên cố định và cuộn trong vùng riêng. Trên mobile, lịch chuyển thành danh sách theo người/lớp và ngày. Bảng phân công/danh sách rộng cuộn ngang trong vùng riêng. Lựa chọn tuần, tab, cách nhóm và bộ lọc được lưu tùy chọn trên trình duyệt theo bản lịch/phiên bản; sau khi mở lại bản lịch, trang dữ liệu bắt đầu từ trang đầu tiên.

## Ý nghĩa số liệu

| Số liệu | Phạm vi |
|---|---|
| Buổi, tiết, GV có buổi và buổi có xung đột | Tuần đang chọn, sau bộ lọc; tính trên toàn bộ kết quả trước phân trang |
| Lớp/nhóm LT/TH | Số dòng khối lượng khớp bộ lọc; LT và từng tổ TH là các dòng riêng |
| Tiết yêu cầu / đã xếp / thiếu tổng kỳ | Toàn bộ khối lượng của những lớp/nhóm được chọn, không phải nhu cầu riêng của tuần |
| Tiết tuần / buổi tuần trong phân công | Chỉ các buổi của tuần đang xem khớp bộ lọc |

Một lớp không có buổi trong tuần vẫn có thể xuất hiện ở bảng phân công với tiết tuần bằng 0 và tổng kỳ đầy đủ. Bản nháp thiếu tiết vẫn hiện dòng nhu cầu dù chưa có buổi nào. Khi lọc phòng hoặc chỉ xung đột, bảng phân công chỉ giữ lớp/nhóm có buổi khớp điều kiện đó trong tuần; tổng kỳ của dòng vẫn ghi toàn bộ lớp/nhóm, không chỉ số tiết tại phòng được lọc.

Loại GV được lưu cùng snapshot danh tính khi tạo phiên bản mới. Đổi loại/tên GV trong danh mục không sửa thông tin lịch sử. Phiên bản cũ chưa có loại GV được đánh dấu **Chưa lưu loại GV**; không suy ngược loại hiện tại thành dữ liệu lịch sử.

## Xung đột và trạng thái

- Kiểm tra trùng được thực hiện trước khi lọc hiển thị. Lịch khoa khác hoặc lịch cá nhân đang áp dụng vẫn chặn phòng/GV dù không xuất hiện trong bảng đang lọc.
- Kiểm tra dùng ngày/giờ thực, mã trường/phòng có chuẩn hóa hoa/thường, mốc tuần riêng của từng GV và giờ tùy chỉnh có cả giây. Hai trường khác nhau có cùng mã phòng không bị coi là cùng phòng.
- Các loại hiển thị là trùng GV, phòng, lớp/nhóm và LT/TH cùng ngày. Hai phía của xung đột trong cùng phiên bản đều được đánh dấu.
- Không so phiên bản với phần chiếu lịch cá nhân của chính bản lịch đó. Bản nháp mới so với lịch ngoài phạm vi; phiên bản lịch sử cũng được đối chiếu với lịch ngoài phạm vi đang áp dụng tại thời điểm tải, không phải báo cáo trùng đã đóng băng trong quá khứ.
- Nếu dữ liệu lịch ngoài phạm vi không quy đổi được thời gian, bảng hiện cảnh báo cần đối chiếu, thay vì kết luận không trùng.
- Bảng ghi thời điểm đối chiếu. Đây là kiểm tra thời gian cho tuần đang xem; thao tác **Kiểm tra bản nháp đã lưu** / **Xác nhận bản nháp** vẫn kiểm tra toàn bộ bản nháp, gồm khối lượng và cấu hình tài nguyên.

Trạng thái phiên bản do server xác định: **Bản nháp**, **Đang áp dụng**, **Phiên bản lịch sử**. Xuất Excel đọc lại trạng thái này; nếu người khác vừa lưu/xác nhận phiên bản mới, file của phiên bản đang xem được ghi đúng trạng thái lịch sử/đang áp dụng tại thời điểm xuất.

## Excel

File có ba sheet:

- `LichToanKhoa`: một dòng cho mỗi buổi khớp tuần/bộ lọc, gồm ID buổi/lớp, ngày, giờ thực, tiết, môn/khóa, lớp, GV/loại, phòng/sĩ số và cảnh báo.
- `PhanCongToanKhoa`: một dòng cho mỗi lớp/nhóm được chọn, tách cột tổng kỳ khỏi cột tuần và số buổi trùng.
- `ThongTin`: trường/khoa/học kỳ, phiên bản/trạng thái, tuần/khoảng ngày, thời điểm đối chiếu, bộ lọc và số liệu.

Tên file chứa trường, số phiên bản, trạng thái và tuần. Nút **Xuất Excel phiên bản** của chặng 4 vẫn xuất toàn bộ phiên bản theo hợp đồng lịch cũ; nút **Xuất toàn khoa theo bộ lọc** dùng phạm vi tuần hiện tại như mô tả trên.

## API ADMIN

`GET /api/admin/scheduling-plans/{id}/versions/{versionId}/overview`

| Query | Giá trị |
|---|---|
| `week` | 1–100; mặc định 1 |
| `view` | `calendar` (mặc định), `assignments`, `sessions`, `export` |
| `groupBy` | `lecturer` (mặc định) hoặc `class` |
| `page` | Từ 0; trang theo nhóm lịch hoặc dòng tùy tab |
| `lecturerId` | ID danh mục GV; bỏ trống để xem tất cả |
| `courseCode`, `cohort`, `classId`, `roomCode` | Giá trị từ danh sách lựa chọn của phiên bản |
| `sessionType` | `THEORY` / `PRACTICE` |
| `lecturerType` | `STAFF` / `VISITING` / `UNKNOWN` |
| `conflictsOnly` | `true` / `false`, mặc định false |
| `q` | Tìm kiếm kết hợp, tối đa 200 ký tự |

Response gồm `context`, `filters`, khoảng ngày, `stats`, `options`, thông tin trang và dữ liệu tương ứng `groups` / `assignments` / `entries`, cùng `warnings` khi có dữ liệu cần đối chiếu. ID phiên bản không thuộc bản lịch trả 404; tuần/bộ lọc sai trả 400; thiếu token trả 401, tài khoản TEACHER trả 403.

Các tab đọc tối đa 10 nhóm lịch hoặc 25 dòng/trang. Một hàng lịch tối đa 200 buổi; nếu vượt, UI ghi rõ số buổi đang hiện và yêu cầu lọc hẹp hoặc dùng danh sách có phân trang. `view=export` trả toàn bộ buổi và phân công khớp bộ lọc, bỏ giới hạn trang để tạo file. Client xuất gửi `page=0`.

## Thay đổi mã và phạm vi

- [FacultyBoardService](../server/src/main/java/com/service/FacultyBoardService.java) truy vấn buổi của đúng phiên bản và một tuần, đọc snapshot khối lượng/danh tính để dựng bảng và số liệu. [Truy vấn đặt chỗ](../server/src/main/java/com/repository/BoardReservationRepository.java) chỉ đọc lịch liên quan trường hoặc danh tính GV; phần chiếu của bản lịch khác được giới hạn ngày, lịch cũ theo chuỗi tuần chỉ được quy đổi cho tuần đang xem.
- [FacultyScheduleBoard](../frontend/src/FacultyScheduleBoard.jsx) hiển thị lịch, phân công, bộ lọc và chi tiết; [bộ xuất](../frontend/src/facultyBoardWorkbook.js) nhận dữ liệu xuất mới từ server. Request cũ bị hủy khi chuyển tuần/bộ lọc; dữ liệu trả về phải khớp phiên bản và toàn bộ bộ lọc trước khi hiển thị/xuất.
- Chặng 5 không thêm migration. Loại GV được bổ sung trong JSON snapshot có khả năng đọc dữ liệu cũ. Không tự sửa, xác nhận hay di chuyển buổi khi xem/lọc/xuất.
- Bổ sung/chuyển phân công cho thỉnh giảng và điều chỉnh buổi là phạm vi các chặng 6–7. Chặng này cung cấp bảng xem và báo cáo để phục vụ các thao tác đó.

Giới hạn trang và hàng lịch là giới hạn bảo vệ, không phải cam kết hiệu năng trên dữ liệu thực tế của trường. Cần đo với dữ liệu triển khai thực tế trước khi chốt SLA hoặc tăng giới hạn.

## Kiểm chứng

- Suite backend đầy đủ: 220 ca đạt, không lỗi/thất bại/bỏ qua; gồm [9 ca hợp đồng bảng toàn khoa](../server/src/test/java/com/service/FacultyBoardContract.java) chạy trên H2 và cùng 9 ca trên [MySQL](../server/src/test/java/com/service/MySqlFacultyBoardTest.java). Tổng sau khi bổ sung phép đo mẫu: **221 ca riêng biệt đạt**.
- [Phép đo mẫu](../server/src/test/java/com/service/FacultyBoardScaleTest.java): 20 GV, 20 phòng, 200 lớp, 400 buổi/1.200 tiết trong một tuần. Trang lịch chỉ trả 10/20 hàng GV; xuất dữ liệu đủ 400 buổi và 200 dòng phân công. Sau khi clear persistence context, H2 trên máy hiện tại ghi nhận 331 ms cho trang lịch và 98 ms cho dữ liệu xuất. Thời gian này chưa tính truyền mạng/tạo file Excel và không đại diện database triển khai thực tế.
- Frontend: **46 unit test và 90 ca Playwright desktop/mobile đạt**; production build thành công. Test mới kiểm tra dùng chung tuần/bộ lọc giữa các tab, mở chi tiết bằng bàn phím, request cũ, trạng thái xuất mới, Excel, phân trang, lỗi/tải lại và lưu tùy chọn. Đã rà ảnh desktop/mobile.
- Các kiểm thử sử dụng API giả lập trên trình duyệt và H2/MySQL container cho backend. Không chạy migration hay nhập dữ liệu vào database đang dùng.

Chặng 6 đã bổ sung nhận/chuyển lớp cho thỉnh giảng, giờ cố định và lịch bận cùng luồng xem trước/lưu nháp. Xem [hướng dẫn thỉnh giảng](./thinh-giang-sau-xep-lich-chang-6.md).
