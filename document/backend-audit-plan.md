# Rà soát lỗi backend và kế hoạch xử lý

Ngày kiểm tra: 26/10/2026. Nhánh: `feat/docker-ngrok`, commit `ba413f2`.

**Cập nhật triển khai:** các bản sửa BE-01 đến BE-13 đã được triển khai trong working tree theo [backend-fixes.md](backend-fixes.md). Đã chạy 8 test MySQL thực, gồm migration, rollback và ghi đồng thời; tất cả qua sau khi sửa deadlock khóa ghi. Bằng chứng dưới đây thuộc lần rà soát trước khi sửa. Không có migration nào được chạy lên DB dev/prod đang dùng.

## 1. Kết quả và phạm vi

Phần frontend sau bản sửa backend được lên kế hoạch và triển khai riêng trong [frontend-refinement-plan.md](frontend-refinement-plan.md).

- Bộ kiểm thử hiện có: **16/16 qua**, gồm 4 test `AuthServiceTest`, 7 test phân quyền HTTP, 1 test token, 1 test xếp lịch và 3 test đọc Excel.
- Chạy thêm **9 phép kiểm chứng tạm thời**, đều tái hiện được hành vi lỗi hoặc cấu hình giao dịch được mô tả bên dưới. Những phép kiểm chứng này khẳng định hành vi hiện tại, không phải kiểm thử hồi quy cho hành vi đúng.
- Không thay đổi code nghiệp vụ, không truy cập/ghi database thật, không commit/push trong lượt rà soát. Source test chẩn đoán tạm đã được gỡ sau khi chạy; báo cáo sinh ra nằm trong `server/target/surefire-reports/`.
- Các kiểm thử hiện tại mock repository và một số service. Chúng chưa chứng minh tính đúng của transaction, ràng buộc MySQL, lưu lịch nhiều lần hoặc hai yêu cầu đồng thời.
- `application-dev.yml` đang có thay đổi cục bộ từ trước; giữ nguyên và không chép giá trị nhạy cảm vào báo cáo.

Lệnh đã chạy từ `server`:

```powershell
.\mvnw.cmd '-Dmaven.repo.local=C:\Users\hhai6\.m2\repository' test
.\mvnw.cmd '-Dmaven.repo.local=C:\Users\hhai6\.m2\repository' '-Dtest=BackendAuditProbeTest' test
```

Lệnh thứ hai chỉ mô tả lần chẩn đoán đã thực hiện; muốn chạy lại cần dựng lại các tình huống ở mục 2. Chưa chạy ứng dụng với MySQL hoặc kiểm tra triển khai thực tế.

## 2. Các lỗi đã tái hiện

P1: xử lý trước vì có thể truy cập sai dữ liệu, mất lịch hoặc tạo lịch không hợp lệ. P2: xử lý sau các vấn đề P1, hoặc ghép cùng thay đổi có liên quan.

### BE-01 — P1 — Đăng ký công khai có thể nhận danh tính giảng viên đã có lịch

- Vị trí: `server/src/main/java/com/service/AuthService.java:47`, `:58`.
- Điều kiện: mã giảng viên đã tồn tại, chưa có username và không phải ADMIN.
- Tái hiện: tạo hồ sơ `VICTIM`, gọi `register` với username của người khác và teacherId `VICTIM`; server trả token có subject `VICTIM` và ghi username mới vào hồ sơ đó.
- Tác động: người biết mã GV có thể nhận tài khoản để đọc, thay thế hoặc xóa lịch của mã đó. Việc chặn đăng ký ADMIN hiện tại không chặn tình huống này.
- Hướng sửa hiện tại (cập nhật 27/10/2026): không cho nhập mã để nhận hồ sơ đã có. Đăng ký tự do nhận danh tính mới do server cấp. Chức năng ADMIN cấp tài khoản đã gỡ; hồ sơ chưa có tài khoản không tự liên kết với đăng ký mới.
- Kiểm thử `AuthServiceTest`: hồ sơ GV có sẵn chưa đăng ký bị từ chối khi được yêu cầu nhận qua đăng ký; vẫn giữ test chặn đăng ký ADMIN và nâng cấp mật khẩu SHA-256. Kiểm thử HTTP xác nhận URL cấp tài khoản cũ bị từ chối cho mọi vai trò.

### BE-02 — P1 — Hai luồng có thể gửi lịch rỗng vào hàm thay thế lịch

