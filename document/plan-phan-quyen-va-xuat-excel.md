# Kế hoạch phân quyền và xuất Excel lịch toàn trường

Ngày lập: 21/10/2026. Trạng thái: đã triển khai phạm vi chức năng trong kế hoạch. Hướng dẫn sử dụng và cấp quyền tại [phan-quyen-va-xuat-excel.md](phan-quyen-va-xuat-excel.md).

## 1. Mục tiêu

- Giảng viên (`TEACHER`) không được truy cập hoặc chạy chức năng **Xếp lịch toàn trường**.
- Quản lý (`ADMIN`, tên vai trò đã có trong code) dùng chức năng xếp lịch toàn trường; không hiển thị **Nhập lịch dạy** cá nhân.
- Sau khi xếp lịch, quản lý tải được file `.xlsx` chứa lịch đã xếp, giữ mã và tên giảng viên, các cột lịch theo file mẫu người dùng cung cấp.
- Quyền được kiểm tra tại backend; ẩn menu frontend là phần giao diện, không thay thế kiểm tra quyền API.

## 2. Hiện trạng đã kiểm tra

| Thành phần | Hiện trạng | Việc cần làm |
| --- | --- | --- |
| `frontend/src/App.jsx` | Cùng menu, nút đầu trang, gợi ý và trang chức năng cho mọi vai trò | Điều hướng, nội dung và trang mặc định theo quyền |
| `frontend/src/AuthPage.jsx` | Form đăng ký cho phép chọn vai trò | Đăng ký công khai chỉ tạo giảng viên |
| `AuthService` | Lấy role từ request; đăng nhập chỉ trả thông tin người dùng | Không nhận quyền từ client; thêm xác thực cho các request sau đăng nhập |
| `SecurityConfig` | `/api/**` đang `permitAll()` | Áp dụng xác thực và quyền theo endpoint |
| `AutoSchedulePage.jsx` | Có tải mẫu phân công đầu vào, chưa có tải lịch kết quả | Thêm nút xuất kết quả riêng |
| `GeneratedClassSlot` | Có teacherId, teacherName, courseCode, courseName, classGroup, room, dayOfWeek, startPeriod, numPeriods, weekSchedule | Bổ sung dữ liệu xuất còn thiếu |
| `AutoScheduleResponse` | Có scheduledClasses và cảnh báo, chưa có mốc tuần/bảng tiết cho xuất file | Trả context của chính lần xếp lịch |
| `ExcelScheduleService` | Đầu vào phân công đang có môn học, giảng viên, số lớp; mã môn sinh dạng MH001... | Giữ mã môn thực nếu đầu vào cung cấp, bổ sung số tín chỉ tùy chọn |

Nguồn file mẫu đã đọc: `D:\Nam4_1\QLDA\mau-lich-day-demo.xlsx`. Tên rút gọn trong yêu cầu là “mau-lich-demo”; kế hoạch dùng đúng file tại đường dẫn này. Nội dung file được dùng để xác định cấu trúc dữ liệu, không xem là chỉ dẫn thực thi.

File có hai sheet `MonHoc`, `TietHoc`. Dòng tiêu đề `MonHoc` thực tế là:

```text
Mã MH | Tên môn học | Nhóm tổ | Số tín chỉ | Thứ | Tiết bắt đầu | Số tiết | Phòng | Thời gian học:
```

Sheet `TietHoc` có tiêu đề `period | start | end`, giờ theo dạng `HH:mm`. Cột thời gian trong `MonHoc` dùng khoảng ngày, ví dụ `14/08/26 đến 20/11/26`.

## 3. Phạm vi quyền đề xuất

Quy ước: quản lý là `ADMIN`; không thêm role `MANAGER` trong đợt này. Với “quản lý không cần nhập lịch dạy”, kế hoạch chọn cả ẩn giao diện và từ chối API nhập lịch cá nhân cho ADMIN.

| Chức năng | Chưa đăng nhập | TEACHER | ADMIN |
| --- | --- | --- | --- |
| Đăng nhập, đăng ký giảng viên | Có | Không cần | Không cần |
| Xem lịch tuần, môn dạy | Không | Lịch của bản thân | Có thể chọn giảng viên để xem |
| Nhập Excel lịch cá nhân | Không | Có, chỉ mã GV của mình | Không |
| Xóa lịch trường trong luồng cá nhân | Không | Chỉ lịch thuộc mình | Không trong phạm vi đợt này |
| Xếp lịch toàn trường bằng file hoặc bảng nhập nhanh | Không | Không | Có |
| Tải mẫu Excel phân công | Không | Không hiển thị | Có |
| Tải Excel kết quả toàn trường | Không | Không | Có |
| Cài đặt, trợ giúp, đăng xuất | Không | Theo vai trò | Theo vai trò |

