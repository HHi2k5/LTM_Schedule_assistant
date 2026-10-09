# Tài liệu yêu cầu hệ thống hỗ trợ lập lịch giảng dạy

## 1. Tổng quan

### 1.1. Mục đích

Hệ thống hỗ trợ nhà trường lập, phân công và quản lý lịch giảng dạy cho toàn bộ học kỳ. Từ kế hoạch giảng dạy của học kỳ, nhân viên phụ trách có thể xếp lịch theo từng tuần, phân công giảng viên và phòng học, kiểm tra xung đột trước khi công bố lịch.

Giảng viên sử dụng ứng dụng phía client để xem lịch đã được xếp, gửi thông tin về khả năng tham gia giảng dạy (đặc biệt với giảng viên thỉnh giảng), gửi yêu cầu xin nghỉ và đăng ký hoặc đề xuất lịch dạy bù.

### 1.2. Phạm vi

Hệ thống gồm:

- **Server (phía nhà trường):** quản lý dữ liệu học kỳ, lớp học phần, giảng viên, phòng học; tiếp nhận thông tin/yêu cầu từ giảng viên; lập và công bố lịch; xử lý nghỉ dạy và dạy bù.
- **Client (phía giảng viên):** cho phép giảng viên đăng nhập, xem lịch cá nhân, gửi thông tin giảng dạy và thực hiện các yêu cầu liên quan đến lịch.

Đây là ứng dụng client–server. Client giao tiếp với server qua API; server lưu trữ dữ liệu tập trung và là nguồn dữ liệu chính thức của lịch.

### 1.3. Đối tượng sử dụng

| Đối tượng | Mô tả |
|---|---|
| Quản trị viên | Quản lý tài khoản, vai trò và cấu hình chung của hệ thống. |
| Nhân viên/điều phối đào tạo | Quản lý học kỳ, kế hoạch giảng dạy, giảng viên, phòng học; lập lịch, xử lý yêu cầu và công bố lịch. |
| Giảng viên cơ hữu | Nhận và theo dõi lịch giảng dạy do nhà trường phân công; gửi yêu cầu nghỉ hoặc dạy bù. |
| Giảng viên thỉnh giảng | Gửi thông tin về môn/lớp được mời dạy và các khung thời gian có thể tham gia để nhà trường xếp lịch; theo dõi lịch và gửi yêu cầu liên quan. |

## 2. Mục tiêu và quy tắc nghiệp vụ

1. Lịch được tổ chức theo học kỳ và có thể xem/điều chỉnh theo từng tuần.
2. Mỗi buổi học được phân công lớp học phần, giảng viên, thời gian và phòng học.
3. Một giảng viên không được có hai buổi dạy trùng thời gian.
4. Một phòng học không được phân cho hai lớp trong cùng khoảng thời gian.
5. Sức chứa và loại phòng phải phù hợp với yêu cầu của lớp học (nếu nhà trường khai báo các thuộc tính này).
6. Thời khóa biểu chính thức chỉ được xem là có hiệu lực sau khi nhân viên có thẩm quyền công bố.
7. Việc gửi yêu cầu xin nghỉ hoặc dạy bù không tự động làm thay đổi lịch chính thức. Yêu cầu cần được điều phối viên duyệt và cập nhật lịch.
8. Giảng viên thỉnh giảng gửi thông tin môn/lớp được mời dạy cùng khung thời gian có thể tham gia. Điều phối viên dùng thông tin đó để lập lịch; việc gửi thông tin không đồng nghĩa lịch đã được xác nhận.
9. Các thay đổi lịch sau khi công bố cần được lưu vết và thông báo đến giảng viên bị ảnh hưởng.

## 3. Yêu cầu chức năng

### 3.1. Tài khoản và phân quyền

- Người dùng đăng nhập và đăng xuất.
- Quản trị viên tạo, cập nhật, khóa hoặc kích hoạt tài khoản.
- Hệ thống phân quyền theo vai trò; giảng viên chỉ xem lịch và yêu cầu của mình, nhân viên điều phối được thao tác dữ liệu lịch trong phạm vi được cấp.
- Hệ thống lưu thông tin giảng viên, bao gồm loại giảng viên (cơ hữu/thỉnh giảng) và thông tin liên hệ cần thiết.

### 3.2. Quản lý dữ liệu học vụ

Nhân viên điều phối có thể:

- Tạo và cập nhật học kỳ, ngày bắt đầu/kết thúc và các tuần học.
- Quản lý môn học, lớp học phần, số lượng sinh viên và số buổi cần học.
- Quản lý giảng viên và phân công/ghi nhận giảng viên phụ trách lớp.
- Quản lý phòng học, sức chứa và loại phòng.
- Nhập hoặc cập nhật kế hoạch giảng dạy làm cơ sở tạo lịch tuần.

### 3.3. Lập và quản lý lịch

