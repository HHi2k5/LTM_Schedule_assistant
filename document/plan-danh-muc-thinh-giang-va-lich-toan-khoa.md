# Kế hoạch: danh mục dùng lại, bổ sung thỉnh giảng và lịch toàn khoa

Ngày lập: 31/10/2026. Cơ sở khảo sát: mã nguồn nhánh `production`.

Cập nhật yêu cầu 01/11/2026: đã hoàn tất [tự tạo tài khoản giảng viên](./tai-khoan-giang-vien-tu-dong.md), tên đăng nhập MGV và mật khẩu ban đầu họ tên không dấu viết liền, chữ thường + MGV. Quy tắc này thay chính sách không tạo tài khoản của thiết kế/nhật ký ban đầu; chưa chạy tạo tài khoản hoặc migration trên database thực.

Cập nhật tiếp theo cùng ngày: đã bỏ tự đăng ký ở cả giao diện và API. Giảng viên nhận tài khoản được cấp từ nhà trường để đăng nhập; quản trị cấp qua danh mục/nhập Excel như hướng dẫn trên.

Trạng thái: hoàn tất triển khai và kiểm thử cả 8 chặng trong mã nguồn trên nhánh `production`, từ 31/10 đến 01/11/2026; chi tiết theo mục 11. Chưa áp migration vào database thực hoặc triển khai lại ứng dụng đang chạy. Bản nháp có thể còn xung đột nhưng chỉ xác nhận khi đủ tiết và hợp lệ; bảng toàn khoa có lịch tuần, phân công và danh sách buổi. Mục 2–9 giữ khảo sát/thiết kế ban đầu; hợp đồng thực tế và cách dùng nằm trong hướng dẫn từng chặng.

## 1. Kết quả cần đạt

1. Trường quản lý giảng viên, phân công môn và phòng học trong database; những lần xếp lịch sau chọn dữ liệu đã lưu, không phải tải lại các file danh mục.
2. Sau khi xếp lịch, có thể bổ sung giảng viên thỉnh giảng đã có phân công môn và thời gian dạy/bận; xem xung đột và điều chỉnh phần lịch liên quan.
3. Ngay trong màn hình chia lịch có bảng toàn khoa, xem được cả lịch nháp và lịch đã lưu khi mở lại trang.
4. Giao diện có nhiều màu hơn, phân biệt chức năng và trạng thái rõ ràng, vẫn đọc được bảng lịch dày thông tin.

## 2. Hiện trạng đã kiểm tra

| Thành phần | Hiện tại | Hướng thay đổi |
|---|---|---|
| [Teacher](../server/src/main/java/com/model/Teacher.java) | Gộp danh tính giảng viên, tài khoản đăng nhập và mốc tuần cá nhân | Bổ sung danh mục giảng viên theo trường, liên kết tài khoản khi có; giữ luồng tự đăng ký |
| [Room](../server/src/main/java/com/model/Room.java) | Đã lưu theo `school_code`, duy nhất mã phòng trong trường | Giữ bảng, bổ sung quản trị, trạng thái hoạt động và liên kết trường chuẩn |
| [Đọc file và lưu phòng](../server/src/main/java/com/service/ExcelScheduleService.java) | File phòng có thể bỏ qua nếu trường có phòng trong DB; cập nhật phòng đang cùng giao dịch xếp lịch, thất bại sẽ rollback | Lưu danh mục độc lập với chạy thuật toán; chọn tập phòng sử dụng |
| [Đọc kế hoạch tuần](../server/src/main/java/com/service/WeeklyPlanExcelReader.java) | File giảng viên vẫn bắt buộc; chứa môn, khóa và trọng số LT/TH | Tách đọc kế hoạch khỏi nguồn ứng viên; lấy ứng viên từ DB hoặc nhập file để cập nhật danh mục |
| [School](../server/src/main/java/com/model/School.java) | Là bản ghi trường thuộc từng giảng viên, không phải danh mục trường dùng chung | Tạo trường và khoa dùng chung; không đổi ý nghĩa bảng cũ |
| [Lưu kết quả tuần](../server/src/main/java/com/service/ScheduleService.java) | Thay các dòng `WEEKLY_AUTO` theo trường, kể cả giảng viên bị rút khỏi phân công; giữ dòng nguồn khác | Thay theo bản lịch và phạm vi chỉnh sửa, tránh xóa lịch của khoa khác |
| [Bộ xếp lịch tuần](../server/src/main/java/com/service/WeeklyScheduleEngine.java) | Tự ghi lịch khi xếp đủ; bỏ qua lịch tự động cũ của trường khi xây tập đặt chỗ | Tách tính toán/xem trước và ghi; chỉ bỏ qua những buổi đang được thay thế |
| [Kiểm tra tài nguyên](../server/src/main/java/com/service/WeeklyReservations.java) | Có kiểm tra giảng viên, phòng và lớp/nhóm theo ngày giờ thực | Tái sử dụng cho xem trước, chỉnh sửa và xác nhận lịch; bổ sung báo cáo xung đột có cấu trúc |
| [Màn hình xếp lịch](../frontend/src/AutoSchedulePage.jsx) | Nhập 3 file hoặc nhập tay; kết quả nằm trong state trang, có bảng chi tiết và bộ lọc | Chọn danh mục DB, mở lại bản lịch từ server, thêm bảng toàn khoa và luồng thỉnh giảng |
| Trường `cohort` / “Khóa” | Là khóa sinh viên, ví dụ 2024 | Giữ riêng; bổ sung `departmentId` để biểu diễn khoa |

## 3. Quy tắc nghiệp vụ đề xuất

### 3.1. Danh mục lưu lâu dài

- Giảng viên có mã trong trường, họ tên, khoa, loại cơ hữu/thỉnh giảng, trạng thái hoạt động và tài khoản liên kết nếu đã đăng ký.
- Mã giảng viên trường và mã tài khoản hệ thống là hai trường riêng; không tự đoán liên kết theo tên. Cùng tài khoản ở nhiều trường phải dùng chung danh tính khi kiểm tra trùng.
- Quản lý thêm/sửa hồ sơ không tạo tài khoản, mật khẩu hoặc quyền đăng nhập. Không trả dữ liệu xác thực trong API danh mục.
- Lưu quan hệ giảng viên–môn–khóa và trọng số LT/TH mặc định. Khi lập kế hoạch, sao chép vào phân công của đợt đó; có thể chỉnh trọng số mà không sửa mặc định cho đợt khác.
- Phòng thuộc trường, dùng chung giữa các khoa. Danh mục chứa mã, tên, sức chứa, loại LT/LAB, tòa nhà và trạng thái hoạt động.
- Dữ liệu đã được lịch tham chiếu thì ngừng sử dụng thay vì xóa. Thay danh mục không tự đổi lịch đã xếp; khi sửa lịch phải kiểm tra lại điều kiện hiện hành.
- Import danh mục là thao tác độc lập: xem trước số dòng mới/cập nhật/lỗi, xác nhận rồi lưu trong giao dịch. File lỗi không ghi dở; không xóa các bản ghi vắng trong file.
- Chuẩn hóa khoảng trắng và quy tắc chữ hoa/thường khi kiểm tra trùng mã, nhất quán với unique constraint MySQL.

### 3.2. Xếp lịch bằng danh mục

Luồng chính: chọn trường → khoa → học kỳ/đợt → tải kế hoạch mở lớp hoặc nhập tay → chọn giảng viên và phòng đã lưu → kiểm tra dữ liệu → chạy xếp lịch → xem bảng toàn khoa → lưu/xác nhận.

- Không bắt buộc file giảng viên và phòng nếu DB đã đủ dữ liệu. Nếu thiếu, chỉ rõ môn/khóa chưa có ứng viên hoặc loại phòng/sức chứa còn thiếu.
- Danh sách giảng viên không thay thế phân công: hệ thống vẫn cần biết ai dạy được môn nào và trọng số LT/TH.
- Tập phòng mặc định là các phòng đang hoạt động của trường; cho phép chọn tập con cho từng bản lịch.
- Kế hoạch giữ nguyên khoảng ngày, số lớp, sĩ số, tín chỉ, tổng tiết LT/TH và ràng buộc tuần đang có.
- Trước khi ghi lịch, kiểm tra lại dữ liệu/tài nguyên đã thay đổi kể từ lần xem trước.
- Bản nháp có thể thiếu tiết hoặc còn xung đột. Mặc định đề xuất chỉ xác nhận lịch khi đủ khối lượng và không có xung đột chặn.

### 3.3. Thêm thỉnh giảng sau khi đã xếp lịch

Mặc định đề xuất: bổ sung người đã được chia môn và có giờ dạy cố định hoặc lịch bận; kiểm tra trùng rồi đưa ra phương án sửa. Nếu ý người dùng là cho phép lưu lịch trùng để theo dõi, áp dụng nhánh nghiệp vụ ở cuối mục này.

