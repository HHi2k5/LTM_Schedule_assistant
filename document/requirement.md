# Tài liệu yêu cầu hệ thống hỗ trợ quản lý lịch giảng dạy

## 1. Tổng quan

### 1.1. Mục đích

Giảng viên tải file Excel (.xlsx) hoặc JSON (.json) chứa lịch dạy đã được nhà trường phân công. Hệ thống đọc lịch, lưu theo từng trường và tổng hợp thành lịch cá nhân theo tuần. Giảng viên có thể theo dõi lịch của nhiều trường và nhận cảnh báo khi các buổi dạy trùng giờ.

Hệ thống không yêu cầu khai báo thời gian khả dụng để tự lập lịch từ đầu. Môn học, phòng học, thứ, tiết và tuần dạy được lấy từ lịch đã có; hệ thống quy đổi số tiết thành giờ thực tế theo cấu hình của từng trường.

### 1.2. Phạm vi

- **Client:** tải file Excel hoặc JSON, bổ sung thông tin giảng viên/trường và mốc tuần còn thiếu, xem trước dữ liệu nhập, xem lịch tuần và cảnh báo trùng lịch.
- **Server:** tiếp nhận dữ liệu lịch theo trường, lưu bảng giờ tiết và môn học, tính ngày/giờ từng buổi trong tuần được chọn, phát hiện xung đột và trả lịch tổng hợp.
- **Mở rộng sau luồng nhập/xem lịch:** xác thực, phân quyền, yêu cầu nghỉ dạy, dạy bù và thông báo.

Tài liệu bám theo mô hình và API backend hiện có. Các phần đọc trực tiếp Excel, xem trước và kiểm tra dữ liệu là yêu cầu cần hoàn thiện, không phải chức năng đã có đầy đủ.

### 1.3. Người sử dụng

Giảng viên cơ hữu và thỉnh giảng dùng cùng luồng nhập lịch đã được phân công. Một giảng viên có thể có nhiều trường; mỗi trường có màu hiển thị, bảng giờ tiết và danh sách môn/lớp riêng trong lịch của giảng viên đó.

Cập nhật 27/10/2026: giảng viên tự đăng ký tài khoản TEACHER và nhận mã GV mới từ server. ADMIN quản lý phân công/xếp lịch, không tạo tài khoản giảng viên trên web hoặc qua API. Khi phân công, nhà trường dùng đúng mã GV trên tài khoản giảng viên cung cấp. Tài khoản đã tồn tại và chức năng đổi mật khẩu cá nhân trong Cài đặt được giữ nguyên.

## 2. Quy tắc nghiệp vụ

1. Lịch được lưu theo giảng viên và từng trường, phù hợp quan hệ `Teacher → School → SchoolPeriod / TeachingCourse` của backend.
2. Mỗi môn/lớp có thứ học, tiết bắt đầu, số tiết liên tiếp và chuỗi tuần học. Nếu có nhiều khung học khác nhau, biểu diễn thành các dòng lịch riêng.
3. Quy ước thứ: `2` là Thứ Hai, `3` là Thứ Ba, …, `8` là Chủ Nhật.
4. Tuần được đánh số từ `1`. Mốc `semesterStartDate` là ngày Thứ Hai của tuần 1; ngày này hiện được lưu chung ở giảng viên, không riêng từng trường.
5. Khi nhập lịch từ nhiều trường, phải quy đổi tuần của từng trường về cùng mốc tuần của giảng viên. Không gửi một mốc mới làm lệch lịch các trường đã lưu mà không có bước đối chiếu.
6. `weekSchedule` là chuỗi biểu diễn các tuần: ký tự ở vị trí `weekIndex - 1` quyết định tuần có học hay không. Backend hiện coi dấu `-` hoặc khoảng trắng là không học; các ký tự khác là có học. Chuỗi ngắn hơn tuần được truy vấn được hiểu là không có buổi trong tuần đó.
7. Tiết kết thúc được tính bằng `startPeriod + numPeriods - 1`. Giờ bắt đầu lấy từ tiết đầu, giờ kết thúc lấy từ tiết cuối trong bảng giờ của trường.
8. Phát hiện trùng lịch bằng ngày và giờ thực tế, kể cả giữa các trường khác nhau. Hai buổi chỉ tiếp giáp giờ kết thúc/bắt đầu không bị coi là trùng.
9. Xung đột được đánh dấu để hiển thị, không tự đổi giờ, đổi phòng hoặc xóa buổi dạy.
10. Nhập lại cùng `teacherId` và mã trường sẽ thay toàn bộ bảng giờ tiết và danh sách môn của trường đó. Lịch các trường khác được giữ lại. Giao diện phải thông báo phạm vi thay thế trước khi gửi.
11. Luồng hiện tại không lưu nhiều phiên bản học kỳ cho một giảng viên. Quản lý lịch sử học kỳ cần mở rộng mô hình riêng nếu bổ sung sau này.
12. Lịch nhập là lịch do giảng viên cung cấp; hệ thống không có bước tự lập, phê duyệt hoặc công bố lịch ban đầu thay nhà trường.

