# Kế hoạch tinh chỉnh frontend sau bản sửa backend

Ngày: 26/10/2026. Nhánh: `feat/docker-ngrok`.

**Trạng thái:** đã triển khai FE-01–FE-08 theo phạm vi bên dưới trong working tree. Kế hoạch được lập trước khi sửa frontend. Kết quả nghiệm thu và giới hạn thực tế ghi ở mục 7.

**Cập nhật 27/10/2026:** rút FE-04 theo yêu cầu người dùng; đã bỏ trang ADMIN cấp tài khoản giảng viên và API cũ. Các mục đăng ký/danh tính bên dưới đã điều chỉnh theo luồng giảng viên tự đăng ký. Đổi mật khẩu cá nhân trong Cài đặt được giữ nguyên.

Nguồn đối chiếu: [bản sửa backend](backend-fixes.md), code React hiện tại và các API đã sửa trong working tree. Kế hoạch được lập trước khi thay đổi frontend trong đợt này.

## 1. Mục tiêu và phạm vi

Đồng bộ hành vi frontend với backend mới, sửa các luồng dễ gây hiểu nhầm hoặc làm người dùng gửi dữ liệu không hợp lệ, và cải thiện khả năng sử dụng trên desktop/mobile. Tiếp tục dùng giao diện Campus, React, ExcelJS và hệ thống màu/thành phần hiện tại.

Các luồng chính: đăng nhập/tự đăng ký, đổi mật khẩu cá nhân, TEACHER nhập lịch, ADMIN xếp lịch/xuất Excel và xem lịch đã lưu. Mỗi thay đổi cần có trạng thái đang xử lý, thành công, chưa lưu và lỗi phù hợp.

## 2. Vấn đề đã kiểm tra

| Mã | Ưu tiên | Bằng chứng hiện tại | Hướng xử lý |
|---|---|---|---|
| FE-01 | P1 | `excel.js:170`: mẫu chỉ có `ThoiKhoaBieu`, thiếu `TietHoc` mà server bắt buộc. Nhánh đọc 9 cột tại `:145` luôn dùng giờ mặc định. | Mẫu `MonHoc` + `TietHoc`; đọc giờ thực từ file và báo thiếu sheet. |
| FE-02 | P1 | Parser dùng `parseInt` và giới hạn khoảng ngày 16 tuần; validator còn nhận 104 tuần trong khi server tối đa 100. | Parse nghiêm ngặt, tính ngày theo đúng thứ/mốc tuần và đồng bộ giới hạn server. |
| FE-03 | P1 | `AutoSchedulePage.jsx:477`: kết quả nào cũng có dấu thành công; nút xuất vẫn ghi “Hoàn Chỉnh”, nút xem TKB luôn có dù `saved=false`. | Phân biệt rõ đã lưu/chưa lưu/rỗng; khóa xem lịch đã lưu khi chỉ có preview. |
| FE-04 | Đã rút | Chức năng ADMIN cấp tài khoản giảng viên đã bỏ theo yêu cầu ngày 27/10/2026. | Không có trang/menu/API cấp tài khoản. Giảng viên tự đăng ký và cung cấp mã của mình cho trường. |
| FE-05 | P1 | File phòng đang được mô tả là có thể dùng “35 phòng”; backend đã bỏ tự sinh phòng và tìm theo trường. Bảng nhập nhanh lọc bỏ dòng thiếu thay vì báo lỗi. | Kiểm tra danh mục phòng đúng trường, yêu cầu file khi chưa có phòng; kiểm tra toàn bộ dòng đang dùng trước khi gửi. |
| FE-06 | P2 | API giữ `error.status`, nhưng nhiều UI chỉ hiện chung message; `/auth/me` gặp lỗi mạng/500 cũng xóa token. | Xử lý lỗi theo ngữ cảnh; giữ phiên khi lỗi tạm thời, cho thử khôi phục lại. |
| FE-07 | P2 | `App.jsx:220` luôn mở tuần 2 khi xem TKB; nút tuần tiếp chưa giới hạn 100. | Chọn tuần hoạt động đầu của GV trong kết quả, chặn tuần ngoài 1–100. |
| FE-08 | P2 | Mã GV mới dài, nhiều bảng nhập/nút dùng inline style; hướng dẫn còn nhắc MySQL/35 phòng/luồng cũ. | Hiển thị mã đầy đủ kèm sao chép, bố cục linh hoạt và nội dung theo vai trò. |