- Vị trí: `ExcelScheduleService.java:116`; `AutoScheduleService.java:89`, `:243`; `ScheduleService.java:70`.
- Tái hiện 1: workbook chỉ có header được import thành `courses=[]`, vẫn gọi `saveOrUpdateSchoolSchedule`.
- Tái hiện 2: yêu cầu xếp lịch có 1 lớp nhưng không có phòng; kết quả vẫn gọi hàm lưu với `courses=[]` cho GV đó.
- Hàm lưu xóa tập môn cũ trước khi thêm tập môn mới. Với lịch đã tồn tại, danh sách rỗng dẫn tới xóa hết các môn của trường đó.
- Hướng sửa: chặn import không có dòng hợp lệ; xếp lịch thất bại hoàn toàn không được thay dữ liệu cũ. Kết quả một phần phải có chính sách rõ ràng trước khi ghi đè, không mặc định xóa phần chưa xếp được. Giữ xóa lịch là thao tác API riêng.
- Nghiệm thu: file rỗng/sai cấu trúc, không có phòng, không xếp được lớp và lỗi đọc file đều giữ nguyên lịch cũ. Kiểm tra dữ liệu trong MySQL trước/sau, không chỉ kiểm tra response.

### BE-03 — P1 — Thay mốc tuần của một lần nhập làm lệch lịch các trường khác

- Vị trí: `ScheduleService.java:34`; cùng đường ghi tại `AuthService.java:63` khi liên kết hồ sơ cũ.
- Tái hiện: GV có trường A với tuần `1`, mốc `10/08/2026`; lưu trường B với mốc `17/08/2026`. Mốc chung của GV đổi, tuần của A giữ nguyên; buổi A chuyển ngày khi truy vấn lại.
- Hướng sửa trước mắt: giữ mốc hiện có nếu GV đã có lịch; từ chối mốc khác và yêu cầu đối chiếu. Nếu hỗ trợ quy đổi thì chuẩn hóa tuần/ngày trong một giao dịch, giữ nguyên ngày học thực tế của các trường đã lưu.
- Nghiệm thu: nhập B hoặc xếp lịch toàn trường không đổi các ngày học của A; mọi mốc được chấp nhận phải là Thứ Hai. Không cho đăng ký tài khoản sửa mốc lịch đã có.

### BE-04 — P1 — Khóa loại trùng làm mất buổi và che xung đột giữa các trường

- Vị trí: `ScheduleService.java:144`.
- Tái hiện: hai trường A/B có cùng tên môn, ngày và giờ bắt đầu. API chỉ trả **1 buổi**, `isConflict=false`.
- Nguyên nhân: khóa chỉ gồm tên môn + ngày + giờ, bỏ trường, mã môn, phòng và định danh bản ghi.
- Hướng sửa: tránh loại buổi hợp lệ để bù cho nhân bản JOIN; xử lý trùng theo định danh entity/nguồn dữ liệu. Hai trường khác nhau phải được giữ và đưa vào phát hiện xung đột.
- Nghiệm thu: trả đủ 2 buổi với cờ xung đột; hai buổi chỉ tiếp giáp giờ vẫn không xung đột; bản ghi JOIN nhân bản không tạo thêm buổi giả.

### BE-05 — P1 — Import bỏ bảng giờ thật, đồng thời sửa ngầm số liệu sai

- Vị trí: `ExcelScheduleService.java:106`, `:701`, `:719`; fallback ở `ScheduleService.java:184`.
- Tái hiện trên workbook `MonHoc` + `TietHoc`: tiết 1 trong file bắt đầu `07:20`, dữ liệu gửi lưu lại là `07:00`; số tiết dạng text `-2` lại trở thành `2`.
- Nguyên nhân: import chỉ đọc sheet đầu, luôn dùng 12 tiết mặc định. `parseIntSafe` loại mọi ký tự không phải chữ số, thay vì xác thực số nguyên.
- Tác động: giờ hiển thị và cảnh báo trùng có thể sai; gọi trực tiếp API vẫn qua được dù frontend có validation.
- Hướng sửa: đọc và kiểm tra `TietHoc`; trường hợp thiếu bảng giờ phải dùng nguồn cấu hình đã xác định hoặc báo thiếu. Parse số nguyên nghiêm ngặt, kiểm tra ngày/thứ/tuần/số tiết/tham chiếu từng tiết, báo lỗi theo dòng/cột. Không tự bịa giờ khi thiếu tiết.
- Nghiệm thu: giữ giờ `07:20`; từ chối `-2`, `1.5`, `abc`, tiết ngoài bảng, ngày không hợp lệ và chuỗi tuần sai; file hợp lệ vẫn import được.

### BE-06 — P1 — Thuật toán mở dư lớp khi số GV lớn hơn số lớp