1. Mở bản lịch đã có → **Thêm thỉnh giảng**.
2. Chọn hồ sơ đã lưu hoặc tạo hồ sơ mới; chọn môn, lớp/nhóm và loại LT/TH.
3. Chọn **nhận lại lớp đã có** hoặc **bổ sung lớp mới**. Nhận lại lớp phải chuyển phân công của lớp đó, không tạo bản sao làm tăng tổng tiết. Lớp mới phải có nhu cầu khối lượng tương ứng.
4. Chọn **giữ giờ lớp hiện tại**, **nhập lịch dạy cố định** hoặc **khai báo lịch bận**. Lịch bận bên ngoài chỉ chặn giảng viên, không chiếm phòng/lớp tại trường.
5. Hệ thống xem trước thay đổi và báo rõ buổi trùng, ngày giờ, tài nguyên, mức độ và các lớp liên quan.
6. Người dùng chọn đổi giảng viên, giờ hoặc phòng; hoặc yêu cầu xếp lại phạm vi đang chọn. Hiển thị so sánh trước/sau trước khi áp dụng.
7. Server kiểm tra lại và ghi toàn bộ thay đổi trong một giao dịch. Nếu thất bại hoặc bản lịch đã được người khác sửa, không ghi một phần.

Các loại cần phân biệt:

| Loại | Cách kiểm tra/xử lý |
|---|---|
| Giảng viên trùng giờ | Cùng người, cùng ngày thực, khoảng giờ giao nhau; kiểm tra cả lịch trường khác đã có |
| Phòng trùng giờ | Cùng trường và phòng, gồm cả lịch của khoa khác |
| Lớp/nhóm sinh viên trùng | Dùng định danh lớp cha/nhóm; LT chặn các nhóm con liên quan, TH chặn đúng nhóm |
| Trùng lịch bận | Khoảng bận của thỉnh giảng giao với buổi được phân công |
| Sai phòng hoặc khối lượng | Sai loại/sức chứa; thừa hoặc thiếu tiết sau đổi phân công |
| Vi phạm quy tắc LT/TH | Giữ các quy tắc phân ngày và khối lượng đang áp dụng trong thuật toán |

Quy đổi về ngày và giờ thực, múi giờ Asia/Ho_Chi_Minh; không so riêng số tiết giữa hai trường. Hai buổi tiếp giáp giờ không tính là trùng. Chuỗi tuần/mốc tuần khác nhau phải quy đổi trước khi so; không tự sửa mốc tuần cá nhân để làm khớp.

Xếp lại cục bộ giữ cố định mọi buổi ngoài phạm vi được chọn và các buổi được khóa. Chỉ giải phóng reservation của các buổi thực sự được thay. Nếu không có lời giải, trả lý do và phạm vi cần mở rộng; không tự xáo lại toàn khoa. Bản lịch chưa sửa tiếp tục được giữ làm cơ sở.

Nếu người dùng chọn cho phép lưu trùng: cho lưu ở trạng thái nháp có cảnh báo; cần chốt riêng có được xác nhận/công bố lịch trùng hay không. Không tự coi cảnh báo là đã được giải quyết.

### 3.4. Bảng toàn khoa trong màn hình chia lịch

- Đề xuất một khu vực **Toàn khoa** có hai tab: **Lịch tuần** và **Phân công**; mặc định mở lịch tuần. Phạm vi tab thứ hai chờ lựa chọn của người dùng.
- Thanh ngữ cảnh luôn hiển thị trường, khoa, học kỳ/đợt, tuần, trạng thái nháp/đã xác nhận và lần cập nhật.
- Lịch tuần: hàng theo giảng viên hoặc lớp, cột theo thứ; mỗi ô thể hiện giờ/tiết, môn, lớp/nhóm, phòng và nhãn LT/TH.
- Phân công: môn, khóa sinh viên, lớp/nhóm, giảng viên, loại cơ hữu/thỉnh giảng, tổng tiết yêu cầu/đã xếp/còn thiếu, trạng thái.
- Lọc theo giảng viên, môn, lớp, phòng, LT/TH, thỉnh giảng và chỉ xem xung đột; tìm kiếm kết hợp được với tuần.
- Chọn một buổi mở chi tiết và hành động sửa; bảng tổng hợp, số liệu khối lượng và báo cáo trùng cùng dùng một bản lịch từ server.
- Tải lại trang vẫn mở được bản lịch đã lưu. Khi đang tính toán hiển thị tiến trình; không cần WebSocket để vẽ từng bước của thuật toán trong đợt đầu.
- Bảng rộng có hàng tiêu đề/cột tên cố định, cuộn ngang; màn hình nhỏ có dạng danh sách.
- Xuất Excel có sheet lịch toàn khoa và phân công theo phạm vi đã chọn, ghi rõ trạng thái, phiên bản và bộ lọc xuất. Không dùng kết quả state cũ sau khi server đã lưu bản khác.

## 4. Mô hình dữ liệu đề xuất

Giữ các bảng tài khoản và lịch cá nhân hiện tại. Các tên dưới đây là tên dự kiến; hoàn thiện DDL ở bước triển khai.

| Bảng | Nội dung và ràng buộc chính |
|---|---|
| `institutions` | Danh mục trường, mã duy nhất, tên, cấu hình giờ tiết dùng chung |
| `departments` | Thuộc trường; mã khoa duy nhất trong trường |
| `institution_lecturers` | Hồ sơ giảng viên theo trường; mã duy nhất trong trường, khoa chính, loại, trạng thái, `account_teacher_id` nullable |
| `lecturer_assignments` | Giảng viên–môn–khóa sinh viên, trọng số LT/TH mặc định; duy nhất theo bộ khóa nghiệp vụ |
| `rooms` — mở rộng | Thêm trạng thái và FK trường chuẩn; giữ ánh xạ `school_code`, unique trường–mã phòng |
| `scheduling_plans` | Trường, khoa, nhãn học kỳ/đợt, ngày tuần 1, trạng thái, revision, tập phòng, snapshot đầu vào và cấu hình giờ |
| `scheduling_demands` | Thuộc bản lịch; định danh môn/lớp/nhóm, quan hệ nhóm–lớp cha, sĩ số, khoảng ngày, tổng tiết LT/TH, ứng viên/trọng số của đợt |
| `scheduling_entries` | Buổi học thuộc bản lịch/nhu cầu; giảng viên, phòng, ngày, giờ/tiết, loại LT/TH, cờ khóa và nguồn tự động/thỉnh giảng/chỉnh tay |
| `lecturer_busy_slots` | Lịch bận theo ngày/giờ thực, nguồn và danh tính liên kết; khoảng lặp theo tuần được chuẩn hóa ra ngày khi kiểm tra |
| `scheduling_changes` | Nhật ký lần thay đổi: người sửa, revision trước/sau, phạm vi, bản ghi trước/sau đủ để khôi phục lần thay đổi gần nhất |

- Dùng danh mục môn tối giản theo mã/tên trong phân công và nhu cầu; chưa cần xây toàn bộ chương trình đào tạo.
- Một bản lịch quản lý một khoa và một đợt/học kỳ. Danh mục phòng và kiểm tra tài nguyên có phạm vi toàn trường; kiểm tra giảng viên có thể vượt trường nếu đã liên kết đúng danh tính.
- Ứng viên/trọng số trong nhu cầu có thể lưu JSON có schema ở bản đầu để giảm số bảng; định danh lớp/nhóm và buổi phải có cột riêng phục vụ truy vấn/ràng buộc.
- Mỗi buổi dùng ngày cụ thể ở mô hình mới. Bộ chuyển đổi gom lại thành `weekSchedule` để phục vụ API lịch cá nhân và xuất Excel hiện có.
- Lịch mới lấy bản lịch/buổi làm nguồn chính. Khi xác nhận, cập nhật các dòng chiếu sang lịch cá nhân trong cùng giao dịch; không cho hai luồng cùng sửa độc lập một bản sao.
- Dòng lịch cá nhân do planner quản lý cần thêm liên kết bản lịch/buổi để cập nhật đúng phạm vi; không chỉ dựa vào `sourceType=WEEKLY_AUTO` và mã trường.
- Ràng buộc unique ngăn nhập lặp cùng buổi nghiệp vụ, FK ngăn tham chiếu sai trường/khoa; index theo bản lịch–ngày, giảng viên–ngày, trường–phòng–ngày.

## 5. Backend và hợp đồng API dự kiến

Tiền tố đề xuất `/api/admin`; bổ sung vào cấu hình security vì hệ thống hiện chặn các route chưa được khai báo. Mọi endpoint quản trị kiểm tra ADMIN phía server; dữ liệu lịch bận ngoài khoa chỉ trả thông tin cần thiết để giải thích xung đột.

