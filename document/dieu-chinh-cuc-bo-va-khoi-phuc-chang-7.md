# Chặng 7: điều chỉnh cục bộ và khôi phục bản lịch

Triển khai trên nhánh `production`. Tiến độ kiểm thử được ghi trong [kế hoạch chung](./plan-danh-muc-thinh-giang-va-lich-toan-khoa.md). Hướng dẫn này tiếp nối [luồng thỉnh giảng](./thinh-giang-sau-xep-lich-chang-6.md).

## Cách sử dụng

1. Vào **Bản lịch theo khoa**, mở bản lịch và chọn phiên bản mới nhất.
2. Bấm **Điều chỉnh lịch** hoặc mở chi tiết buổi trên bảng khoa để **Sửa buổi này**, **Khóa/Mở khóa buổi này**, **Xếp lại buổi này**.
3. Chọn thao tác, phạm vi và nhập lý do. Có thể tìm buổi theo môn, lớp, GV hoặc phòng; lựa chọn được giữ khi đổi trang. Phạm vi lớp/nhóm có tìm kiếm và phân trang riêng.
4. Bấm **Xem trước điều chỉnh**. Đọc bảng trước/sau, số buổi giữ nguyên, số buổi khóa, tổng tiết yêu cầu trước/sau, phần còn thiếu, lịch bận thêm/bỏ và các xung đột.
5. Bấm **Lưu điều chỉnh vào nháp** để tạo phiên bản mới. Phiên bản gốc và lịch đang áp dụng vẫn được giữ.
6. Kiểm tra lại rồi **Xác nhận bản nháp** khi đủ khối lượng và hợp lệ. Máy chủ kiểm tra lại tài nguyên hiện hành trong giao dịch xác nhận.

Đổi đầu vào sẽ bỏ kết quả xem trước. Khi có lỗi 409, giao diện giữ đầu vào, bỏ đề xuất cũ và không tự gửi lại. Mở lại bản lịch để lấy phiên bản/danh mục mới, sau đó xem trước lại. Hủy điều chỉnh nếu muốn quay về các thao tác khác; bản nháp đã lưu không thay đổi.

## Các thao tác

| Thao tác | Phạm vi và tác dụng |
|---|---|
| Sửa buổi | Chọn một buổi; đổi ngày, phòng, tiết bắt đầu, số tiết. Giữ lớp/nhóm và GV gốc. Server cũng hỗ trợ sửa nhiều buổi trong cùng đề xuất. |
| Thêm buổi | Thêm vào lớp/nhóm hiện có để bù tiết thiếu. Giữ GV của cả lớp; nếu chưa có GV có thể chọn người có phân công phù hợp. Không tạo nhu cầu mới hoặc tự tăng tổng tiết yêu cầu. |
| Bỏ buổi | Bỏ đúng các buổi đã chọn. Giữ nhu cầu của lớp; nháp có thể thiếu tiết và chưa thể xác nhận. |
| Khóa / mở khóa | Áp dụng đúng các buổi hoặc toàn bộ buổi của các lớp/nhóm đã chọn. Thay đổi trạng thái khóa cũng tạo phiên bản và nhật ký. |
| Đổi giảng viên | Chuyển toàn lớp/nhóm sang GV cơ hữu hoặc thỉnh giảng đủ điều kiện trong khoa, có phân công môn–khóa và trọng số dương đúng LT/TH. Không chia một lớp cho nhiều GV qua thao tác này. |
| Xếp lại phạm vi | Chọn buổi hoặc toàn lớp/nhóm, khoảng ngày và tập phòng cho phép. Chọn lớp/nhóm sẽ xếp cả tiết còn thiếu. Giữ phân công GV; muốn đổi người, lưu thao tác đổi GV trước. |
| Khôi phục phiên bản | Khôi phục toàn bộ nội dung phiên bản lịch sử vào một nháp mới: buổi, phân công, nhu cầu, lịch bận và trạng thái khóa. Không sửa/xóa lịch sử và không tự xác nhận. |