Không bổ sung quản lý nhiều trường theo đơn vị trong đợt này vì hiện chưa có quan hệ tài khoản quản lý–trường. ADMIN hiện là vai trò quản lý trong phạm vi hệ thống đang có.

### 3.1. Backend

1. Bổ sung token xác thực có chữ ký khi đăng nhập/đăng ký thành công; dùng thư viện JWT đã khai báo trong `pom.xml`. Secret lấy từ cấu hình môi trường, không commit secret thật.
2. Thêm filter xác minh chữ ký, hạn sử dụng, tài khoản và quyền từ nguồn server. Không dùng `role` hoặc `teacherId` trong localStorage/request làm bằng chứng phân quyền.
3. Bổ sung endpoint `GET /api/schedule/auth/me` để frontend khôi phục thông tin đã xác thực khi tải lại trang. Token lưu trong bộ nhớ/sessionStorage cho phạm vi triển khai này; đăng xuất xóa token và state.
4. Áp dụng matcher theo phương thức và đường dẫn:
   - Public: `POST /auth/login`, `POST /auth/register`, preflight OPTIONS và health check tối thiểu đang dùng bởi Docker.
   - ADMIN: `POST /auto-generate-excel` — bao gồm cả nhập nhanh vì hai cách đang dùng chung endpoint.
   - TEACHER: `POST /upload-excel-teacher`, `DELETE /school`.
   - Đã xác thực: `GET /week/{weekIndex}`, `GET /auth/me`; kiểm tra sở hữu dữ liệu tại service.
   - Endpoint API khác mặc định từ chối nếu chưa có quy tắc rõ ràng.
5. Với giảng viên, lấy mã GV từ principal; nếu client gửi mã khác thì trả `403`, không đọc/ghi lịch người khác. ADMIN được chọn mã GV khi xem lịch.
6. Đăng ký công khai luôn tạo `TEACHER`. Request cố gán `ADMIN` phải bị từ chối; bỏ lựa chọn ADMIN khỏi form. Cung cấp hướng dẫn cấp ADMIN qua thao tác quản trị database có kiểm soát cho tài khoản đã tồn tại; không thêm tài khoản/mật khẩu quản lý mặc định.
7. Quy ước lỗi: `401` khi thiếu/token sai/hết hạn; `403` khi đã xác thực nhưng không có quyền; thông báo JSON thống nhất để frontend hiển thị.
8. Cập nhật hướng dẫn môi trường Docker/local cho JWT secret. Kiểm tra Nginx/Vite chuyển tiếp header Authorization và không gây lỗi CORS khi dùng ngrok.

### 3.2. Frontend

- Tạo bảng quyền dùng chung, ví dụ `permissions.js`, thay vì rải điều kiện chuỗi role ở nhiều nơi.
- TEACHER: trang mặc định `schedule`; ẩn menu, nút đầu trang, gợi ý lỗi/trợ giúp dẫn tới xếp lịch toàn trường; giữ nhập lịch cá nhân.
- ADMIN: trang mặc định `autoschedule`; ẩn menu nhập lịch, nút “Nhập lịch cá nhân”, thẻ gợi ý nhập lịch và mọi đường dẫn tới trang import.
- Chặn cả `navigate()` và điều kiện render trang không có quyền. Khi tài khoản/quyền thay đổi, chuyển về trang mặc định hợp lệ.
- TEACHER không sửa mã GV trong picker hoặc form import. ADMIN vẫn có thể chọn giảng viên ở màn hình xem lịch.
- Header, sidebar và trợ giúp giải thích đúng vai trò; quản lý không dùng nội dung “lịch dạy cá nhân” làm lời giới thiệu mặc định.
- Hàm `api()` gắn token dùng chung cho JSON và FormData. `401` đưa về đăng nhập; `403` báo không có quyền, không báo nhầm là lỗi kết nối.

## 4. Đặc tả file Excel kết quả