| Nhóm | API dự kiến |
|---|---|
| Danh mục | `GET/POST /institutions`, `/institutions/{id}/departments`, `/institutions/{id}/lecturers`, `/institutions/{id}/rooms`; `PATCH` theo ID để sửa/ngừng hoạt động |
| Phân công mặc định | `GET/PUT /lecturers/{id}/assignments` |
| Nhập danh mục | `POST /institutions/{id}/{lecturers|rooms}/imports/preview`, `POST .../imports/commit`; commit kiểm tra lại file/snapshot và revision, không tin dữ liệu đã validate ở client |
| Bản lịch | `GET/POST /scheduling-plans`, `GET /scheduling-plans/{id}` |
| Tính/xem trước | `POST /scheduling-plans/{id}/generate-preview`; trả lịch đề xuất, thiếu tiết, phân công, xung đột và revision cơ sở |
| Lưu nháp | `PUT /scheduling-plans/{id}/draft`; kiểm tra schema/ràng buộc, cho giữ trạng thái chưa hoàn thành |
| Bảng toàn khoa | `GET /scheduling-plans/{id}/overview?week=...&view=...` |
| Thỉnh giảng | `POST /scheduling-plans/{id}/visiting-lecturers/preview`; trả thay đổi phân công/buổi và xung đột |
| Điều chỉnh | `POST /scheduling-plans/{id}/adjustments/preview`; hỗ trợ sửa tay và yêu cầu xếp lại một tập buổi/lớp cụ thể |
| Áp dụng | `POST /scheduling-plans/{id}/changes/commit`; nhận proposal ID và revision, kiểm tra lại rồi ghi giao dịch |
| Xác nhận | `POST /scheduling-plans/{id}/confirm`; kiểm tra đủ tiết, xung đột và đồng bộ lịch cá nhân |
| Khôi phục | `POST /scheduling-plans/{id}/changes/{changeId}/revert-preview`; khôi phục cũng phải kiểm tra xung đột/revision trước khi commit |

Hợp đồng xung đột thống nhất: `type`, `severity`, `entryIds`, `date`, `startTime`, `endTime`, tài nguyên liên quan, thông điệp tiếng Việt và phương án xử lý nếu có. Phương án chỉ là đề xuất, không đảm bảo luôn tìm được lời giải.

Tách bộ đọc Excel thành parser thuần, dịch vụ danh mục, bộ tính lịch và dịch vụ ghi. Tái sử dụng kiểm tra/ràng buộc đang có, nhưng không gọi thẳng hàm generate hiện tại trong luồng preview vì nó có tác dụng ghi.

Trong lúc commit, dùng revision chống ghi đè và khóa ghi lịch hiện có, đọc lại toàn bộ reservation liên quan, kiểm tra lại rồi ghi. Revision của một bản lịch không đủ để phát hiện khoa khác vừa đặt cùng phòng. Trả HTTP 409 khi dữ liệu cơ sở thay đổi; UI giữ nội dung chỉnh sửa để người dùng đối chiếu và xem trước lại.

## 6. Di trú dữ liệu và tương thích

1. Thêm migration Flyway tiếp theo sau V3, không sửa các migration đã chạy. Bổ sung theo từng bước có thể triển khai và kiểm chứng.
2. Tạo danh mục trường từ mã đang có ở phòng và lịch cá nhân; báo cáo các mã có tên/cấu hình giờ mâu thuẫn trước khi chọn cấu hình dùng chung.
3. Tạo hồ sơ giảng viên từ dữ liệu có sẵn, bỏ qua tài khoản ADMIN thuần quản trị; liên kết tài khoản theo ID đã biết. Không thay thông tin đăng nhập hoặc quyền.
4. Chưa có thông tin khoa thì đưa vào nhóm “Chưa phân khoa” để người quản lý gán, không suy từ cột khóa sinh viên.
5. Không thể khôi phục đầy đủ danh sách ứng viên/trọng số chỉ từ lịch đã lưu. Tận dụng snapshot cũ nếu đủ, phần thiếu yêu cầu nhập danh mục một lần.
6. Lịch cũ tiếp tục xem được. Nếu chuyển thành bản lịch chung, cần đối chiếu snapshot, lớp/nhóm, mốc tuần và sự trùng lặp; không gán mọi dòng trong một trường thành cùng bản lịch một cách tự động.
7. Giữ endpoint cũ trong giai đoạn chuyển tiếp, đưa qua adapter vào mô hình mới. Không cho luồng cũ tiếp tục thay toàn trường sau khi đã có bản lịch theo khoa, vì sẽ vượt phạm vi.
8. Danh mục đã lưu tồn tại dù chạy xếp lịch thất bại. Việc ghi nháp không thay lịch cá nhân đã xác nhận.
9. Khi thay bản lịch đã xác nhận, mở bản chỉnh sửa dựa trên revision hiện hành; lịch đang dùng vẫn giữ đến khi xác nhận bản sửa thành công. Lịch sử thay đổi phải giữ được trạng thái trước đó.

## 7. Thiết kế giao diện

Điều hướng quản trị đề xuất: **Danh mục** → Giảng viên / Phòng học / Phân công môn; **Xếp lịch** → Kế hoạch / Toàn khoa / Xung đột.

- Xếp lịch thành các bước rõ ràng: **Chọn dữ liệu → Phân công → Xếp và điều chỉnh → Xác nhận**. Hiển thị số giảng viên/phòng có sẵn và link quản lý danh mục ngay đầu trang.
- Thanh tiêu đề có nền chuyển sắc nhẹ xanh dương–tím; nền trang sáng, bảng nền trắng.
- Xanh dương `#2563EB` cho thao tác chính, xanh ngọc `#0F766E` cho phần đã hoàn thành, tím `#7C3AED` cho thỉnh giảng, cam `#B45309` cho phần cần xử lý, đỏ `#B91C1C` cho xung đột chặn.
- Đây là màu nhấn; kiểm tra tương phản với từng nền trước khi dùng cho chữ nhỏ. Màu không thay thế nội dung trạng thái.
- LT dùng nhãn xanh dương, TH dùng nhãn xanh ngọc; thỉnh giảng dùng badge tím. Màu trường trên lịch cá nhân vẫn có chú giải riêng để tránh lẫn ý nghĩa.
- Card số liệu: lớp/nhóm, giảng viên, tiết đã xếp, xung đột; nền màu nhạt, biểu tượng và số lớn.
- Bảng có zebra nhẹ, trạng thái hover/selected, tiêu đề cố định và cột tên cố định. Buổi trùng có viền, biểu tượng và mô tả khi mở chi tiết; có thể hiển thị các buổi trùng cùng ô mà không che mất nhau.
- Panel chi tiết bên phải chứa thông tin buổi, sửa phân công và so sánh trước/sau. Modal thêm thỉnh giảng chia bước chọn người → phân công/lịch → xem trước.
- Tách CSS theo khu vực, đưa màu/khoảng cách/radius về biến dùng chung; giảm style inline trong màn hình xếp lịch khi chạm tới các phần đó.
- Có trạng thái tải, rỗng, lỗi, thay đổi chưa lưu; dùng bàn phím được, focus rõ và danh sách thay thế trên mobile.

## 8. Chia đợt triển khai

| Đợt | Công việc | Điều kiện hoàn thành |
|---|---|---|
| 1 — Danh mục dùng lại | Migration trường/khoa, hồ sơ GV, phòng, phân công mặc định; API CRUD/import; UI danh mục; tách parser | Nhập một lần, tải lại trang và xếp nhiều lần không phải nạp lại file GV/phòng; thất bại khi xếp không làm mất danh mục |
| 2 — Bản lịch và toàn khoa | Bản lịch/nhu cầu/buổi, refactor preview/ghi, adapter lịch cá nhân, bảng toàn khoa và lọc/xuất | Mở lại được bản lịch từ DB; sửa khoa A không xóa lịch khoa B; toàn khoa và lịch cá nhân sau xác nhận thống nhất |
| 3 — Thỉnh giảng và xung đột | Lịch cố định/bận, nhận lớp/thêm lớp, báo cáo trùng, sửa tay, xếp lại cục bộ, revision/nhật ký | Thêm thỉnh giảng sau xếp lịch; xem trước được tác động; commit nguyên khối; giữ các buổi đã khóa/ngoài phạm vi |
| 4 — Hoàn thiện giao diện và hồi quy | Áp dụng hệ màu toàn ứng dụng, responsive, tương phản, tối ưu bảng và hướng dẫn sử dụng | Các luồng cũ/mới hoạt động, bảng dày vẫn đọc được trên desktop và mobile |

Áp dụng token màu và style chung từ đợt 1; mỗi màn hình mới dùng cùng hệ thiết kế. Đợt 4 hoàn thiện các trang còn lại và tính nhất quán. Xếp lại cục bộ triển khai sau khi phát hiện trùng và sửa tay đã ổn định.

## 9. Kiểm thử và nghiệm thu

### Danh mục và di trú

- Import lần đầu/lặp lại/cập nhật đúng bản ghi; từ chối mã trùng trong một file; lỗi một dòng không ghi dở.
- Cùng mã phòng hoặc GV ở hai trường không bị ghi đè. Ngừng hoạt động không làm mất lịch sử.
- Tải lại trang, đăng nhập lại và xếp nhiều đợt vẫn dùng đúng danh mục DB.
- Danh mục không lộ mật khẩu/token, không tạo tài khoản ngoài luồng tự đăng ký.
- Chạy migration trên MySQL có dữ liệu cũ; kiểm tra tài khoản, lịch cá nhân và cấu hình giờ được bảo toàn.

### Lịch và thỉnh giảng