Buổi khóa không được sửa, bỏ hoặc chuyển sang người khác. Xếp lại tự động bỏ qua buổi khóa và giữ chúng làm ràng buộc. Luồng thỉnh giảng cũng không được thay giờ hoặc chuyển người của buổi khóa. Muốn đổi, mở khóa rõ ràng trong một đề xuất trước.

Với nháp đã điều chỉnh riêng hoặc có thỉnh giảng/lịch cố định, phần xếp lại toàn bộ từ danh mục được thay bằng hướng dẫn điều chỉnh phạm vi. Có thể khôi phục một phiên bản tự động trước đây để lập lại toàn bộ; server cũng bảo vệ quy tắc này.

## Xếp lại cục bộ

Thuật toán chỉ giải phóng buổi chưa khóa trong phạm vi chọn. Mọi buổi khác trong bản lịch được giữ nguyên, kể cả buổi ngoài khoảng ngày đang tìm. Các buổi được giữ không bị dời để tạo chỗ cho phạm vi chọn.

Tập đặt chỗ gồm buổi giữ lại, lịch đang áp dụng của khoa khác, lịch cá nhân có liên quan theo trường/danh tính và lịch bận đang công bố. Lịch bận riêng của nháp cũng tham gia. Đối chiếu theo ngày và giờ thực; tài khoản chung ở nhiều trường dùng chung danh tính GV. Lịch bận chỉ chặn GV.

Thuật toán giữ loại phòng, sức chứa, quy tắc LT/TH, khoảng học của môn/thực hành, giới hạn tiết theo buổi/tuần và bảng giờ. Tìm từ Thứ Hai đến Thứ Bảy như thuật toán tuần hiện có; sửa tay vẫn nhập được Chủ nhật và phải qua kiểm tra trước khi xác nhận.

Đây là tìm kiếm có giới hạn: hai chiến lược duyệt ngày, tối đa tổng 250.000 lần thử, tối đa 2.000 buổi mỗi lần. Thông báo **Chưa tìm được phương án** không chứng minh không tồn tại lời giải; có thể đổi ngày, phòng, mở khóa hoặc chọn phạm vi rộng hơn. Khi không tìm được phương án cho toàn phạm vi, server không lưu một phần và không tạo phiên bản/nhật ký mới.

Đề xuất không thay đổi nội dung cũng không tạo phiên bản mới. Buổi ngoài phạm vi còn xung đột vẫn được giữ và báo rõ; một kết quả xếp lại cục bộ không tự làm toàn bản lịch đủ điều kiện công bố.

## Khôi phục và nhật ký

- Chọn một phiên bản cũ, bấm **Khôi phục phiên bản đang xem**; hoặc dùng nút khôi phục trong nhật ký.
- Phạm vi khôi phục là toàn bản lịch, có thể giảm/tăng nhu cầu nếu phiên bản cũ có số lớp khác. Bảng trước/sau ghi rõ tổng tiết và lịch bận thay đổi.
- Nếu khôi phục ảnh hưởng buổi hiện đang khóa, mặc định bị chặn. Cần mở khóa trước hoặc đánh dấu **Cho phép khôi phục cả những buổi hiện đang khóa**, rồi xem trước và lưu rõ ràng.
- Khôi phục đối chiếu danh mục và tài nguyên hiện tại. Phòng/GV đã ngừng hoạt động hoặc lịch khác mới công bố có thể làm nháp khôi phục chưa đủ điều kiện xác nhận.
- Khôi phục cấu hình/chế độ lập lịch có thể không đổi buổi nào. Ví dụ khóa rồi mở khóa khiến giờ học trở lại như bản tự động gốc; khôi phục bản gốc vẫn tạo được nháp để mở lại việc lập toàn bộ. Giao diện báo riêng thay đổi cấu hình khi số buổi thay đổi bằng 0.
- **Nhật ký thay đổi bản lịch** ghi thao tác, người thực hiện, thời gian, phiên bản trước/kết quả, lý do và số buổi bị ảnh hưởng; 20 dòng/trang. Có thể mở lại phiên bản từ đây.
- Nhật ký bắt đầu từ khi triển khai V8; không tạo giả thao tác/người sửa cho lịch sử cũ. Các phiên bản trước V8 vẫn mở được trong danh sách phiên bản.