### Phép kiểm chứng đã chạy

Dùng workbook thật trong bộ nhớ, không sửa source hoặc gọi database:

- `templateWorkbook().worksheets` hiện chỉ trả `ThoiKhoaBieu`.
- Thêm `TietHoc` với tiết 1 `07:20–08:10`, preview vẫn trả `07:00–07:50`.
- Số tiết `1.5` được preview thành `1`, trong khi server từ chối.
- Với mốc 10/08/2026, buổi Thứ Hai trong khoảng 11/08/2026–24/08/2026: frontend trả `111-------------`, server đúng là `-11`. Frontend đang thêm buổi 10/08 ngoài khoảng ngày.

## 3. Thay đổi theo từng luồng

### A. TEACHER nhập lịch — ưu tiên đầu tiên

- Mẫu tải về có `MonHoc` 9 cột và `TietHoc` (`period`, `start`, `end`). Thông tin hướng dẫn yêu cầu điền giờ thực của trường; không dùng giờ mẫu làm dữ liệu ngầm.
- Đọc `MonHoc` theo cấu trúc server chấp nhận và `TietHoc` thật, kể cả khi thứ tự sheet thay đổi. Không chấp nhận định dạng chỉ frontend đọc được nhưng endpoint upload không hỗ trợ.
- Báo lỗi khi thiếu bảng giờ, thiếu thông tin dòng, số âm/số lẻ/sai thứ/ngày hoặc không có tuần hoạt động; ghi rõ sheet/dòng.
- Giới hạn: 100 tuần, tiết 1–30, tối đa 5000 dòng, mốc Thứ Hai trong 2000–2100; giữ cùng mốc lịch đã có. Không tự đổi dữ liệu sai thành giá trị mặc định.
- Preview hiển thị giờ tiết thật; đổi file/metadata phải hủy xác nhận cũ. File không hợp lệ không được giữ ở trạng thái có thể gửi.
- Dùng thông tin người đăng nhập để điền tên/mã GV; mã thuộc tài khoản là chỉ đọc. Phân biệt không có lịch với lỗi khi truy vấn mốc tuần.
- Chỉ chuyển sang lịch sau response lưu thành công, rồi tải lại từ server.

### B. ADMIN xếp lịch và xem kết quả

| Trạng thái | Hiển thị | Hành động |
|---|---|---|
| Đang chạy | Tiến trình xử lý, khóa form và nút gửi | Ngăn gửi lặp |
| `saved=true`, `SUCCESS` | “Đã lưu lịch toàn trường” | Tải Excel lịch đã lưu, xem TKB GV |
| `saved=false`, `NOT_SAVED`, có slot | “Chưa lưu — bản xem trước”, nêu lịch cũ còn nguyên | Tải Excel bản xem trước, chỉnh đầu vào và chạy lại; không mở TKB từ preview |
| `saved=false`, không có slot | “Chưa xếp được lớp”, hiển thị nguyên nhân | Không bật tải Excel/xem TKB |
| HTTP lỗi hoặc response không xác định trạng thái lưu | Thông báo lỗi có ngữ cảnh | Giữ đầu vào, không hiện trạng thái lưu thành công |

- Kết quả gắn với tên/mã trường và mốc tuần trả từ server. Đổi form không đổi nội dung file của kết quả trước; hiển thị thông báo đầu vào đã thay đổi.
- Trình bày số ca học đúng ý nghĩa lý thuyết/thực hành; không dùng số dòng Excel làm số lớp vật lý.
- Tra phòng bằng `GET /rooms?schoolId=...`. Khi không có phòng cho trường, yêu cầu file phòng. Nếu có sẵn, mô tả đúng số phòng của trường đó.
- File `.xlsx` không rỗng, tối đa 10 MB/file, tổng 32 MB. Mốc Thứ Hai, số lớp/sĩ số/tuần/tiết và các dòng nhập nhanh được kiểm tra trước khi tạo FormData.
- Không bỏ âm thầm một dòng đang điền dở; khóa cả bảng nhập nhanh khi chạy/xuất.
- Lấy tuần có hoạt động đầu tiên của GV khi mở TKB, không cố định tuần 2. Điều hướng tuần giới hạn 1–100.

### C. Tự đăng ký và quản lý tài khoản cá nhân