- Tạo lịch giảng dạy cho toàn học kỳ từ kế hoạch giảng dạy.
- Tạo, xem và chỉnh sửa lịch theo tuần.
- Phân công thời gian, giảng viên và phòng cho từng buổi học.
- Kiểm tra xung đột giảng viên, phòng và các ràng buộc đã khai báo trước khi lưu hoặc công bố.
- Hiển thị rõ các buổi chưa được xếp hoặc có xung đột để điều phối viên xử lý.
- Cho phép lưu lịch ở trạng thái bản nháp trước khi công bố.
- Cho phép công bố lịch theo học kỳ hoặc theo phạm vi tuần được chọn.
- Cho phép điều chỉnh lịch đã công bố; lưu người sửa, thời điểm sửa và nội dung thay đổi.
- Hỗ trợ tra cứu lịch theo tuần, học kỳ, giảng viên, lớp học phần và phòng học.

### 3.4. Luồng dành cho giảng viên cơ hữu

- Xem lịch cá nhân theo tuần hoặc học kỳ.
- Xem thông tin từng buổi: môn/lớp, ngày giờ, phòng học và trạng thái.
- Gửi yêu cầu xin nghỉ cho một buổi dạy, kèm lý do và thông tin liên quan.
- Theo dõi trạng thái yêu cầu: chờ duyệt, đã duyệt hoặc từ chối.
- Gửi đề xuất dạy bù, bao gồm buổi cần dạy bù và các khung thời gian đề xuất.
- Xem kết quả xếp lịch dạy bù sau khi điều phối viên xử lý.

### 3.5. Luồng dành cho giảng viên thỉnh giảng

- Gửi thông tin môn/lớp học phần được nhà trường mời giảng dạy.
- Khai báo các ngày/khung giờ có thể tham gia trong học kỳ hoặc khoảng thời gian được yêu cầu.
- Cập nhật thông tin trước khi lịch được chốt, theo chính sách của nhà trường.
- Theo dõi trạng thái tiếp nhận thông tin và lịch đã được nhà trường xếp.
- Sử dụng các chức năng xem lịch, xin nghỉ và đề xuất dạy bù như giảng viên cơ hữu sau khi có lịch chính thức.

### 3.6. Xử lý nghỉ dạy và dạy bù

- Nhân viên điều phối xem danh sách yêu cầu, lọc theo trạng thái, giảng viên, học kỳ hoặc tuần.
- Điều phối viên duyệt hoặc từ chối yêu cầu và có thể nhập ghi chú phản hồi.
- Khi duyệt nghỉ, điều phối viên có thể ghi nhận phương án thay thế hoặc đánh dấu buổi học cần xếp dạy bù.
- Điều phối viên xác nhận lịch dạy bù sau khi kiểm tra xung đột giảng viên và phòng.
- Lịch dạy bù được liên kết với buổi học gốc để dễ tra cứu.
- Khi lịch nghỉ/dạy bù được duyệt và cập nhật, giảng viên liên quan nhận được thông báo.

### 3.7. Thông báo

- Thông báo khi lịch được công bố hoặc thay đổi.
- Thông báo khi yêu cầu xin nghỉ/dạy bù được duyệt hoặc từ chối.
- Thông báo khi thông tin giảng dạy của giảng viên thỉnh giảng được tiếp nhận hoặc cần bổ sung.
- Có thể hiển thị thông báo trong ứng dụng; kênh email hoặc thông báo đẩy là tùy chọn triển khai.

## 4. Quy trình nghiệp vụ chính

### 4.1. Lập lịch cho học kỳ

1. Điều phối viên tạo học kỳ và nhập kế hoạch giảng dạy, lớp học phần, giảng viên và phòng học.
2. Giảng viên thỉnh giảng gửi môn/lớp được mời dạy và khung thời gian có thể tham gia.
3. Điều phối viên tạo lịch toàn kỳ hoặc lịch theo từng tuần.
4. Hệ thống kiểm tra xung đột và hiển thị các mục cần xử lý.
5. Điều phối viên hoàn thiện, lưu bản nháp và công bố lịch.
6. Giảng viên xem lịch cá nhân trên client.

### 4.2. Xin nghỉ và xếp dạy bù

1. Giảng viên chọn buổi dạy cần xin nghỉ, nhập lý do và gửi yêu cầu.
2. Điều phối viên xem xét, duyệt hoặc từ chối yêu cầu.
3. Nếu cần dạy bù, giảng viên đề xuất thời gian hoặc điều phối viên lập phương án.
4. Hệ thống kiểm tra xung đột thời gian và phòng.
5. Điều phối viên xác nhận, cập nhật lịch và thông báo cho các bên liên quan.

## 5. Yêu cầu dữ liệu chính

Các nhóm dữ liệu tối thiểu:

- **Tài khoản:** tên đăng nhập, thông tin xác thực được bảo vệ, vai trò, trạng thái.
- **Giảng viên:** thông tin cá nhân/liên hệ, loại giảng viên, đơn vị (nếu có).
- **Học kỳ:** tên, năm học, ngày bắt đầu/kết thúc, tuần học.
- **Môn học và lớp học phần:** mã, tên môn, lớp, sĩ số, số buổi hoặc kế hoạch buổi học.
- **Phòng học:** mã phòng, sức chứa, loại phòng, trạng thái sử dụng.
- **Buổi học:** lớp học phần, giảng viên, phòng, thời gian, trạng thái, liên kết đến buổi gốc nếu là dạy bù.
- **Thông tin khả dụng của giảng viên thỉnh giảng:** học kỳ, lớp/môn liên quan, các khung giờ đề xuất, trạng thái xử lý.
- **Yêu cầu nghỉ/dạy bù:** giảng viên, buổi học, lý do, thời gian đề xuất, trạng thái, người xử lý, ghi chú.
- **Lịch sử thay đổi và thông báo:** đối tượng thay đổi, nội dung, người thực hiện, thời điểm và người nhận.