- Thêm thỉnh giảng không trùng; trùng GV, phòng, lớp/nhóm; lịch bận trường khác; khác mốc tuần và bảng giờ; hai buổi tiếp giáp.
- Chuyển lớp sang GV khác không sinh hai lịch cho cùng lớp hoặc làm đổi tổng tiết; bổ sung lớp mới cập nhật nhu cầu đúng.
- Xếp lại chỉ sửa phạm vi chọn, giữ buổi khóa và lịch khoa khác; không có lời giải thì không ghi đè.
- Thiếu tiết/trùng lịch lưu được nháp theo chính sách, xác nhận bị chặn theo chính sách đã chốt.
- Hai ADMIN cùng sửa một bản lịch; hai khoa khác bản lịch cùng chọn một phòng; đổi trạng thái/sức chứa phòng giữa preview và commit.
- Xác nhận/khôi phục lỗi giữa chừng phải rollback cả lịch chung và phần chiếu lịch cá nhân.
- Lịch ngoài trường chưa liên kết danh tính không thể bảo đảm kiểm tra cùng người: UI cần yêu cầu liên kết hoặc nhập lịch bận trước khi kết luận không trùng.

### Toàn khoa và giao diện

- Chuyển tuần, lọc kết hợp, đổi tab, tải lại trang, xem bản nháp và bản đã xác nhận; số liệu khớp lịch/khối lượng và Excel.
- Khoa khác có đặt phòng vẫn được kiểm tra dù không xuất hiện trong bảng đang lọc.
- Desktop/mobile, bàn phím, tương phản, chữ tiếng Việt, trạng thái rỗng/lỗi/tải và nhiều buổi cùng ô.
- Tái sử dụng các bộ test Java hiện có về engine/reservations/persistence/security và các test frontend workflow/export/Playwright; thêm ca cho những hành vi mới ở trên.
- Đo với tập dữ liệu đại diện quy mô thực tế trước khi chốt ngưỡng phân trang và thời gian đáp ứng; không mặc định tải mọi buổi của mọi khoa lên trình duyệt.

## 10. Quy ước đã triển khai theo các chặng

1. Thỉnh giảng hỗ trợ nhận lớp, bổ sung nhu cầu/lớp mới, lịch cố định và lịch bận. Lưu nháp còn trùng để tiếp tục xử lý; chặn xác nhận khi thiếu tiết hoặc có xung đột chặn.
2. Toàn khoa có ba dạng xem: lịch tuần, bảng phân công và danh sách buổi; dùng chung phiên bản, tuần và bộ lọc.

Thứ tự khuyến nghị: **Danh mục → Bản lịch và bảng toàn khoa → Thỉnh giảng và điều chỉnh cục bộ → Hoàn thiện giao diện**.

## 11. Các chặng thực hiện và tiến độ

Mỗi chặng là một phần có thể review và kiểm thử riêng. Không đánh dấu hoàn thành chỉ vì đã có thiết kế. Bảng dưới chia nhỏ bốn đợt ở mục 8; thực hiện tuần tự theo phụ thuộc.

| Chặng | Phần bàn giao | Tiêu chí hoàn thành | Trạng thái |
|---|---|---|---|
| 1. Nền tảng danh mục | Migration bổ sung, model/repository/service và API ADMIN cho trường, khoa, GV, phân công, phòng; dữ liệu tách khỏi tài khoản | Tạo/đọc/cập nhật qua JSON, validate phạm vi trường/khoa, không tạo tài khoản, ngừng hoạt động, phát hiện cập nhật cũ; test persistence/security và hồi quy | Hoàn thành backend |
| 2. Quản lý danh mục trên web | Trang quản lý GV/phòng/phân công, chọn trường/khoa, import Excel xem trước rồi lưu độc lập; đối chiếu danh mục cũ | Quản lý và nhập một lần qua UI, tải lại không mất; lỗi import không ghi dở | Hoàn thành |
| 3. Xếp lịch bằng dữ liệu đã lưu | Tách parser kế hoạch và nguồn ứng viên; chọn danh mục DB thay file; trọng số riêng của lần xếp | Xếp lặp lại chỉ cần kế hoạch, bỏ yêu cầu file GV/phòng nếu DB đủ; thiếu dữ liệu được báo đúng | Hoàn thành |
| 4. Bản lịch nháp theo khoa | Bản lịch/nhu cầu/buổi, preview không ghi lịch cá nhân, revision, xác nhận và adapter API cũ | Mở lại bản nháp; xác nhận nguyên khối; sửa khoa này không xóa lịch khoa khác | Hoàn thành |
| 5. Bảng toàn khoa | Lịch tuần, bảng phân công, bộ lọc, chi tiết và xuất Excel dựa trên bản lịch server | Đúng tuần/phạm vi/trạng thái; số liệu và Excel khớp; dùng được trên mobile | Hoàn thành |
| 6. Bổ sung thỉnh giảng | Hồ sơ, nhận/chuyển lớp, lịch cố định hoặc lịch bận; preview báo cáo trùng | Không nhân đôi tiết; phát hiện trùng GV/phòng/lớp, ngày giờ thực và lịch ngoài trường | Hoàn thành |
| 7. Điều chỉnh và xếp lại cục bộ | Sửa tay, khóa buổi, xếp lại phạm vi chọn, trước/sau, nhật ký và khôi phục | Giữ lịch ngoài phạm vi; không có lời giải thì giữ bản cũ; xử lý ghi đồng thời | Hoàn thành |
| 8. Hoàn thiện màu sắc và nghiệm thu | Hệ màu toàn ứng dụng, responsive, tương phản, trạng thái lỗi/rỗng và hồi quy tổng thể | Luồng cũ/mới qua kiểm thử; bảng lịch dày đọc rõ, cảnh báo không phụ thuộc riêng màu | Hoàn thành |

Token màu và style của các màn hình mới được áp dụng từ chặng 2; chặng 8 hoàn thiện toàn ứng dụng. Chặng 1 chưa thay luồng ba file và chưa có giao diện danh mục; việc bỏ nạp lại file hoàn tất ở chặng 3.

### Nhật ký chặng 1

- Phạm vi: nền tảng backend và hợp đồng API danh mục; không chạy migration vào database phát triển/production trong lúc làm mã.
- Thiết kế chuyển tiếp: danh mục trường dùng mã trường làm định danh, khớp `school_code` hiện có. Phòng tiếp tục giữ khóa trường/mã phòng; chưa ép FK trường lên dữ liệu cũ chưa đối chiếu.
- Dữ liệu danh mục mới không tự tạo từ tên/mã giảng viên trong lịch cũ. Việc đối chiếu/backfill có xem trước thuộc chặng 2 để tránh gán sai trường, khoa hoặc tài khoản.
- Cấu hình giờ chung và snapshot của đợt được nối ở chặng 3–4; chặng 1 không tự chọn một cấu hình khi lịch cũ của các GV khác nhau.
- Đã thêm [migration V4](../server/src/main/resources/db/migration/V4__reusable_catalogs.sql), model/repository, DTO an toàn, [CatalogService](../server/src/main/java/com/service/CatalogService.java) và [CatalogController](../server/src/main/java/com/controller/CatalogController.java).
- API chỉ dành cho ADMIN; mã theo phạm vi trường, liên kết tài khoản tường minh; PUT yêu cầu revision và trạng thái. Không có API xóa cứng. Hợp đồng và ví dụ ở [hướng dẫn API chặng 1](./danh-muc-api-chang-1.md).
- Phòng có trạng thái hoạt động và revision. API phòng khả dụng, nguồn phòng từ Excel và cả hai thuật toán loại phòng đã ngừng hoạt động; lịch đã có vẫn giữ nguyên.
- Kiểm thử tập trung qua VS Code: 61 ca đạt. Toàn bộ Maven với `RUN_MYSQL_TESTS=true`: 149 ca đạt ngay, một assertion của test CHECK MySQL cần sửa theo mã lỗi 3819; chạy lại riêng ca đó đã đạt. Tổng trạng thái cuối: **150 ca, 0 lỗi/thất bại/bỏ qua**.
- Đã kiểm tra thực tế trên MySQL 8.0 tạm: nâng V3 lên V4, bảo toàn tài khoản/lịch/phòng, các ràng buộc unique/FK/trọng số, Flyway chạy lại không phát sinh migration. Bộ test lưu lịch MySQL hiện có cũng đạt với schema V4 và Hibernate validate.
- Không áp dụng migration vào database phát triển/production, không đổi cấu hình phát triển có sẵn. Chưa thay giao diện hoặc bỏ yêu cầu file GV; tiếp theo là chặng 2.

### Nhật ký chặng 2 — 31/10/2026