### 4.1. Nút tải và nguồn dữ liệu

- Thêm nút **Tải Excel lịch đã xếp** trong vùng kết quả của `AutoSchedulePage`, cạnh thông báo hoàn tất.
- Phân biệt với nút **Mẫu Excel Phân công**: mẫu là đầu vào, file mới là kết quả.
- Chỉ bật khi ADMIN đã có `scheduledClasses` không rỗng, không đang xếp lịch/xuất file.
- Xuất chính kết quả server trả về; không chạy lại thuật toán và không ghi lại database.
- Nếu một số lớp chưa xếp được: vẫn cho tải các lớp đã xếp, giữ cảnh báo trên giao diện và thông báo file chỉ chứa phần đã xếp.
- Chụp context `schoolId`, `schoolName`, `semesterStartDate`, `periods` cùng kết quả từ server. Sửa form sau khi xếp không được làm đổi thông tin file của kết quả trước.
- Tên file đề xuất: `lich-da-xep-{schoolId}-{semesterStartDate}.xlsx`; chuẩn hóa ký tự tên file.
- Phạm vi đầu tiên: xuất kết quả lần xếp lịch đang hiển thị. Chưa bổ sung lịch sử tác vụ hoặc tải lại kết quả sau khi reload.

### 4.2. Sheet MonHoc: 11 cột

Giữ nguyên chín cột mẫu ở A–I, thêm hai cột giảng viên ở J–K. Cách này tránh dịch các cột mà bộ đọc Excel 9 cột hiện tại đang dùng.

| Cột | Tiêu đề | Nguồn/quy tắc |
| --- | --- | --- |
| A | Mã MH | `slot.courseCode`; giữ mã thực nếu có, nếu chưa có dùng đúng mã server đã sinh |
| B | Tên môn học | Tên môn gốc, không ghép thêm “Nhóm ...” vì đã có cột C |
| C | Nhóm tổ | `slot.classGroup`, dạng text để giữ `01`, `02` |
| D | Số tín chỉ | Metadata môn học; không suy ra từ `numPeriods` và không mặc định tất cả là 3 |
| E | Thứ | `dayOfWeek`, số 2–8; 8 là Chủ Nhật |
| F | Tiết bắt đầu | `startPeriod`, số nguyên |
| G | Số tiết | `numPeriods` của giai đoạn tương ứng, không ghi chuỗi “Tiết 1–3” |
| H | Phòng | `room` nguyên bản |
| I | Thời gian học: | Khoảng ngày học thực tế tương ứng các tuần hoạt động, như file mẫu |
| J | Mã GV | `teacherId`, dạng text, không bỏ trống |
| K | Tên GV | `teacherName`, giữ Unicode tiếng Việt |

Mỗi dòng phải điền đầy đủ mã/tên môn, nhóm và mã/tên GV; không gộp ô hoặc dựa vào dòng trước. Không thêm “Ghi chú giai đoạn” hay “Chuỗi tuần” vào cấu trúc MonHoc trong phạm vi yêu cầu này.

### 4.3. Dữ liệu còn thiếu cần bổ sung

- `GeneratedClassSlot.courseName` hiện là tên hiển thị đã ghép nhóm. Bổ sung trường tên môn gốc, ví dụ `baseCourseName`, để export không phải đoán bằng cách cắt chuỗi.
- Số tín chỉ hiện không có trong DTO. Thêm `credits` nullable vào dữ liệu phân công và slot kết quả.
- Đề xuất bổ sung hai cột tùy chọn `Mã MH`, `Số tín chỉ` sau ba cột phân công hiện tại; bảng nhập nhanh thêm hai trường tương ứng. File phân công cũ ba cột vẫn đọc được.
- Với đầu vào cũ không có số tín chỉ, giữ cột D nhưng để ô trống và thông báo thiếu metadata; không tự bịa dữ liệu. Nếu nghiệp vụ yêu cầu cột này luôn đầy đủ, cần bắt buộc người quản lý bổ sung trước khi xuất.
- Trả `semesterStartDate` và bảng tiết đã sử dụng trong `AutoScheduleResponse` để frontend không phụ thuộc ngày/bảng tiết đang sửa trên form.

### 4.4. Giai đoạn đảo tiết và khoảng ngày