## 3. Yêu cầu chức năng

### 3.1. Tải và đọc file Excel hoặc JSON

- Thay chức năng khai báo thời gian khả dụng bằng **Nhập lịch dạy**.
- Nhận file `.xlsx` theo cấu trúc được hỗ trợ; mẫu sheet/cột cụ thể cần được chốt từ file thực tế.
- Nhận thêm file `.json` chứa một đối tượng `SchoolUploadRequest`: `teacherId`, `teacherName`, `semesterStartDate`, `school` (kèm `periods`) và `courses`, đúng cấu trúc tại mục 3.2.
- Cung cấp mẫu tải về riêng cho Excel và JSON. File JSON tự điền thông tin giảng viên/trường, mốc tuần và màu; nếu cần sửa thông tin, sửa file rồi chọn lại.
- Hai định dạng dùng chung kiểm tra ngày, giờ, số tiết, chuỗi tuần và bước xác nhận thay thế. Không tự đổi mốc tuần trong JSON sang mốc khác đã lưu; phải báo lỗi để người dùng sửa dữ liệu.
- Đọc mã/tên môn hoặc lớp, phòng học, thứ, tiết bắt đầu, số tiết và các tuần có học.
- Đọc thông tin trường, bảng giờ tiết và mốc tuần từ file nếu có; cho phép bổ sung khi file thiếu.
- Nếu file dùng ngày cụ thể hoặc số tuần theo mốc riêng của trường, chuẩn hóa về thứ và chuỗi tuần theo mốc chung của giảng viên.
- Hiển thị bản xem trước dữ liệu chuẩn hóa, tên trường, mốc tuần và phạm vi lịch sẽ được thay thế.
- Báo lỗi theo dòng/cột khi thiếu trường bắt buộc, sai thứ, tiết/số tiết không hợp lệ, tuần không xác định hoặc không có giờ tiết tương ứng.
- Không tự đoán giờ khi thiếu bảng tiết; yêu cầu bổ sung cấu hình trước khi nhập.
- Cho phép hủy để sửa file; chỉ gửi dữ liệu hợp lệ sau khi giảng viên xác nhận.
- Khi đọc file hoặc lưu thất bại, thông báo rõ và giữ lịch đang có.
- Sau khi lưu thành công, tải lịch tuần từ server để hiển thị dữ liệu đã nhập.

### 3.2. Lưu lịch theo trường

Dữ liệu sau khi đọc Excel hoặc JSON được chuẩn hóa theo `SchoolUploadRequest`:

| Trường dữ liệu | Ý nghĩa |
|---|---|
| `teacherId` | Mã giảng viên sở hữu lịch. |
| `teacherName` | Tên giảng viên, dùng khi tạo mới theo backend hiện tại. |
| `semesterStartDate` | Ngày Thứ Hai của tuần 1, dùng chung cho lịch của giảng viên. |
| `school.schoolId` | Mã trường, dùng cùng mã giảng viên để xác định lịch cần cập nhật. |
| `school.schoolName` | Tên trường. |
| `school.colorCode` | Màu phân biệt trường trên lịch. |
| `school.periods` | Danh sách `period`, `start`, `end`: số tiết, giờ bắt đầu và giờ kết thúc. |
| `courses` | Danh sách dòng lịch môn/lớp của trường. |

Mỗi phần tử `courses` có cấu trúc `TeachingCourseDTO`:

| Trường dữ liệu | Ý nghĩa |
|---|---|
| `courseCode` | Mã môn/lớp từ file. |
| `courseName` | Tên môn học. |
| `room` | Phòng học. |
| `dayOfWeek` | Thứ học, từ 2 đến 8. |
| `startPeriod` | Tiết bắt đầu. |
| `numPeriods` | Số tiết liên tiếp. |
| `weekSchedule` | Chuỗi tuần học đã chuẩn hóa theo mốc chung. |