- Đã thêm **Danh mục nhà trường** vào điều hướng ADMIN, với [CatalogPage](../frontend/src/CatalogPage.jsx), form thêm/sửa trường/khoa/GV/phòng/phân công, lọc khoa/trạng thái, tìm kiếm và phân trang. Form giữ nội dung khi gặp xung đột revision; dữ liệu được đọc lại từ API sau lưu hoặc tải lại trang.
- Hệ màu xanh dương, tím, xanh ngọc và cam được áp dụng cho các khu vực của màn hình mới, có badge cơ hữu/thỉnh giảng và trạng thái. Đã xem ảnh kiểm thử desktop/mobile và kiểm tra không tràn ngang trang; bảng rộng cuộn trong vùng riêng.
- Đã thêm mẫu Excel và [CatalogImportService](../server/src/main/java/com/service/CatalogImportService.java): server parse/validate khi xem trước và khi ghi, trả dòng mới/cập nhật/giữ nguyên/lỗi, ràng buộc snapshot với file và dữ liệu cơ sở. Ghi cả file trong một giao dịch, không nhận action/ID do client tự quyết định.
- Các cột tùy chọn trống giữ liên kết, loại GV, tòa nhà và trạng thái hiện có; import không tự kích hoạt lại phòng đã ngừng sử dụng. Giữ nguyên mã phòng cũ có chữ thường và kiểm tra nhập lại không tạo thay đổi giả.
- Đã có đối chiếu dữ liệu trường/GV cũ bằng API chỉ đọc; người dùng chọn tên trường đúng và chuẩn bị file GV để xem trước rồi xác nhận nhập. Không tự ghép danh tính theo tên, không suy khoa từ khóa sinh viên và không thay lịch cá nhân.
- Hướng dẫn thao tác, mẫu cột và API ở [hướng dẫn chặng 2](./danh-muc-huong-dan-chang-2.md).
- Kiểm thử backend: `mvnw.cmd -o test` thành công, **150 ca đạt**, 12 ca MySQL tùy chọn bỏ qua do không bật `RUN_MYSQL_TESTS` trong lần chạy này. Bao gồm 11 ca tích hợp nhập danh mục và một ca mô phỏng lỗi ở dòng thứ hai để xác minh rollback những dòng đã ghi. Schema vẫn V4, đã kiểm tra MySQL ở chặng 1.
- Frontend: **35 unit test đạt**, **58 Playwright test đạt** trên desktop/mobile, gồm luồng cũ và mới; production build thành công. Kiểm thử trình duyệt dùng API giả lập, kiểm thử backend dùng API thật với H2; chưa phải phiên chạy end-to-end trên database đang dùng.
- Không thay cấu hình phát triển của người dùng, không áp dụng migration hoặc nhập dữ liệu vào database phát triển/production. Tiếp theo: **chặng 3 — lấy ứng viên, trọng số và phòng từ danh mục DB để bỏ yêu cầu nạp lại file khi lập lịch**.

### Nhật ký chặng 3 — 31/10/2026

- Đã tách bộ đọc kế hoạch khỏi nguồn ứng viên, thêm [CatalogSchedulingService](../server/src/main/java/com/service/CatalogSchedulingService.java), API tải nguồn xếp lịch và API chỉ nhận một file kế hoạch. Giảng viên, phân công, phòng và tên trường lấy từ database, kiểm tra đúng phạm vi và trạng thái hoạt động.
- [Màn hình xếp lịch](../frontend/src/CatalogSchedulingForm.jsx) mặc định chọn danh mục đã lưu; vẫn chuyển được sang ba file/ba bảng cũ. Có chọn tập GV/phòng, trọng số riêng của lần chạy, bảng giờ cũ hoặc khung mặc định khi chưa có lịch, và nhập kế hoạch trực tiếp hoặc bằng Excel.
- Trọng số chỉnh trong lần chạy được đưa vào snapshot môn học, không ghi đè phân công mặc định. File kế hoạch và lựa chọn được giữ để chạy lại; thay đổi nguồn trước lúc chạy được phát hiện bằng snapshot và HTTP 409, không tự gửi lại.
- Thêm [migration V5](../server/src/main/resources/db/migration/V5__catalog_schedule_identity.sql) để gắn danh mục GV với danh tính lưu lịch. Có tài khoản thì lưu đúng tài khoản; chưa có thì dùng hồ sơ lịch độc lập ổn định, không tạo username/password. Dữ liệu cũ có thể nối tường minh bằng `existingScheduleTeacherId` sau khi đối chiếu; không ghép theo mã hoặc tên một cách ngầm định.
- Đổi/bỏ liên kết khi đã có lịch yêu cầu đối chiếu, tránh tự gộp hoặc chuyển lịch sang người khác. Mã lịch dùng chung trong cùng trường có ràng buộc unique; lịch xuyên trường tiếp tục kiểm tra theo cùng danh tính đã xác định.
- Kết quả hiện mã GV trong trường kèm tài khoản. Excel giữ hợp đồng lịch cũ, thêm sheet ánh xạ danh tính; Xem TKB dùng đúng mã lưu lịch. Các kết quả thiếu tiết chỉ xem trước và rollback cả lịch lẫn liên kết/hồ sơ tạm.
- Giữ phạm vi cập nhật toàn trường ở chặng này và yêu cầu xác nhận kế hoạch đầy đủ. Chọn tập GV/phòng là chọn tài nguyên, chưa phải sửa riêng một khoa. Phạm vi khoa và bản nháp sẽ thực hiện ở chặng 4.
- Đã sửa quan hệ giảng viên–trường trong dịch vụ lưu lịch để các truy vấn trong cùng giao dịch nhìn thấy trường và bảng giờ vừa ghi.
- Kiểm chứng: **175 ca backend đã đạt**, gồm suite đầy đủ 174 ca với MySQL bật và một ca MySQL mới kiểm tra trực tiếp lưu/rollback nguồn danh mục; 10 ca API/danh tính cũng được chạy lại sau khi bổ sung kiểm tra multipart. Không có ca backend bỏ qua trong kết quả cuối. Migration kiểm tra cả V3 → V4 → V5 có dữ liệu cũ, FK và unique.
- Frontend: **39 unit test đạt**, production build thành công; **70 ca Playwright đã đạt** trên desktop/mobile sau khi sửa fixture hai ca đồng hồ (không chờ tải trang xếp lịch khi thời gian giả lập đang dừng). Các ca mới kiểm tra chỉ gửi file kế hoạch, chạy lại không nạp GV/phòng, trọng số/pool phòng, 409, nhập tay, xuất Excel và quyền xem lịch.
- Đã rà ảnh desktop/mobile và kiểm tra không tràn ngang trang. Kiểm thử trình duyệt dùng API giả lập; API backend được kiểm tra với H2 và adapter nguồn danh mục có kiểm tra trên MySQL tạm.
- Cách dùng và hợp đồng API ở [hướng dẫn chặng 3](./xep-lich-danh-muc-chang-3.md). Không áp dụng migration vào database đang dùng. Tiếp theo: **chặng 4 — bản lịch nháp theo khoa, preview không ghi lịch cá nhân và xác nhận nguyên khối**.

### Nhật ký chặng 4 — 31/10/2026

- Thêm [migration V6](../server/src/main/resources/db/migration/V6__department_schedule_plans.sql), model/repository cho bản lịch, phiên bản, nhu cầu và buổi học. Bản lịch gắn trường–khoa–học kỳ–mốc tuần, có revision và hai con trỏ nháp/đang áp dụng. Đầu vào và kết quả mỗi phiên bản được giữ nguyên; tạo bản mới không sửa phiên bản cũ.
- [SchedulingPlanService](../server/src/main/java/com/service/SchedulingPlanService.java) và [API bản lịch](../server/src/main/java/com/controller/SchedulingPlanController.java) cho phép tạo, mở lại, xếp/lưu nháp, đọc lịch sử, phân trang buổi học, kiểm tra và xác nhận. Preview dùng danh mục đúng khoa và phòng chung, chưa tạo danh tính lưu lịch hoặc ghi lịch cá nhân. Bản nháp thiếu tiết vẫn mở lại được và bị chặn xác nhận.
- Khi xác nhận, [bộ kiểm tra](../server/src/main/java/com/service/SchedulingPlanValidation.java) kiểm tra đúng buổi đã lưu theo dữ liệu hiện hành: khối lượng, trường/khoa, danh tính, phân công, phòng, ngày/giờ, trùng GV/phòng/lớp và quy tắc LT/TH. Báo cáo xung đột có ID buổi, ngày/giờ, tài nguyên và thông báo; không tự chạy lại thuật toán để thay phương án đã xem.
- Ghi lịch dưới khóa SQL chung, kiểm tra revision, cập nhật danh tính/lịch cá nhân/trạng thái trong cùng giao dịch. Chỉ thay những dòng thuộc ID bản lịch đang xác nhận; giữ ID của các khoa khác và lịch cá nhân ngoài phạm vi. Khi tạo nháp mới, phiên bản đã công bố tiếp tục áp dụng cho tới lần xác nhận thành công tiếp theo.
- Luồng ghi toàn trường cũ bị chặn nếu trường đã có bản lịch khoa được xác nhận; nhập/xóa toàn bộ lịch cá nhân cũng không được ghi đè phần lịch của bản lịch khoa. Đã kiểm tra cả mã trường khác chữ hoa/thường.
- Thêm [trang Bản lịch theo khoa](../frontend/src/SchedulingPlansPage.jsx) dành cho ADMIN: tạo/mở lại bản lịch, khôi phục kế hoạch và lựa chọn nguồn, xem phiên bản/buổi học, kiểm tra xung đột và xác nhận. Đổi đầu vào chặn xác nhận cho đến khi lưu nháp mới. Xuất Excel ghi phiên bản và trạng thái; giao diện dùng màu xanh dương, tím và cam cho các trạng thái, đã rà ảnh desktop/mobile và kiểm tra không tràn ngang trang.
- Sửa truy vấn nạp lịch cũ trong [TeacherRepository](../server/src/main/java/com/repository/TeacherRepository.java): nạp danh sách trường riêng rồi nạp hai tập tiết/môn theo batch, tránh nhân bản trường do tích Descartes khi tải lại. Chuẩn hóa chữ hoa/thường mã trường trong bộ đặt chỗ để lịch cũ không bị bỏ qua khi kiểm tra phòng/lớp.
- Kiểm chứng backend cuối cùng: **202 ca riêng biệt đạt, không thất bại/lỗi/bỏ qua**. Suite đầy đủ đầu tiên chạy 198 ca; ca ghi nháp đồng thời cần nhận đúng nhóm ngoại lệ cạnh tranh khóa của MySQL, đã sửa assertion và chạy lại thành công. Sau khi bổ sung kiểm tra API/lịch ngoài trường/mã trường cũ, lần chạy tập trung cuối **65 ca đạt**, bao gồm **13 ca bản lịch trên MySQL**, **12 ca trên H2**, engine và bộ đặt chỗ. Các suite còn lại giữ kết quả đạt từ lần chạy đầy đủ.
- MySQL tạm đã kiểm tra V5 → V6 giữ nguyên dữ liệu/FK, Hibernate validate, hai khoa xác nhận đồng thời cùng phòng chỉ một bên thành công, hai người lưu cùng revision không để lại phiên bản/buổi mồ côi. Test lỗi cuối giao dịch xác nhận xác minh rollback cả trạng thái, lịch cá nhân và danh tính mới.
- Frontend: **41 unit test và toàn bộ 80 Playwright test desktop/mobile đạt**, production build thành công. Kiểm thử trình duyệt dùng API giả lập; kiểm thử API backend dùng H2 và MySQL container, chưa phải phiên chạy end-to-end trên database đang dùng.
- Cách dùng, API và giới hạn ở [hướng dẫn chặng 4](./ban-lich-theo-khoa-chang-4.md). Không đổi cấu hình phát triển có sẵn và chưa áp dụng migration vào database phát triển/production. Tiếp theo: **chặng 5 — bảng lịch tuần toàn khoa, bảng phân công và bộ lọc dựa trên phiên bản đã lưu**.

