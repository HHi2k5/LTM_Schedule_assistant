# Frontend lịch giảng dạy

```powershell
npm.cmd install
npm.cmd run dev
```

Chạy backend ở cổng 8080 cùng cơ sở dữ liệu đã cấu hình trong server. Vite chuyển tiếp `/api` đến `http://127.0.0.1:8080`. Khi triển khai ở địa chỉ khác, đặt `VITE_API_BASE_URL` theo `.env.example` và cấu hình CORS/xác thực tương ứng ở backend.

## Nhập lịch

1. Chọn **Nhập lịch dạy → Mẫu Excel** hoặc **Mẫu JSON**.
2. Thay các dòng ví dụ bằng lịch thực tế. Với Excel, giữ tên hai sheet `MonHoc`, `TietHoc` và tiêu đề cột; sheet `HuongDan` giải thích từng trường.
3. Với Excel, điền mã/tên giảng viên, mã/tên trường, mốc Thứ Hai của tuần 1 và màu trường. Với JSON, các thông tin này tự điền từ file; sửa file và chọn lại nếu cần thay đổi.
4. Chọn file `.xlsx` hoặc `.json` (tối đa 5 MB), đọc và xem trước. Kiểm tra bảng môn và bảng giờ tiết rồi xác nhận lưu.
5. Lịch được gửi tới `POST /api/schedule/upload-school` dưới dạng JSON. Sau khi lưu thành công, giao diện lấy lịch bằng `GET /api/schedule/week/{weekIndex}?teacherId=...` và chuyển tới tuần đầu có học trong file.

Backend hiện chưa nhận file Excel trực tiếp nên frontend đọc file bằng ExcelJS và chuẩn hóa theo DTO có sẵn. Chỉ hỗ trợ cấu trúc file mẫu; chưa đọc mọi mẫu lịch riêng của từng trường. Các lỗi được kiểm tra phía frontend; backend vẫn cần bổ sung validation để bảo vệ API khi gọi trực tiếp.

File JSON chứa một đối tượng `SchoolUploadRequest`: `teacherId`, `teacherName`, `semesterStartDate`, `school` (gồm `schoolId`, `schoolName`, `colorCode`, `periods`) và `courses`. JSON dùng số nguyên cho thứ và tiết, chuỗi cho ngày/giờ và tuần. Hai định dạng dùng chung kiểm tra ngày, giờ, tiết và chuỗi tuần, cùng bước xác nhận lưu. Mốc tuần trong JSON khác mốc đã lưu sẽ bị báo lỗi, không tự thay thế.

`weekSchedule` nên dùng `x` hoặc `1` cho tuần học, `-` cho tuần nghỉ. Đồng thời hỗ trợ chữ/số đánh dấu tuần có học và khoảng trắng đánh dấu tuần nghỉ theo backend; giữ nguyên khoảng trắng đầu/cuối để không lệch tuần. Ví dụ `xxxx-xxxx` học tuần 1–4 và 6–9. Mọi trường dùng chung mốc tuần 1 của giảng viên. Nếu lịch trường dùng mốc khác, cần quy đổi chuỗi tuần trước khi nhập. Với mã giảng viên đã có lịch, giao diện lấy mốc từ backend và khóa trường nhập ngày này.

Nhập lại cùng mã giảng viên và mã trường **thay toàn bộ** bảng tiết và môn của trường đó. Giao diện yêu cầu xác nhận trước khi gửi. Nếu backend không xác nhận được mã giảng viên tồn tại, chỉ tiếp tục khi người dùng xác nhận đó là mã mới; backend hiện chưa phân biệt lỗi không tìm thấy với lỗi máy chủ bằng mã HTTP riêng.

## Hiển thị

Lịch tuần, danh sách, thống kê và xung đột lấy từ backend, không dùng lịch mẫu. Các buổi giao nhau được đặt cạnh nhau; vị trí/chiều cao dựa trên giờ thực tế. Cột trái là số tiết của trường tham chiếu được chọn.

Backend chưa có API lấy đầy đủ cấu hình trường. Vì vậy bảng tiết tham chiếu được nhớ ở trình duyệt sau khi nhập thành công. Trên thiết bị chưa nhập file, cột trái chỉ hiện các tiết bắt đầu có trong lịch tuần; giờ, ngày và buổi dạy vẫn lấy từ backend.

Mã giảng viên là lựa chọn dữ liệu, chưa phải đăng nhập. Các luồng yêu cầu nghỉ/dạy bù và thông báo mẫu đã được bỏ khỏi điều hướng chính vì chưa có API hỗ trợ.

## Kiểm tra

```powershell
npm.cmd test
npm.cmd run build
```

Kiểm tra gồm đọc/ghi XLSX thật, lỗi file và tham chiếu tiết, bảo vệ mốc tuần, bố trí buổi trùng giờ, vị trí theo phút và hợp đồng gọi API. Kiểm tra kết nối với backend thực cần server và MySQL đang chạy.