## 6. Yêu cầu phi chức năng

- **Tương thích:** giao diện client chạy trên trình duyệt hiện đại; thiết kế responsive cho máy tính và thiết bị di động.
- **Bảo mật:** xác thực người dùng; phân quyền phía server; không lưu mật khẩu dạng rõ; chỉ trả về dữ liệu người dùng được phép xem.
- **Toàn vẹn dữ liệu:** các kiểm tra xung đột phải được thực hiện ở server, không chỉ dựa vào giao diện client.
- **Khả dụng:** dữ liệu lịch đã công bố cần được lưu trữ tập trung và có thể truy xuất ổn định.
- **Hiệu năng:** các thao tác xem lịch theo tuần và tra cứu thông thường cần phản hồi trong thời gian phù hợp với quy mô triển khai của nhà trường.
- **Khả năng truy vết:** ghi nhận thao tác quan trọng như công bố/sửa lịch và duyệt yêu cầu.
- **Dễ bảo trì:** tách biệt giao diện client, API server và tầng lưu trữ dữ liệu; API có tài liệu và phiên bản rõ ràng.
- **Sao lưu:** dữ liệu lịch và yêu cầu cần được sao lưu theo chính sách vận hành của nhà trường.

## 7. Định hướng công nghệ và kiến trúc

- **Frontend/client:** React.
- **Backend/server:** có thể sử dụng Java; framework và phiên bản cụ thể sẽ được quyết định khi thiết kế kỹ thuật (ví dụ Spring Boot nếu phù hợp).
- **Giao tiếp:** API HTTP/REST giữa React client và server; dữ liệu trao đổi có thể sử dụng JSON.
- **Cơ sở dữ liệu:** cơ sở dữ liệu quan hệ phù hợp với các quan hệ giữa học kỳ, lớp học phần, buổi học, giảng viên và phòng; hệ quản trị cụ thể sẽ được chọn ở giai đoạn thiết kế.
- **Nguyên tắc:** server chịu trách nhiệm xác thực, phân quyền, kiểm tra quy tắc/xung đột và lưu dữ liệu chính thức. React client không tự quyết định lịch chính thức.

## 8. Ngoài phạm vi phiên bản đầu

Các nội dung sau chưa được xác định là bắt buộc trong phiên bản đầu:

- Tự động tối ưu lịch bằng thuật toán.
- Tích hợp với hệ thống quản lý đào tạo, đăng nhập một lần hoặc lịch cá nhân bên ngoài.
- Gửi SMS, thông báo đẩy hoặc email tự động.
- Chấm công, tính lương và đánh giá giờ giảng.
- Tự động phân công giảng viên hoặc tự động thay đổi lịch khi có xung đột.

## 9. Tiêu chí nghiệm thu mức tổng quát

1. Người dùng đăng nhập và chỉ truy cập được chức năng/dữ liệu phù hợp với vai trò.
2. Điều phối viên tạo được học kỳ, dữ liệu lớp học phần, giảng viên và phòng học.
3. Điều phối viên lập được lịch cho học kỳ và xem/chỉnh sửa lịch theo tuần.
4. Hệ thống phát hiện và ngăn công bố lịch có xung đột giảng viên hoặc phòng.
5. Giảng viên xem được lịch cá nhân đã công bố trên client React.
6. Giảng viên thỉnh giảng gửi được thông tin lớp/môn và khung thời gian khả dụng; điều phối viên xem được để xếp lịch.
7. Giảng viên gửi được yêu cầu xin nghỉ và đề xuất dạy bù; điều phối viên xử lý được các yêu cầu đó.
8. Lịch dạy bù được kiểm tra xung đột, liên kết với buổi học gốc và hiển thị cho giảng viên liên quan sau khi xác nhận.
9. Những thay đổi và quyết định duyệt/từ chối quan trọng có lịch sử và thông báo phù hợp.

## 10. Các điểm cần thống nhất khi thiết kế chi tiết

- Quy tắc thời lượng tiết học, ca học, giờ bắt đầu/kết thúc và thời gian nghỉ giữa các buổi.
- Lịch được công bố theo toàn học kỳ hay có thể công bố từng tuần.
- Người có quyền duyệt nghỉ và dạy bù; thời hạn gửi yêu cầu.
- Giảng viên thỉnh giảng được phép cập nhật khung giờ đến thời điểm nào.
- Cách nhập dữ liệu ban đầu: nhập thủ công, tải tệp hay tích hợp hệ thống khác.
- Hệ quản trị cơ sở dữ liệu, xác thực, kênh thông báo và yêu cầu triển khai/vận hành cụ thể.