- Form đăng ký mới chỉ gửi username/password/fullName; thông báo server cấp mã GV mới. Không gửi role hoặc teacherId trống trong payload đăng ký.
- Kiểm tra mật khẩu tối thiểu 8 ký tự, tối đa 72 byte UTF-8 khi tạo tài khoản; không áp giới hạn tạo mới vào đăng nhập tài khoản cũ.
- Không có trang, điều hướng hoặc API để ADMIN cấp tài khoản giảng viên. URL cũ bị từ chối; không cung cấp luồng thay thế để nhận hồ sơ có sẵn.
- Giảng viên tự đăng ký rồi gửi mã trên tài khoản cho trường dùng trong danh sách phân công. Hồ sơ phân công chưa có tài khoản không tự được gắn với tài khoản mới.
- ADMIN và TEACHER đổi mật khẩu cá nhân trong Cài đặt bằng mật khẩu hiện tại/mới/xác nhận. Không lưu mật khẩu vào browser storage; xóa form sau thành công.
- Trang tài khoản hiển thị đầy đủ mã GV, nút sao chép và phản hồi khi sao chép thành công/thất bại.

### D. Lỗi API và phiên đăng nhập

| Mã lỗi | Cách xử lý UI |
|---|---|
| 400 | Hiện lỗi dữ liệu; client đánh dấu trường sai khi đã tự xác định được. Không đoán field từ chuỗi message server. |
| 401 trên API được bảo vệ | Xóa phiên, dữ liệu nhạy cảm và đưa về đăng nhập. Sai mật khẩu đăng nhập hiển thị ngay trên form. |
| 403 | Thông báo không đủ quyền, giữ token; không gọi là lỗi kết nối. |
| 404 | Với xem lịch: báo chưa tìm thấy hồ sơ/lịch và kiểm tra lại mã GV. |
| 409 | Báo dữ liệu trùng/đang thay đổi; cho người dùng kiểm tra lại thay vì tự gửi lại thao tác ghi. |
| 413 | Nêu giới hạn file và tổng request. |
| 500 hoặc lỗi mạng | Giữ dữ liệu nhập và phiên đang có; cho thử lại phù hợp ngữ cảnh. |

Khôi phục `/auth/me` bị lỗi mạng/500 cần màn hình thử lại; không xóa token chỉ vì server tạm lỗi. Không tự retry POST/import/xếp lịch khi chưa biết kết quả của lần gửi trước.

### E. Bố cục và hướng dẫn

- Bổ sung thành phần trạng thái có icon + nội dung, không chỉ phân biệt bằng màu. Thông báo lỗi có `role=alert`; trạng thái chờ/thành công dùng live region phù hợp.
- Nhãn/nút thống nhất “Xem trước”, “Đã lưu”, “Chưa lưu”, “Tải Excel”; hướng dẫn theo TEACHER/ADMIN. Tránh đưa tên bảng DB/cổng server vào thao tác thông thường.
- Form và nhóm nút xuống dòng trên mobile; bảng lớn cuộn trong vùng riêng, không kéo tràn toàn trang. Mã GV dài được ngắt dòng hoặc hiển thị cuộn nhưng sao chép đầy đủ.
- Bàn phím dùng được, focus tới lỗi đầu tiên khi gửi form; không tự mở hoặc sao chép thông tin nhạy cảm.

## 4. Thứ tự triển khai và file dự kiến

| Bước | Kết quả | File chính |
|---|---|---|
| 1 | Đồng bộ parser, giới hạn và mẫu Excel; có test tái hiện FE-01/02 | `excel.js`, `scheduleImport.js`, `ImportSchedule.jsx`, `schedule.test.js` |
| 2 | UI kết quả saved/preview/rỗng; kiểm tra đầu vào/phòng; xem đúng tuần | `AutoSchedulePage.jsx`, `App.jsx`, helper workflow mới |
| 3 | Đăng ký gọn, hiển thị mã GV và đổi mật khẩu cá nhân; bỏ chức năng cấp tài khoản của ADMIN | `AuthPage.jsx`, `App.jsx`, `TeacherIdentity.jsx`, `ChangePasswordForm.jsx`, `permissions.js` |
| 4 | Xử lý lỗi/khôi phục phiên nhất quán; hoàn thiện responsive/hướng dẫn | `schedule.js`, `App.jsx`, `styles.css`, các form liên quan |
| 5 | Kiểm thử luồng UI, build và cập nhật hướng dẫn | test domain hiện có, browser tests mới, tài liệu frontend |