- Giữ các slot nửa đầu/nửa sau riêng biệt; không gộp hai giai đoạn thành một dòng nếu khác tiết, số tiết, phòng hoặc chuỗi tuần.
- Với tuần hoạt động `w`, ngày học = `semesterStartDate + (w - 1) * 7 + (dayOfWeek - 2)` ngày. Dùng phép tính ngày không lệch múi giờ.
- Nhận diện tuần hoạt động theo quy tắc đang dùng của hệ thống: ký tự khác `-`/khoảng trắng là có học.
- Một dải tuần liên tiếp tạo một dòng: ngày đầu/ngày cuối là ngày học đúng thứ của slot, định dạng `dd/MM/yy đến dd/MM/yy` như mẫu. Chỉ một buổi thì hai ngày bằng nhau.
- Nếu chuỗi tuần có khoảng nghỉ ở giữa, tách thành nhiều dòng ứng với từng dải liên tiếp để không biểu diễn thành học cả tuần nghỉ.
- Slot không có tuần hoạt động hoặc metadata ngày/tiết không hợp lệ phải được báo rõ; không tạo file sai một cách im lặng.
- Vì có thể tách dải tuần, số dòng Excel có thể lớn hơn số slot. Số lớp vật lý vẫn theo `totalClassesScheduled`, không lấy số dòng file làm số lớp.

Ví dụ: tuần 1 bắt đầu 10/08/2026, thứ 2, chuỗi `11--11` tạo hai dòng khoảng `10/08/26 đến 17/08/26` và `07/09/26 đến 14/09/26`.

### 4.5. Sheet TietHoc và định dạng

- Giữ sheet `TietHoc` với ba cột `period`, `start`, `end`, lấy từ bảng tiết thật của lần xếp lịch; không dùng giờ trong file demo nếu khác cấu hình server.
- Header đậm, freeze hàng đầu, auto-filter, độ rộng cột phù hợp; không merge ô dữ liệu.
- Mã môn, nhóm, mã GV, khoảng ngày là text; thứ/tiết/số tín chỉ có dữ liệu là số. Tên tiếng Việt được giữ nguyên; dữ liệu người dùng ghi thành giá trị text, không công thức Excel.
- Sắp xếp ổn định theo mã GV → mã môn → nhóm → thứ → giai đoạn/tuần đầu → tiết bắt đầu.

### 4.6. Cách triển khai export

- Dùng ExcelJS đang có trong frontend, tách module `scheduleExport.js` với hàm chuyển slot thành dòng và hàm tạo workbook để dễ kiểm tra.
- Quyền truy cập dữ liệu kết quả được backend bảo vệ ở API xếp lịch; download tạo từ kết quả đã được phép nhận. Không thêm API export nhận tùy ý dữ liệu client trong đợt này.
- Tải file bằng Blob/object URL và thu hồi URL sau khi tải. Trạng thái xuất độc lập với trạng thái chạy thuật toán.
- Không cam kết nhập lại toàn bộ file nhiều giảng viên bằng luồng import cá nhân hiện tại: luồng đó nhận một teacherId cho cả file. Nhập lại nhiều giảng viên là chức năng khác. Giữ chín cột đầu chỉ bảo toàn cấu trúc lịch, không đồng nghĩa hỗ trợ nhập lại hàng loạt.

## 5. Các bước triển khai

| Bước | Công việc | Kết quả bàn giao |
| --- | --- | --- |
| 1 | Chuẩn hóa TEACHER/ADMIN, khóa đăng ký ADMIN, thiết kế principal/token và quy tắc sở hữu | Quyền backend có thể kiểm tra độc lập với UI |
| 2 | Thêm token/filter, endpoint me, matcher API và xử lý lỗi 401/403 | Không vượt quyền bằng gọi API trực tiếp |
| 3 | Thêm header xác thực vào api(), điều hướng/menu/nút và trang mặc định theo role | Hai luồng giao diện giảng viên/quản lý rõ ràng |
| 4 | Bổ sung metadata môn học, tên môn gốc, mốc tuần và bảng tiết vào kết quả | Đủ nguồn dữ liệu cho từng cột Excel |
| 5 | Viết module xuất MonHoc/TietHoc, tách dải tuần, tích hợp nút tải | File 11 cột theo mẫu và 2 cột giảng viên |
| 6 | Kiểm tra phân quyền, workbook, Docker/ngrok; cập nhật tài liệu chạy/cấp quyền | Kết quả nghiệm thu và hướng dẫn sử dụng |