Backend tìm hoặc tạo giảng viên, sau đó tìm hoặc tạo trường thuộc giảng viên. Nếu trường đã có, dữ liệu tiết và môn của trường được thay toàn bộ trong một giao dịch. Đây là gộp lịch giữa các trường; không phải bổ sung từng dòng vào lịch cũ của cùng trường.

### 3.3. Tổng hợp lịch theo tuần

- Nhận `teacherId` và `weekIndex` để truy vấn.
- Tính ngày Thứ Hai bằng `semesterStartDate + (weekIndex - 1) tuần` và ngày Chủ Nhật bằng ngày Thứ Hai cộng 6 ngày.
- Duyệt các trường và môn/lớp của giảng viên; chỉ lấy các dòng có học trong tuần được chọn.
- Tính ngày học từ thứ và ngày Thứ Hai của tuần; lấy giờ thực tế từ bảng tiết của trường.
- Kiểm tra trùng giờ, sau đó sắp xếp buổi theo ngày và giờ bắt đầu.
- Trả `WeekScheduleResponse` gồm mã giảng viên, số tuần, ngày đầu/cuối tuần và danh sách `lessons`.
- Mỗi `LessonSlot` gồm trường, màu trường, tên môn, phòng, thứ, ngày, tiết đầu/cuối, giờ đầu/cuối, `isConflict` và `conflictReason`.

### 3.4. Hiển thị lịch

- Hiển thị lịch tuần hoặc danh sách các buổi dạy từ dữ liệu backend.
- Chuyển tuần phải tải đúng lịch của tuần được chọn; không lặp dữ liệu mẫu ở mọi tuần.
- Phân biệt lịch các trường bằng tên và màu trường.
- Cột trái hiển thị **Tiết** và số tiết; thẻ buổi dạy hiển thị giờ thực tế và thông tin trường/môn/phòng.
- Vị trí và chiều cao thẻ tính theo giờ thực tế, chính xác đến phút. Buổi bắt đầu 7:10 hoặc 7:20 phải thấp hơn buổi bắt đầu 7:00 theo tỷ lệ thời gian.
- Khi các trường có bảng tiết khác nhau, dùng cùng thang thời gian để định vị thẻ; số tiết gốc của từng trường được giữ trong chi tiết buổi dạy. Quy ước số tiết tham chiếu trên cột trái cần được thống nhất khi hoàn thiện giao diện.
- Không mặc định mọi tiết dài 60 phút; sử dụng giờ trong cấu hình trường.
- Làm nổi bật buổi có `isConflict = true` và hiển thị lý do từ backend.
- Hiển thị trạng thái trống khi chưa nhập lịch hoặc tuần đó không có buổi dạy.

### 3.5. Kiểm tra dữ liệu cần hoàn thiện

- Kiểm tra mã giảng viên, mã/tên trường, mốc tuần và danh sách tiết/môn trước khi ghi dữ liệu.
- `weekIndex` phải từ 1 trở lên; mốc tuần phải là ngày Thứ Hai.
- Thứ từ 2 đến 8, tiết bắt đầu và số tiết phải dương; các tiết tham chiếu phải tồn tại trong bảng tiết.
- Mỗi tiết có giờ kết thúc sau giờ bắt đầu, không trùng số tiết; khoảng tiết của một buổi phải quy đổi được thành thời gian hợp lệ.
- Không nhận `weekSchedule` rỗng hoặc chứa ký hiệu ngoài quy ước nhập được hỗ trợ.
- Kiểm tra dữ liệu phía server, kể cả khi client đã kiểm tra.
- Việc thay mốc tuần chung phải được kiểm soát vì ảnh hưởng lịch của mọi trường thuộc giảng viên.

### 3.6. Chức năng mở rộng

Các chức năng sau chưa nằm trong luồng backend lịch hiện có, triển khai sau khi nhập/xem lịch hoạt động:

- Mở rộng vai trò điều phối khi có yêu cầu; xác thực TEACHER/ADMIN và ràng buộc mã tài khoản đã được cập nhật theo mục 1.3.
- Gửi và duyệt yêu cầu nghỉ dạy, đề xuất dạy bù liên kết với buổi gốc.
- Thông báo khi lịch thay đổi hoặc yêu cầu được xử lý.
- Lịch sử file nhập, thay đổi lịch và nhiều học kỳ.