## API quản trị

Các endpoint thuộc `/api/admin/scheduling-plans/{id}` và chỉ cho ADMIN:

| Method / đường dẫn | Mục đích |
|---|---|
| `GET /adjustment-options` | Revision, phiên bản cơ sở, snapshot danh mục, GV/phòng/bảng giờ và lớp/nhóm. |
| `GET /adjustment-entries?baseVersionId=…&page=0&q=…` | Buổi của nháp hiện hành, 25 dòng/trang. Tìm kiếm chuỗi literal, không coi `%`/`_` là wildcard. |
| `POST /adjustments/preview` | Dựng đề xuất chỉ đọc, trả token, trạng thái, thay đổi và xung đột. |
| `POST /adjustments/commit` | Gửi lại nguyên input đã xem trước và token; tạo nháp mới nếu còn hợp lệ và có thay đổi. |
| `GET /changes?page=0` | Nhật ký phân trang. |

Input chứa `revision`, `baseVersionId`, `snapshot`, `action` (`MOVE`, `ADD`, `DELETE`, `LOCK`, `REASSIGN`, `AUTO`, `RESTORE`), cùng các trường áp dụng:

- `entryIds` hoặc `classIds`: phạm vi buổi/lớp; không dùng ID từ phiên bản khác.
- `edits`: danh sách `{entryId, classId, date, roomId, startPeriod, numPeriods}`. MOVE cần ID buổi hiện có; ADD để `entryId: null` và chọn lớp hiện có.
- `lecturerId`, `locked`, `fromDate`, `toDate`, `roomIds`, `restoreVersionId`, `overrideLocks`, `reason`.
- Commit có dạng `{input: <input đã xem trước>, previewToken: <token>}`. Không nhận các hàng trước/sau do client tự chỉnh làm dữ liệu để ghi.

`READY` có thể lưu nháp; `NO_CHANGE` và `NO_SOLUTION` không thể lưu. `canConfirm` chỉ mô tả điều kiện tại lúc xem trước; xác nhận là thao tác riêng và luôn kiểm tra lại.

Giới hạn: tối đa 100 lớp/nhóm hoặc 2.000 buổi chọn; tối đa 2.000 buổi thay đổi và 2.000 khoảng bận thêm/bỏ trong một đề xuất, kể cả khôi phục; bản lịch tối đa 50.000 buổi; ngày trong 100 tuần; lý do tối đa 255 ký tự. Bản lịch lớn cần chia phạm vi; một khôi phục vượt giới hạn bị từ chối rõ ràng.

## Lưu trữ và giao dịch

[Migration V8](../server/src/main/resources/db/migration/V8__scoped_adjustments_and_history.sql) thêm định danh buổi theo dòng lịch sử, cờ khóa, metadata điều chỉnh và bảng nhật ký. Buổi mới có khóa ổn định khi sao chép sang phiên bản tiếp theo; phiên bản cũ được backfill `LEGACY_<id>`, không suy đoán quan hệ giữa các buổi lịch sử.

[ScheduleAdjustmentService](../server/src/main/java/com/service/ScheduleAdjustmentService.java) dựng lại đề xuất ở commit dưới khóa ghi SQL chung. Token gắn người thực hiện, input, nội dung kết quả và xung đột. Revision, danh mục hoặc lịch hiện hành đổi làm phương án khác sẽ trả 409.

Ghi phiên bản, nhu cầu, buổi, lịch bận, con trỏ nháp và nhật ký cùng giao dịch. Lỗi ở bước cuối rollback toàn bộ. Xác nhận cũng ghi lịch cá nhân, danh tính lịch, trạng thái công bố và nhật ký nguyên khối. Xác nhận lặp lại cùng phiên bản đang áp dụng không ghi thêm nhật ký.

Chưa áp dụng V8 vào database phát triển/production đang dùng. Kiểm thử MySQL dùng container tạm riêng; giữ nguyên cấu hình phát triển có sẵn.