### Nhật ký chặng 5 — 31/10/2026

- Thêm [FacultyBoardService](../server/src/main/java/com/service/FacultyBoardService.java), DTO và API `GET /api/admin/scheduling-plans/{id}/versions/{versionId}/overview`. Dữ liệu gắn đúng phiên bản, tuần và bộ lọc; trả ngữ cảnh trường/khoa/học kỳ, trạng thái, thời điểm đối chiếu, số liệu và danh sách lựa chọn từ snapshot.
- [Bảng toàn khoa](../frontend/src/FacultyScheduleBoard.jsx) đặt trong trang bản lịch, gồm lịch tuần theo GV hoặc lớp/nhóm, bảng phân công và danh sách buổi. Kết hợp lọc GV/môn/khóa/lớp/phòng/LT–TH/loại GV/xung đột và tìm kiếm; giữ tuần/bộ lọc khi đổi tab, lưu lựa chọn theo phiên bản trên trình duyệt. Chi tiết buổi hỗ trợ bàn phím, Escape và khôi phục focus.
- Tách số tiết/buổi theo tuần khỏi tổng khối lượng học kỳ. Phân công chưa có buổi vẫn hiện phần thiếu; lớp không học trong tuần không bị kết luận thiếu tiết cả kỳ. Khi lọc phòng/xung đột, số liệu tuần chỉ tính buổi khớp lọc, còn cột tổng kỳ ghi rõ toàn bộ nhu cầu của lớp/nhóm được chọn.
- Kiểm tra xung đột trước khi lọc hiển thị, gồm lịch đang áp dụng của khoa khác và lịch cá nhân/cross-school theo danh tính, ngày và giờ thực. Phần chiếu của chính bản lịch được loại trừ. Hai phía trùng trong phiên bản đều có cảnh báo; dữ liệu ngoài phạm vi không quy đổi được giờ được báo cần đối chiếu.
- Loại cơ hữu/thỉnh giảng được ghi thêm vào JSON snapshot danh tính của phiên bản mới. Phiên bản cũ đọc được và ghi rõ “Chưa lưu loại GV”, không lấy loại hiện tại để suy dữ liệu lịch sử. Không thêm migration ở chặng này.
- Desktop có bảng tuần cuộn trong vùng riêng với tiêu đề/cột tên cố định; mobile dùng danh sách theo người/lớp và ngày. Giới hạn 10 nhóm lịch hoặc 25 dòng/trang, mỗi hàng lịch tối đa 200 buổi và báo rõ nếu cần lọc hẹp. Đã rà ảnh desktop/mobile và kiểm tra trang không tràn ngang.
- [Xuất Excel toàn khoa](../frontend/src/facultyBoardWorkbook.js) tải dữ liệu mới từ server theo phiên bản/tuần/bộ lọc, xuất mọi trang với ba sheet lịch, phân công và thông tin. Trạng thái/phiên bản/thời điểm/bộ lọc được ghi trong file; kiểm tra response khớp đầy đủ yêu cầu và hủy request cũ khi chuyển tuần để tránh hiển thị/xuất dữ liệu cũ.
- Backend: suite đầy đủ **220 ca đạt, không lỗi/thất bại/bỏ qua**, gồm 9 ca bảng toàn khoa trên H2 và cùng 9 ca trên MySQL. Sau đó thêm/chạy riêng một ca dữ liệu mẫu đạt; tổng trạng thái cuối **221 ca riêng biệt đạt**. Các ca bao phủ LT/TH, lọc kết hợp, tuần trống, thiếu tiết, lịch khoa khác/lịch ngoài trường có mốc tuần và giờ tùy chỉnh, trùng hai phía, loại GV snapshot, phân trang, lịch sử, quyền và tham số API.
- Dữ liệu mẫu: 20 GV, 20 phòng, 200 lớp, 400 buổi trong một tuần, 1.200 tiết. API lịch trả 10/20 hàng GV mỗi trang, xuất dữ liệu đủ 400 buổi/200 dòng phân công. Sau khi clear persistence context, phép đo H2 trên máy hiện tại: đọc trang lịch **331 ms**, đọc dữ liệu xuất **98 ms**. Đây là kiểm chứng mẫu, chưa phải SLA hay benchmark MySQL/database thực tế.
- Frontend: **46 unit test, toàn bộ 90 Playwright test desktop/mobile đạt**, production build thành công. Sau khi rà giao diện bổ sung thời điểm cập nhật bản lịch và thông báo dữ liệu chưa đối chiếu trong chi tiết; build cuối đã đạt. Browser dùng API giả lập, backend dùng H2 và MySQL container riêng.
- [Hướng dẫn chặng 5](./bang-toan-khoa-chang-5.md) mô tả cách xem/lọc/xuất và ý nghĩa số liệu. Không thay cấu hình phát triển có sẵn, không ghi vào database phát triển/production. Tiếp theo: **chặng 6 — bổ sung thỉnh giảng sau khi xếp lịch, nhận/chuyển lớp và xem trước lịch cố định/bận cùng xung đột**.

### Nhật ký chặng 6 — 31/10/2026