Không cần thêm endpoint backend cho phạm vi này. Các thao tác dùng API đang có; kiểm tra quyền thực vẫn thuộc server.

## 5. Kiểm thử và nghiệm thu

### Kiểm thử dữ liệu

- Workbook sinh từ mẫu mới đọc được bằng parser frontend và importer backend; giữ nguyên giờ `07:20`.
- Chặn `1.5`, `-2`, `abc`, ngày không tồn tại, sheet giờ thiếu, tiết ngoài bảng và tuần >100.
- Trường hợp Thứ Hai 11/08–24/08/2026 tạo đúng `-11`; bổ sung Chủ Nhật, một ngày, khoảng nghỉ và đổi tháng/năm.
- Thay metadata/file làm vô hiệu preview và xác nhận trước đó.
- Response saved/NOT_SAVED/rỗng, các mã lỗi API và quyền ADMIN/TEACHER có test riêng.

### Kiểm thử tương tác

Dùng browser test cho các luồng quan trọng, có thể mock API theo contract thật để không cần ghi dữ liệu dev: TEACHER nhập file hợp lệ/không hợp lệ, ADMIN nhận NOT_SAVED rồi sửa và chạy lại, không còn menu cấp tài khoản, đổi mật khẩu cá nhân, phiên hết hạn và `/auth/me` lỗi tạm thời. Dùng Playwright cho các luồng này; giữ `node:test` cho logic và workbook hiện có.

- NOT_SAVED không hiển thị dấu đã lưu, không gọi callback lưu, không mở TKB; Excel preview mang nhãn rõ ràng.
- Cả ADMIN và TEACHER không truy cập trang cấp tài khoản. API cũ bị từ chối; đăng ký không cho nhận hồ sơ có sẵn. Đổi mật khẩu cá nhân vẫn hoạt động cho hai vai trò.
- Kiểm tra desktop và màn hình 390px; không tràn trang, nút/label đọc được và thao tác bằng bàn phím.
- Chạy `npm.cmd test`, browser tests và `npm.cmd run build`. Chỉ chạy lại backend khi kiểm tra contract hoặc khi có thay đổi backend thật sự.

## 6. Cách bàn giao

Ghi rõ các thay đổi hành vi, kết quả test/build, ảnh chụp các trạng thái quan trọng và giới hạn còn lại nếu có. Giữ nguyên các bản sửa backend và cấu hình cục bộ đang tồn tại. Đợt tinh chỉnh này không tự commit/push hoặc áp dụng migration vào database đang dùng.

## 7. Kết quả triển khai ngày 26/10/2026

- Mẫu cá nhân có MonHoc + TietHoc trống để điền dữ liệu thực. Preview không dùng giờ mặc định; kiểm tra số nguyên, ngày tồn tại, giờ thực (kể cả giây), giới hạn 100 tuần/30 tiết/5000 dòng. Khoảng Thứ Hai 11/08–24/08/2026 trả đúng `-11`.
- ADMIN phân biệt đã lưu/preview/rỗng, xuất preview có tiền tố riêng, giữ ngữ cảnh response khi đổi đầu vào. Chỉ mở lịch GV sau khi lưu, tại tuần hoạt động đầu tiên. Danh mục phòng truy vấn đúng trường; nhập nhanh không bỏ qua dòng thiếu và khóa sửa trong lúc gửi/xuất.
- Đăng ký gửi đúng ba trường công khai, kiểm tra mật khẩu UTF-8. Trang tài khoản có mã đầy đủ/nút sao chép và đổi mật khẩu cá nhân. Trang cấp tài khoản ADMIN từng được triển khai ngày 26/10 đã gỡ ngày 27/10 theo yêu cầu.
- Lỗi 401 xóa phiên, 403 giữ phiên; khôi phục phiên lỗi mạng/500 có thử lại và giữ token. Form báo lỗi có focus và live region. Hoàn thiện xuống dòng trên mobile và cuộn bảng trong vùng riêng.
- Bỏ plugin React không dùng có peer dependency xung đột Vite 6; tải trang ADMIN/ExcelJS khi cần. Bundle chính từ khoảng 1.26 MB xuống khoảng 286 KB trước gzip; chunk ExcelJS riêng vẫn khoảng 940 KB và còn cảnh báo kích thước build.

### Bằng chứng kiểm thử