## 4. Quy trình chính

### 4.1. Nhập lịch lần đầu

1. Giảng viên chọn file Excel hoặc JSON lịch dạy của một trường.
2. Hệ thống đọc file, bổ sung thông tin trường và bảng giờ tiết còn thiếu.
3. Xác định ngày Thứ Hai của tuần 1 và chuẩn hóa chuỗi tuần học.
4. Hiển thị dữ liệu xem trước và lỗi; giảng viên kiểm tra rồi xác nhận.
5. Gửi dữ liệu chuẩn hóa vào luồng lưu lịch theo trường.
6. Server lưu dữ liệu; client truy vấn tuần cần xem và hiển thị lịch trả về.

### 4.2. Thêm lịch trường khác

1. Giảng viên tải file của trường tiếp theo và xác định mã trường khác.
2. Dùng mốc tuần hiện có của giảng viên; quy đổi tuần học của trường mới về mốc đó.
3. Lưu thêm trường cùng bảng giờ tiết và môn/lớp của trường.
4. Tải lại lịch tuần tổng hợp từ tất cả các trường; hiển thị cảnh báo nếu trùng giờ.

### 4.3. Cập nhật lịch một trường

1. Giảng viên tải file mới và chọn đúng trường cần cập nhật.
2. Hiển thị thông báo thay toàn bộ bảng tiết và danh sách môn của trường đó.
3. Giữ mốc tuần chung hoặc xử lý đối chiếu rõ ràng nếu cần thay đổi.
4. Sau khi xác nhận, server cập nhật trong giao dịch; lịch trường khác được giữ nguyên.
5. Client tải lại lịch tuần và cảnh báo xung đột theo dữ liệu mới.

## 5. Mô hình dữ liệu theo backend

| Thành phần | Dữ liệu và quan hệ |
|---|---|
| `Teacher` | `teacherId`, `fullName`, `semesterStartDate`; có nhiều `School`. |
| `School` | Mã trường, tên trường, màu; thuộc một giảng viên; có nhiều tiết và môn/lớp. |
| `SchoolPeriod` | Số tiết, giờ bắt đầu/kết thúc; thuộc một trường. |
| `TeachingCourse` | Mã/tên môn, phòng, thứ, tiết bắt đầu, số tiết, chuỗi tuần học; thuộc một trường. |
| `LessonSlot` | Dữ liệu buổi dạy được tính cho tuần truy vấn, gồm ngày/giờ và cảnh báo xung đột; là DTO trả về, không phải bảng buổi học riêng hiện có. |

Backend hiện chưa có thực thể học kỳ độc lập, tài khoản đăng nhập, file nhập, lịch sử phiên bản hoặc yêu cầu nghỉ/dạy bù. Các phần này cần bổ sung mô hình nếu triển khai.

## 6. API và kiến trúc

### 6.1. API hiện có

| API | Vai trò |
|---|---|
| `POST /api/schedule/upload-school` | Nhận JSON `SchoolUploadRequest`, tạo/cập nhật lịch của một trường thuộc giảng viên. |
| `GET /api/schedule/week/{weekIndex}?teacherId={teacherId}` | Trả lịch tuần tổng hợp dạng `WeekScheduleResponse`. |

`upload-school` hiện nhận `@RequestBody` JSON, chưa nhận trực tiếp file Excel. Tên API không có nghĩa đã có bộ đọc file. Client cần truyền mã giảng viên cụ thể; giá trị mặc định `GV01` hiện tại chỉ phục vụ luồng thử nghiệm.

### 6.2. Luồng đọc Excel cần bổ sung

- Thêm bước nhận file `.xlsx` và đọc bằng Apache POI, thư viện đã được khai báo trong backend.
- Chuẩn hóa dữ liệu thành `SchoolUploadRequest` rồi tái sử dụng luồng lưu `saveOrUpdateSchoolSchedule`.
- Hỗ trợ trả dữ liệu xem trước và lỗi trước khi gọi bước lưu; không lưu ngay chỉ vì đã tải file lên.
- API nhận file/xem trước cần được định nghĩa khi chốt cấu trúc Excel; không thay đổi ý nghĩa API lưu JSON hiện có.

### 6.3. Công nghệ