- Vị trí: `AutoScheduleService.java:376`, `:382`.
- Tái hiện: yêu cầu 1 lớp lý thuyết, 2 GV, tắt thực hành; kết quả tạo **2 lớp**.
- Nguyên nhân: `Math.max(1, count)` ép cả phần phân bổ bằng 0 lên 1. Hàm cũng không sử dụng quota `assignedClasses` từ dữ liệu phân công cũ.
- Hướng sửa: cho phép quota 0; bảo đảm tổng phân bổ đúng tổng lớp yêu cầu. Phân biệt rõ đầu vào chia đều và đầu vào chỉ định quota từng GV; giữ tương thích luồng file cũ.
- Nghiệm thu: tổng quota bằng số lớp yêu cầu với 0/1/n lớp, số GV ít/bằng/nhiều hơn số lớp; quota từng GV được kiểm tra, không bị bỏ qua.

### BE-07 — P1 — Có thể xếp buổi vượt bảng tiết

- Vị trí: `AutoScheduleService.java:307`, `:315`.
- Tái hiện: bảng giờ có tiết 1–12; yêu cầu tiết bắt đầu 12, học 5 tiết. Server tạo buổi kết thúc ở **tiết 16**.
- Hướng sửa: kiểm tra mọi tiết trong dải trước khi tìm phòng; chỉ xét các ca đầy đủ giờ hợp lệ. Nếu có quy tắc không vượt buổi sáng/chiều thì mô hình hóa riêng, không suy từ số tiết bắt đầu.
- Nghiệm thu: không có slot kết quả tham chiếu tiết không tồn tại; không xếp được thì trả cảnh báo có lý do và không làm mất lịch cũ. Workbook kết quả phải xuất được từ dữ liệu thật của test.

### BE-08 — P1 — Transaction không mặc định rollback lỗi đọc file dạng checked exception

- Vị trí: `ExcelScheduleService.java:220`, `:236`, `:239`, `:576`.
- Đã kiểm chứng bằng `AnnotationTransactionAttributeSource`: transaction của `autoScheduleFrom3Files` trả `rollbackOn(new IOException(...)) == false`.
- Đường xử lý lưu phòng trước rồi mới đọc file GV/kế hoạch. Nếu bước đọc sau phát sinh checked exception, cấu hình hiện tại cho phép transaction commit các thay đổi phòng trước đó dù API trả lỗi.
- Giới hạn bằng chứng: đã xác nhận cấu hình rollback thực của thư viện đang cài; chưa gây lỗi và kiểm tra dữ liệu bằng MySQL thật.
- Hướng sửa: tách đọc/kiểm tra toàn bộ file khỏi pha ghi DB; dùng transaction có chính sách rollback phù hợp cho pha ghi. Kiểm tra cả lời gọi qua proxy và phạm vi giao dịch ngoài cùng.
- Nghiệm thu: integration test cố ý gây lỗi sau một bước ghi; toàn bộ phòng và lịch trước/sau không thay đổi khi tác vụ thất bại.

## 3. Rủi ro phát hiện qua đọc code, cần kiểm chứng tích hợp

| Mã | Ưu tiên | Bằng chứng và tình huống cần kiểm tra | Hướng xử lý |
|---|---|---|---|
| BE-09 | P1 | `AutoScheduleService.java:46`: occupancy bắt đầu rỗng cho mỗi lần gọi; không nạp lịch phòng đã lưu của GV ngoài yêu cầu. Lần xếp sau hoặc hai request đồng thời có thể dùng phòng đã bận. | Nạp lịch hiện hữu ngoài phạm vi thay thế; xét ngày/tuần thật; khóa hoặc version theo tác vụ/phạm vi lịch. Test hai lần xếp và cạnh tranh ghi. |
| BE-10 | P2 | `ExcelScheduleService.java:586` và `AutoScheduleService.java:30` lấy phòng toàn DB; `:566` cập nhật phòng theo mã toàn cục, dù repository đã có truy vấn theo trường. | Ràng buộc phòng theo trường; xác định mã phòng là toàn cục hay theo trường, migration và kiểm thử hai trường có mã phòng giống nhau. Chưa kết luận có dữ liệu hai trường bị trộn trên DB hiện tại. |
| BE-11 | P2 | `AutoScheduleService.java:444`: lịch bận ngoài chỉ còn thứ + giờ, bỏ tuần và mốc học kỳ. Occupancy nội bộ cũng không có tuần. | Test hai lịch không giao tuần nhưng cùng thứ/giờ; tính giao nhau bằng ngày và dải tuần, tránh từ chối nhầm. |
| BE-12 | P2 | `ScheduleController.java` bắt `Exception` và thường trả 400; `getWeeklySchedule`/delete không có xử lý lỗi nghiệp vụ tập trung. `Map.of(..., e.getMessage())` có thể lỗi tiếp nếu message null. | Error DTO + `@RestControllerAdvice`; thống nhất 400/401/403/404/409/500, log lỗi nội bộ, không lộ SQL/stack trace. Test thiếu dữ liệu, lỗi persistence và exception không có message. |
| BE-13 | P2 | Schema SQL gốc thiếu các cột tài khoản; model có username/password/role. Prod dùng `ddl-auto: none`, cấu hình datasource prod còn gắn cứng. | Tạo migration có version, chạy DB rỗng và DB nâng cấp; đưa cấu hình prod về môi trường. Không sửa file cấu hình cục bộ đang dùng. |