| Lệnh | Kết quả |
|---|---|
| `npm.cmd test` | 21/21 pass, không bỏ qua |
| `npm.cmd run test:browser` | 18/18 pass: 9 luồng trên Chromium desktop 1440×1000 và mobile 390×844 |
| `npm.cmd run build` | Pass; còn cảnh báo chunk ExcelJS lớn |

Browser tests dùng API mock theo contract thật, không ghi database. Bao phủ đăng ký, UTF-8, nhập file sai/đúng, vô hiệu preview, NOT_SAVED/export/xem đúng tuần sau lưu, kết quả rỗng, phòng theo trường, dòng nhập thiếu, không còn chức năng cấp tài khoản, đổi mật khẩu, khôi phục phiên 500 và hết hạn 401. Kiểm tra không tràn chiều ngang và chụp ảnh các trạng thái quan trọng. Unit/API test bao phủ 400/401/403/404/409/413/500.

Ảnh được sinh trong [test-results](../frontend/test-results/) khi chạy test, ví dụ `personal-preview.png`, `auto-preview.png`, `auto-empty.png`, `teacher-profile.png`, `admin-help.png`, `password-settings.png`, `restore-error.png` trong thư mục từng ca desktop/mobile. Báo cáo trong [playwright-report](../frontend/playwright-report/). Cả hai thư mục không đưa vào Git.

### Giới hạn đã xác nhận

- Endpoint Excel xếp lịch tự động hiện dùng 16 tuần và bảng 12 tiết; nhập cá nhân hỗ trợ 100 tuần và giờ thực tới 30 tiết. UI kiểm tra theo từng luồng.
- API lịch tuần không có cờ hồ sơ đã có lịch ở tuần khác. Nếu tuần 1 rỗng, UI nêu mốc hiện có và server kiểm tra mốc khi lưu; không khẳng định hồ sơ chưa có lịch. Chưa thêm endpoint/backend field trong đợt này.
- Chưa chạy Safari/Firefox. Kiểm thử browser dùng API mock; luồng đổi mật khẩu đã kiểm tra bổ sung trên Docker thật bằng tài khoản tạm, sau đó xóa tài khoản đó. Các kiểm thử MySQL/backend của đợt trước được giữ nguyên.
- `npm audit` hiện báo 2 mục moderate ExcelJS/uuid; đề xuất tự động là hạ major ExcelJS. Chưa thay thư viện/hạ major trong phạm vi tinh chỉnh này.

Hướng dẫn thao tác cập nhật tại [frontend/README.md](../frontend/README.md). Không commit/push trong đợt này.

## 8. Điều chỉnh theo yêu cầu ngày 27/10/2026

- Bỏ FE-04: xóa trang cấp tài khoản, menu, quyền điều hướng, API/service và quy tắc cho phép gọi API cũ. Giảng viên tự đăng ký; trường dùng mã trên tài khoản khi phân công. Các tài khoản đã tồn tại và đổi mật khẩu cá nhân được giữ nguyên.
- Thay kiểm thử cấp tài khoản bằng kiểm thử không còn menu/trang, URL cũ bị từ chối cho ADMIN/TEACHER, đăng ký vẫn không nhận hồ sơ có sẵn.
- Thêm đồng hồ `HH:mm:ss` bên cạnh ngày ở góc phải thanh trên cùng, cập nhật mỗi giây theo `Asia/Ho_Chi_Minh`. Ngày chuyển đúng lúc nửa đêm; đồng hồ vẫn hiện trên mobile.
- Tài liệu sử dụng/yêu cầu/backend/quyền/Docker đã được đồng bộ theo luồng hiện tại. Bằng chứng mục 7 mô tả đợt 26/10; bộ browser test hiện có 24 lượt (12 luồng × desktop/mobile).

Kiểm chứng sau điều chỉnh: 22 test backend liên quan xác thực/phân quyền/đổi mật khẩu và 21 test dữ liệu frontend pass; 22 lượt browser test cho các luồng hiện có và 2 lượt kiểm tra đồng hồ qua nửa đêm đều pass. Build frontend/backend Docker thành công. Kiểm tra Docker thật xác nhận API cũ trả 403 cho ADMIN, không còn menu cấp tài khoản, đồng hồ chạy và Cài đặt vẫn có đổi mật khẩu; tài khoản tạm được xóa sau kiểm tra. Cả ba container healthy, tài khoản/dữ liệu đang có được giữ nguyên.