- Thêm [VisitingLecturerService](../server/src/main/java/com/service/VisitingLecturerService.java) và API nguồn thỉnh giảng, tạo hồ sơ kèm phân công, xem trước và lưu đề xuất vào nháp. Chỉ dùng GV VISITING đang hoạt động, thuộc khoa, có phân công môn–khóa và trọng số dương đúng LT/TH. Tạo hồ sơ/phân công nguyên khối, không tạo tài khoản/mật khẩu.
- Nhận lại lớp chuyển toàn bộ buổi của lớp/nhóm sang GV được chọn. Giữ giờ hoặc thay toàn bộ giờ cố định trong phạm vi; không cộng vào lịch cũ. Bổ sung lớp/nhóm mới khai báo nhu cầu riêng, có định danh server, tổng tiết, tín chỉ, sĩ số và khoảng ngày; tổ TH có thể liên kết lớp LT cha để kiểm tra sinh viên.
- [VisitingResultBuilder](../server/src/main/java/com/service/VisitingResultBuilder.java) dựng lại lịch gộp, phân công và khối lượng từ buổi gốc của đề xuất. Chuyển lớp giữ tổng tiết; lớp mới tăng đúng nhu cầu đã khai báo; lịch vượt tổng tiết bị từ chối, thiếu tiết được lưu nháp và chặn xác nhận. Bổ sung metadata parent/sĩ số trong khối lượng; phiên bản cũ được đối chiếu từ buổi/nhóm gốc khi thiếu metadata.
- Preview chỉ đọc, trả trước/sau và xung đột. Commit giữ khóa ghi SQL chung, đọc lại revision/phiên bản/danh mục, dựng lại phương án và so token gắn người thao tác, input, kết quả và xung đột. Không nhận các buổi/khối lượng do client tự sửa từ response; thay đổi ngoài phạm vi làm phương án khác trả 409 để xem trước lại.
- Lưu đề xuất tạo phiên bản nháp mới, giữ bản đang áp dụng. Nháp có thể còn trùng; xác nhận luôn kiểm tra lại và không cho công bố thiếu tiết/trùng/cấu hình sai. Luồng xếp tự động cũ bị chặn với nháp có thỉnh giảng/lịch cố định để tránh làm mất ràng buộc; chỉnh riêng từng buổi/khôi phục sẽ làm ở chặng 7.
- [Migration V7](../server/src/main/resources/db/migration/V7__visiting_lecturer_busy_slots.sql) thêm metadata thay đổi và [lịch bận](../server/src/main/java/com/model/LecturerBusySlot.java) theo phiên bản. Lịch bận dùng ngày/giờ thực tới giây, có FK/unique/CHECK. Chỉ lịch bận của phiên bản đang được xác nhận mới áp dụng chung; đổi phiên bản tự giải phóng các khoảng đã bỏ, giữ lịch sử.
- Lịch bận chỉ chặn GV, không đặt phòng/lớp và không kích hoạt quy tắc ngày LT/TH. Đã nối vào kiểm tra xác nhận, bảng toàn khoa và cả hai thuật toán xếp lịch, gồm đối chiếu cùng danh tính ở trường khác. Khoảng bận mới đụng một bản lịch khác đang công bố bị chặn. Liên kết danh tính còn lịch bận đang áp dụng được bảo vệ dù không còn buổi dạy.
- [VisitingLecturerPanel](../frontend/src/VisitingLecturerPanel.jsx) có tạo hồ sơ/phân công, tìm/chọn lớp theo trang, nhu cầu lớp mới, giờ cố định và lịch bận lặp theo tuần, bảng trước/sau và lưu nháp. Đổi input bỏ kết quả preview; 409 giữ đầu vào và không tự gửi lại. Khoảng bận đã lưu được gộp lại theo tuần khi mở form, giữ nguyên các tuần nghỉ. Đã rà ảnh desktop/mobile và kiểm tra không tràn ngang.
- Kiểm thử backend: suite đầy đủ chạy **242 ca**, 241 đạt và một assertion rollback cần sửa loại ngoại lệ unique constraint từ `IllegalArgumentException` sang `DataIntegrityViolationException`. Sau khi sửa và thêm hai ca bảo vệ lịch đã công bố/đọc nhóm TH cũ, chạy tập trung **25 ca đạt**, gồm 11 trên H2, cùng 11 trên MySQL và 3 ca rollback. Trạng thái cuối **246 ca riêng biệt đạt, không lỗi/thất bại/bỏ qua**.
- Rollback đã kiểm tra lỗi ở phân công thứ hai khi tạo hồ sơ, lỗi cuối lúc lưu nháp và lỗi cuối khi xác nhận: không để lại hồ sơ/phân công nửa chừng, phiên bản/nhu cầu/buổi/busy mồ côi, trạng thái busy đã công bố hoặc danh tính lịch mới. MySQL kiểm tra nâng schema cũ qua V7 và chạy lại migration không làm mất tài khoản/lịch.
- Frontend: **52 unit test, toàn bộ 100 Playwright test desktop/mobile đạt**, production build thành công. Browser dùng API giả lập; backend chạy H2 và MySQL container riêng. Cấu hình phát triển có sẵn và database phát triển/production đang dùng được giữ nguyên.
- Cách dùng, hợp đồng API và giới hạn ở [hướng dẫn chặng 6](./thinh-giang-sau-xep-lich-chang-6.md). Tiếp theo: **chặng 7 — sửa tay, khóa buổi, xếp lại cục bộ, so sánh và khôi phục thay đổi**.

### Nhật ký chặng 7 — 31/10/2026

- Thêm [ScheduleAdjustmentService](../server/src/main/java/com/service/ScheduleAdjustmentService.java) và API xem trước/lưu nháp cho sửa, thêm/bỏ buổi, khóa/mở khóa, đổi GV theo lớp/nhóm, xếp lại phạm vi và khôi phục phiên bản. API chỉ cho ADMIN, kiểm tra revision, phiên bản cơ sở và snapshot danh mục; kiểm tra ID thuộc đúng nháp hiện hành.
- [Migration V8](../server/src/main/resources/db/migration/V8__scoped_adjustments_and_history.sql) thêm khóa định danh buổi để so sánh qua các phiên bản mới, cờ khóa, metadata điều chỉnh và nhật ký bất biến. Phiên bản cũ backfill khóa theo ID, không suy đoán dòng lịch sử. Đã kiểm tra nâng V3→V8 trên MySQL tạm với buổi/JSON V7 có sẵn và chạy lại Flyway không phát sinh migration.
- Sửa buổi giữ lớp và người dạy; đổi GV chuyển toàn bộ lớp/nhóm sang người đủ điều kiện. Bỏ buổi giữ tổng tiết yêu cầu; thêm buổi bù thiếu, không tự tạo nhu cầu hoặc cho vượt tổng tiết. Buổi khóa không bị sửa/bỏ/chuyển người, kể cả từ luồng thỉnh giảng; mở khóa là một thao tác riêng có lịch sử.
- [PartialScheduleEngine](../server/src/main/java/com/service/PartialScheduleEngine.java) giải phóng đúng buổi chưa khóa thuộc phạm vi. Giữ các buổi khác, lịch khoa khác/cá nhân liên quan và lịch bận làm đặt chỗ, đối chiếu danh tính/ngày/giờ thực. Người dùng chọn buổi hoặc toàn lớp/nhóm, khoảng ngày và tập phòng. Chọn lớp bù cả tiết thiếu. Tìm hai chiến lược, tối đa 250.000 lần thử; không tìm được toàn phương án thì giữ bản cũ, không lưu kết quả dở.
- Khôi phục toàn bộ phiên bản lịch sử vào nháp mới, gồm nhu cầu, phân công, buổi, lịch bận và cờ khóa. Trước/sau ghi rõ thay đổi tổng tiết và lịch bận; ảnh hưởng buổi hiện đang khóa cần mở khóa trước hoặc chọn cho phép rõ ràng. Kiểm tra tài nguyên hiện hành, không tự xác nhận và không sửa lịch sử.
- Preview chỉ đọc. Commit dựng lại đề xuất dưới khóa SQL chung và so token gắn người sửa/input/kết quả/xung đột; thay đổi làm đề xuất cũ mất hiệu lực trả 409. Phiên bản/nhu cầu/buổi/busy/con trỏ nháp/nhật ký cùng giao dịch. Nhật ký ghi người, thời gian, thao tác, lý do, phiên bản trước/kết quả và số buổi; 20 dòng/trang. Xác nhận lặp cùng phiên bản không tạo nhật ký thừa.
- [ScheduleAdjustmentPanel](../frontend/src/ScheduleAdjustmentPanel.jsx) có lựa chọn phân trang, phạm vi/ngày/phòng, so sánh trước/sau 25 dòng/trang, tổng tiết và xung đột. Đổi input bỏ preview; lỗi 409 giữ input và không tự retry. Bảng khoa có trạng thái khóa, thao tác từ chi tiết buổi và điều hướng bàn phím. Excel toàn khoa thêm trạng thái khóa. Nhật ký cho xem/khôi phục; nháp có điều chỉnh riêng được bảo vệ khỏi xếp lại toàn bộ qua luồng cũ.
- Bộ hồi quy backend đầy đủ: **275 ca đạt, không lỗi/thất bại/bỏ qua**, gồm ghi đồng thời, rollback khi lỗi nhật ký cuối giao dịch, lịch ngoài khoa mới công bố và xếp lại tránh lịch bận của nháp. Khi rà soát bổ sung ca khôi phục chế độ tự động sau khóa/mở khóa mà giờ học không đổi, đã sửa và chạy lại **29 ca điều chỉnh đạt** (14 H2, 15 MySQL). Tổng trạng thái cuối **277 ca riêng biệt đạt**, tất cả report đều xanh, không bỏ qua.
- Frontend: **58 unit test, toàn bộ 112 Playwright test desktop/mobile đạt**, production build thành công; đã rà ảnh desktop/mobile và kiểm tra không tràn ngang trang. Sau bổ sung thông báo thay đổi cấu hình khi không đổi buổi, chạy lại 4 ca khôi phục desktop/mobile và build cuối đều đạt. Browser dùng API giả lập; backend dùng H2/MySQL container riêng.
- Không thay cấu hình phát triển có sẵn, không áp migration vào database phát triển/production đang dùng. Cách sử dụng, API và giới hạn ở [hướng dẫn chặng 7](./dieu-chinh-cuc-bo-va-khoi-phuc-chang-7.md). Tiếp theo: **chặng 8 — hoàn thiện màu sắc toàn ứng dụng, responsive, tương phản và nghiệm thu giao diện**.

### Nhật ký chặng 8 — 01/11/2026