Các điểm cấu hình cần chốt cùng triển khai: JWT secret ổn định cho prod, giới hạn upload đồng nhất giữa dev/docker/prod, origin được cho phép theo môi trường. Chưa có bằng chứng các cấu hình này đang gây lỗi trên môi trường triển khai thực tế.

## 4. Kế hoạch thực hiện

### Bước 1 — Khóa việc nhận tài khoản theo mã GV

- Xử lý BE-01 trong `AuthService`, luồng cấp/liên kết tài khoản, DTO và test liên quan.
- Trước mắt từ chối nhận hồ sơ có sẵn qua đăng ký công khai; thiết kế lời mời/quy trình quản trị cho hồ sơ được xếp lịch trước.
- Thêm test hồi quy vào `AuthServiceTest` và kiểm thử qua endpoint đăng ký.
- Điều kiện hoàn thành: không thể truy cập lịch có sẵn chỉ bằng cách biết mã GV; 4 test cũ vẫn qua.

### Bước 2 — Bảo toàn dữ liệu khi nhập/xếp lại

- Xử lý BE-02, BE-03, BE-08.
- Tách parse/validate, tính kết quả và commit; quyết định rõ có chấp nhận kết quả một phần hay không.
- Giữ mốc tuần cũ, không ghi lịch rỗng ngoài thao tác xóa chủ động; bổ sung test rollback với MySQL riêng cho kiểm thử.
- Điều kiện hoàn thành: mọi tình huống đầu vào sai/thất bại không đổi lịch/phòng đã lưu; nhập thêm trường không đổi ngày của trường cũ.

### Bước 3 — Chuẩn hóa dữ liệu lịch và cảnh báo

- Xử lý BE-04, BE-05 cùng phần validation/response của BE-12.
- Đọc bảng giờ thật, kiểm tra dữ liệu ở server, bỏ fallback giờ âm thầm và sửa định danh buổi học.
- Điều kiện hoàn thành: nhập → lưu → xem tuần giữ đúng ngày/giờ; đủ buổi và cờ xung đột giữa các trường; lỗi chỉ rõ vị trí dữ liệu sai.

### Bước 4 — Sửa các ràng buộc thuật toán

- Xử lý BE-06, BE-07; kiểm chứng và xử lý BE-09, BE-10, BE-11.
- Test quota, dải tiết, tuần không giao nhau, phạm vi phòng, lịch đã lưu và hai yêu cầu cạnh tranh trước khi tối ưu cách gom ngày/chọn phòng.
- Điều kiện hoàn thành: không mở dư lớp, không dùng tiết không tồn tại, không xếp đè lịch ngoài phạm vi thay thế; không trộn phòng các trường.

### Bước 5 — Kiểm chứng DB, API và cấu hình triển khai

- Hoàn thiện BE-12, BE-13; dùng MySQL test độc lập, không dùng dữ liệu dev thật.
- Kiểm tra migration DB rỗng/nâng cấp, các API thực với token TEACHER/ADMIN, rollback và chạy lại lịch; kiểm tra workbook xuất từ kết quả backend.
- Chạy lại toàn bộ test backend và frontend/build sau khi thay đổi contract; ghi rõ kết quả từng nhóm và phần nào chưa được kiểm chứng.

## 5. Quy tắc kiểm thử hồi quy

Các probe tạm đã chạy cần chuyển thành test kỳ vọng hành vi đúng khi sửa: phải từ chối nhận hồ sơ không được cấp quyền, phải giữ 2 buổi khác trường, phải giữ mốc cũ, phải từ chối lịch rỗng/số âm, phải giữ giờ `TietHoc`, phải mở đúng số lớp và phải từ chối slot ngoài bảng tiết. Không giữ các assertion chấp nhận hành vi lỗi làm test nghiệm thu.

Không có bước nào tự động commit/push hoặc áp dụng migration vào database đang dùng. Tài liệu này là kết quả kiểm tra và kế hoạch; việc sửa code là bước tiếp theo.