- Frontend: React.
- Backend: Java/Spring Boot, Spring Data JPA.
- Cơ sở dữ liệu: MySQL theo cấu hình dự án.
- Trao đổi dữ liệu: JSON cho lịch; multipart/form-data cho bước nhận file cần bổ sung.

## 7. Yêu cầu phi chức năng

- Lưu lịch tập trung để không mất khi tải lại trang hoặc đổi thiết bị.
- Đọc/hiển thị đúng tiếng Việt và mã hóa UTF-8.
- Kiểm tra file và dữ liệu phía server; giới hạn dung lượng và số dòng phải được quy định khi triển khai nhận file.
- Khi thao tác lưu thất bại, giao dịch không được để lại lịch cập nhật dở dang.
- Định vị buổi dạy theo phút thực tế, giữ đúng số tiết theo trường.
- Giao diện responsive, có trạng thái đang đọc file, lỗi và nhập thành công.
- Hoàn thiện xác thực/phân quyền trước khi dùng với nhiều tài khoản thực tế.

## 8. Ngoài phạm vi luồng chính

- Thu thập thời gian khả dụng và tự lập lịch giảng dạy từ đầu.
- Tự phân công môn, phòng hoặc tự giải quyết xung đột bằng cách chuyển giờ.
- Quy trình nhà trường lập bản nháp và công bố lịch ban đầu.
- Đọc PDF, ảnh hoặc OCR; hỗ trợ mọi mẫu Excel tùy ý khi chưa có quy tắc đọc.
- Chấm công, tính lương, email và tích hợp hệ thống đào tạo bên ngoài.

## 9. Tiêu chí nghiệm thu

1. File `.xlsx` hoặc `.json` đúng mẫu được đọc thành dữ liệu xem trước; xác nhận nhập mới lưu vào hệ thống.
2. Dữ liệu nhập được lưu đúng giảng viên, trường, bảng tiết và danh sách môn/lớp.
3. Chuyển tuần hiển thị đúng các buổi được xác định bằng `weekSchedule`, đúng thứ/ngày/giờ/phòng.
4. Lịch của nhiều trường hiển thị chung và không bị lệch do mốc tuần khác nhau.
5. Nhập lại một trường thay đúng dữ liệu của trường đó, giữ lịch trường khác.
6. Hai buổi giao nhau về thời gian trong cùng ngày có cảnh báo; hai buổi tiếp giáp thì không.
7. Cột trái hiển thị số tiết; buổi bắt đầu 7:10 hoặc 7:20 được đặt thấp hơn buổi bắt đầu 7:00 theo đúng tỷ lệ.
8. Thiếu cấu hình tiết hoặc file sai dữ liệu được báo lỗi, không âm thầm thay giờ và không làm mất lịch cũ.
9. Lịch được tải lại từ backend sau khi nhập và vẫn tồn tại khi mở lại ứng dụng.
10. Giảng viên nhập và xem lịch mà không cần khai báo thời gian khả dụng.

## 10. Các điểm cần chốt và giới hạn hiện tại

- Cần file Excel mẫu để xác định sheet, tiêu đề, ô gộp, cách ghi môn/lớp và tuần học trước khi viết bộ đọc.
- Cần chốt cách cung cấp bảng giờ tiết và quy đổi mốc tuần giữa các trường.
- Cần chốt trục số tiết tham chiếu khi hiển thị lịch nhiều trường có giờ tiết khác nhau.
- Backend hiện dùng giờ dự phòng 07:00/11:00 nếu không tìm thấy tiết. Đây là giới hạn cần sửa bằng kiểm tra và báo lỗi để đạt yêu cầu độ chính xác.
- Backend chưa kiểm tra đầy đủ trường bắt buộc, mốc tuần, số tuần và dữ liệu tiết/môn; cần hoàn thiện theo mục 3.5.
- `semesterStartDate` hiện có thể được cập nhật khi nhập một trường, ảnh hưởng mọi trường; cần kiểm soát khi tích hợp nhập nhiều trường.
- Khi cùng một buổi trùng với nhiều buổi khác, backend hiện chỉ giữ một chuỗi `conflictReason`; nếu cần liệt kê tất cả xung đột phải mở rộng dữ liệu trả về.
- Lưu file gốc, lịch sử nhập, quản lý nhiều học kỳ và nghỉ/dạy bù là phần mở rộng, chưa được mô hình hiện tại hỗ trợ.