Các file dự kiến sửa: `SecurityConfig`, `AuthService`, DTO xác thực, `ScheduleController`, service lịch, filter/token service mới, `AuthPage.jsx`, `App.jsx`, `schedule.js`, `AutoSchedulePage.jsx`, `permissions.js` mới, `scheduleExport.js` mới, DTO phân công/kết quả, `ExcelScheduleService`, `AutoScheduleService`, cấu hình môi trường và tài liệu liên quan. Không sửa thuật toán phân phòng ngoài việc truyền đủ metadata cho export.

## 6. Kiểm thử và nghiệm thu

### Phân quyền

- TEACHER không thấy bất kỳ lối vào xếp lịch toàn trường; ADMIN không thấy lối vào nhập lịch cá nhân.
- TEACHER gọi trực tiếp API xếp lịch trả `403`; ADMIN gọi API import cá nhân trả `403`.
- Thiếu/token giả/hết hạn trả `401`. Đổi role trong localStorage hoặc body request không tăng quyền.
- Đăng ký với role ADMIN bị từ chối; đăng ký bình thường tạo TEACHER. Có tài khoản ADMIN được cấp bằng quy trình quản trị để nghiệm thu.
- TEACHER không đọc/nhập/xóa bằng teacherId người khác; ADMIN vẫn xem lịch giảng viên theo quyền đọc.
- Reload, đăng xuất và chuyển tài khoản không giữ trang/kết quả toàn trường của phiên trước.

### Excel

- Đọc lại workbook bằng ExcelJS: có đúng `MonHoc`, `TietHoc`; MonHoc đúng 11 header theo thứ tự đã quy định, TietHoc đúng ba header.
- Kiểm tra mã GV, tên GV tiếng Việt, mã môn, nhóm `01`, tín chỉ có/thiếu; tên môn không bị lặp hậu tố nhóm.
- Slot đảo 3 ↔ 2 giữ đúng thứ, tiết bắt đầu, số tiết, phòng và ngày của từng giai đoạn.
- Kiểm tra một tuần, Chủ Nhật, dải có tuần nghỉ, mốc qua tháng/năm và timezone; mở rộng các khoảng ngày xuất ra phải tương đương tập buổi gốc, không thêm/mất buổi.
- Sửa trường/ngày trên form sau khi có kết quả không làm đổi file kết quả cũ. Xếp lại thì xuất bộ kết quả mới.
- Không bật tải khi rỗng/đang xử lý; kết quả một phần không xuất lớp chưa xếp. Tên file và nội dung không dùng dữ liệu demo cố định.
- Mở file bằng Excel/LibreOffice để kiểm tra Unicode, cột và định dạng hiển thị.

### Tích hợp

- Kiểm tra cả đường upload file và bảng phân công nhập nhanh.
- Kiểm tra đăng nhập, phân quyền và tải file qua Vite/ngrok và Docker/Nginx, giữ Authorization hoạt động qua proxy.
- Chạy kiểm thử backend cho role/ownership; kiểm thử workbook bằng dữ liệu thật thay vì chỉ mock download; frontend build và Docker build phải thành công.
- Bộ test `schedule.test.js` trên nhánh hiện tại đã có lỗi do mẫu cũ. Ghi nhận baseline và cập nhật các fixture liên quan khi đụng tới định dạng; không báo các lỗi sẵn có là do chức năng mới mà không đối chiếu.

## 7. Những quyết định cần xác nhận khi bắt đầu triển khai

Các mặc định trong kế hoạch để triển khai được, chưa phải yêu cầu bổ sung đã được người dùng chốt:

1. Quản lý dùng role ADMIN; cấm luôn API nhập cá nhân cho ADMIN, thay vì chỉ ẩn menu.
2. Hai cột giảng viên đặt cuối sheet để giữ vị trí chín cột mẫu. Nếu muốn đưa hai cột này lên đầu, phải sửa bộ đọc Excel dùng tên header thay cho vị trí cố định.
3. Số tín chỉ chưa có thì để trống có cảnh báo; không tự suy từ số tiết. Có thể yêu cầu điền đủ nếu cần file hoàn chỉnh về nghiệp vụ.
4. Tải kết quả đang hiển thị; chưa có lịch sử kết quả tồn tại sau reload và chưa có nhập lại hàng loạt nhiều giảng viên.