- Thêm [tokens.css](../frontend/src/tokens.css) và [theme.css](../frontend/src/theme.css), dùng màu chữ/nền/trạng thái, focus, viền input, spacing/radius và shadow chung. Áp dụng cho đăng nhập, điều hướng, tiêu đề, thống kê, lịch cá nhân, nhập/xếp lịch và các trang danh mục/bản lịch. Tiêu đề chuyển sắc nhẹ, panel trắng, nút chính xanh dương; cơ hữu/LT xanh dương, TH/hoàn tất xanh ngọc, thỉnh giảng tím, cảnh báo cam và lỗi đỏ. Trạng thái khóa dùng biểu tượng/nhãn xám riêng.
- Tăng độ rõ chữ phụ, trường chỉ đọc và nút vô hiệu hóa. Bảng khoa có chú giải LT/TH/thỉnh giảng/trùng/khóa và badge thỉnh giảng. Lịch cá nhân có chú giải trường; giữ màu ở viền/nền nhưng dùng chữ đậm màu để tên môn/giờ/phòng đọc được cả với màu trường trắng, vàng hoặc đen. Không đổi tọa độ/thời lượng/làn buổi học.
- Bổ sung liên kết bỏ qua điều hướng, trạng thái mục đang chọn và focus rõ. Menu mobile có nhãn/aria, giữ vòng Tab, Escape/trả focus, khóa cuộn và ngừng tương tác phía sau khi mở; đóng menu hoặc hết phiên trả trạng thái cuộn bình thường. Hỗ trợ giảm chuyển động và forced colors. Đã kiểm tra bố cục 1440/768/390/320px và cuộn bảng trong vùng riêng.
- Cập nhật Trợ giúp theo vai trò, hướng dẫn danh mục dùng lại, bản lịch/nháp, thỉnh giảng, điều chỉnh, xác nhận/lịch sử và luồng Excel cũ; giữ thông tin GV tự đăng ký và liên kết mã tài khoản, hồ sơ danh mục không tạo tài khoản đăng nhập. Giảm inline style ở phần bố trí đăng nhập.
- **58 unit test frontend đạt.** Thêm 5 kịch bản chạy trên desktop/mobile, kiểm tra màu render thực: chữ tối thiểu 4,5:1 và viền input đăng nhập 3:1 trong nhóm thành phần được chọn; focus, menu, hết phiên, giảm chuyển động, forced colors và màu trường rất nhạt/tối. Đã xem ảnh đăng nhập/lịch cá nhân/bảng khoa/trợ giúp; không coi đây là chứng nhận toàn bộ khả năng truy cập hay mọi trình duyệt.
- Hồi quy Playwright đầy đủ chạy **122 ca**: 120 đạt ngay, 2 ca desktop/mobile phát hiện thiếu câu nhắc GV tự đăng ký trong Trợ giúp mới. Đã bổ sung lại nội dung sản phẩm, chạy lại 4 ca Trợ giúp desktop/mobile đều đạt. Trạng thái cuối **122 ca riêng biệt đạt**. Production build cuối thành công; vẫn có cảnh báo kích thước chunk ExcelJS đã có từ trước. Browser dùng API giả lập.
- Chặng này không sửa backend/migration. Kết quả backend được giữ từ chặng 7: 277 ca riêng biệt đạt trên H2/MySQL; không chạy lại suite Java cho thay đổi giao diện. Không sửa cấu hình phát triển có sẵn, không áp migration vào DB thực, không triển khai lại ứng dụng đang chạy.
- [Hướng dẫn giao diện và phạm vi nghiệm thu](./giao-dien-va-nghiem-thu-chang-8.md) đã bàn giao. **Hoàn tất 8/8 chặng triển khai trong mã nguồn.**

### Bổ sung — tự tạo tài khoản GV theo MGV, 01/11/2026

- Theo yêu cầu mới, thêm [LecturerAccountService](../server/src/main/java/com/service/LecturerAccountService.java) dùng chung: username MGV chuẩn hóa chữ hoa, mật khẩu ban đầu là toàn bộ họ tên viết liền, chữ thường, bỏ dấu/ký tự không phải chữ Latin hoặc số, rồi nối MGV. Xử lý Đ/đ và Unicode tổ hợp; báo lỗi nếu không tạo được tên không dấu hoặc vượt giới hạn 72 byte. Mật khẩu lưu BCrypt, role luôn TEACHER; không trả mật khẩu/hash trong DTO hoặc nhật ký.
- Tự tạo khi lưu GV đang hoạt động trong danh mục, nhập danh mục sau preview, tạo hồ sơ thỉnh giảng kèm phân công và lưu thành công lịch từ Excel/nhập tay. Bản xem trước không tạo tài khoản; ghi thất bại rollback tài khoản cùng hồ sơ/phân công/lịch. Dòng danh mục cũ chưa có tài khoản được preview như cập nhật cần tạo tài khoản, kể cả khi các thông tin khác không đổi.
- Tài khoản đã có giữ username/mật khẩu; sửa tên, nhập lại hoặc xếp lịch lại không reset mật khẩu. Nếu MGV đã là username hoặc mã hồ sơ của người khác/ADMIN, báo cần liên kết đúng người, không ghi đè hay tự ghép theo tên. Đối chiếu tên đăng nhập và mã nội bộ theo truy vấn không phân biệt hoa/thường để giữ đúng danh tính khi nhập lịch cũ.
- Thêm API ADMIN `POST /api/admin/institutions/{schoolCode}/lecturer-accounts`, nhận các ID/revision và tạo/nâng tài khoản còn thiếu nguyên khối. Giữ mã nội bộ và các buổi của hồ sơ lịch cũ. Metadata tài khoản tự tạo cho phép đối chiếu bản nháp cũ khi nâng cùng danh tính, không mất khả năng kiểm tra/chỉnh/xác nhận lịch.
- UI danh mục có cột Tên đăng nhập, quy tắc tài khoản và nút **Tạo tài khoản còn thiếu** cho toàn bộ GV đang hoạt động chưa liên kết của trường, không phụ thuộc bộ lọc. Lỗi revision không tự retry. Đã cập nhật thông tin ở đăng ký, Trợ giúp, nhập Excel, hồ sơ thỉnh giảng và các hướng dẫn danh mục/lịch.
- [V9](../server/src/main/resources/db/migration/V9__automatic_lecturer_accounts.sql) thêm cờ `teachers.auto_provisioned`, mặc định false cho tài khoản cũ. MySQL tạm đã kiểm tra nâng schema, bảo toàn mật khẩu cũ và Hibernate validate. Không áp V9 hoặc tạo tài khoản trên database phát triển/production đang dùng.
- Backend: suite đầy đủ **302 ca**, 301 đạt và một lỗi fixture MySQL do dùng đồ thị entity chưa lấy bản đã merge. Đã sửa fixture để kiểm tra ID lịch thực đã lưu, bổ sung kiểm tra quyền/đối chiếu MGV khác hoa thường và sửa truy vấn nhận diện trên MySQL. Nhóm tập trung cuối **70 ca đạt**, gồm 12 tài khoản H2, 13 MySQL (có tạo đồng thời), 3 quy tắc, 16 auto-schedule, 16 hồi quy và 10 persistence. Trạng thái cuối **305 ca riêng biệt đạt, không lỗi/thất bại/bỏ qua**.
- Frontend: **58 unit test đạt**; Playwright đầy đủ **128 ca**, 126 đạt ngay và 2 kỳ vọng cũ về không tạo tài khoản được cập nhật theo yêu cầu mới, chạy lại cả hai đạt. Trạng thái cuối **128 ca riêng biệt đạt**; build cuối thành công. Backend dùng H2/MySQL container riêng, trình duyệt dùng API giả lập. [Hướng dẫn tài khoản tự động](./tai-khoan-giang-vien-tu-dong.md) đã bàn giao.

### Bổ sung — chỉ đăng nhập bằng tài khoản được cấp, 01/11/2026

- Bỏ nút/chế độ/form đăng ký khỏi [AuthPage](../frontend/src/AuthPage.jsx). Form đăng nhập chỉ gửi username/password, không gửi họ tên, vai trò hoặc mã người dùng tự chọn. Hiển thị hướng dẫn nhận tài khoản từ nhà trường.
- Xóa endpoint đăng ký trong [ScheduleController](../server/src/main/java/com/controller/ScheduleController.java), xóa chức năng đăng ký và sinh mã ngẫu nhiên trong [AuthService](../server/src/main/java/com/service/AuthService.java); DTO đăng nhập chỉ còn username/password. [SecurityConfig](../server/src/main/java/com/config/SecurityConfig.java) chặn đường dẫn `/api/schedule/auth/register` và đường dẫn con với mọi vai trò; đăng nhập vẫn là endpoint công khai. Anonymous nhận 401, TEACHER/ADMIN nhận 403 khi gọi đăng ký cũ.
- Cập nhật Trợ giúp và nhãn liên kết tài khoản, loại thông tin mời tự đăng ký. Quản trị tiếp tục cấp tài khoản qua danh mục/Excel và tạo tài khoản còn thiếu; giữ quy tắc MGV/mật khẩu ban đầu. Đăng nhập không tự tạo tài khoản khi username chưa có. Không cần migration mới cho thay đổi đóng đăng ký này.
- Kiểm thử tập trung **45 ca backend đạt**: security, đăng nhập, đổi mật khẩu, tài khoản tự động và persistence. Bao phủ chặn đăng ký cho khách/TEACHER/ADMIN, đường dẫn con/phương thức khác, đăng nhập tài khoản đã cấp và vai trò do server xác định. Thay các ca đăng ký đã bị loại bỏ bằng kiểm tra từ chối đăng ký/đăng nhập; kiểm tra tạo tài khoản đồng thời qua danh mục vẫn nằm trong suite riêng của tính năng cấp tài khoản.
- **58 unit test frontend và 10 Playwright test desktop/mobile đạt**, production build thành công. Kiểm tra không còn nút/form đăng ký, chỉ gửi username/password khi đăng nhập, giữ xử lý lỗi/401 và đổi mật khẩu. Không áp migration hay triển khai lại ứng dụng đang chạy.
