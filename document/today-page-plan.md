# Tách Tổng quan thành trang Hôm nay

## Mục tiêu

Trang Tổng quan đang dùng chung giao diện với Lịch giảng dạy. Thay nội dung đó bằng trang Hôm nay dành cho giảng viên, giúp xem nhanh buổi sắp tới, lịch trong ngày và việc cần chú ý. ADMIN dùng trang Xếp lịch toàn trường làm điểm vào và không có menu Tổng quan/Hôm nay.

## Thiết kế và dữ liệu

- Đổi nhãn Tổng quan thành Hôm nay; chỉ giảng viên truy cập được. Giữ menu Lịch giảng dạy để xem/chuyển tuần, lọc, xuất lịch.
- Component Hôm nay tải lịch tuần hiện tại theo mã tài khoản đăng nhập và mốc học kỳ. Luồng dữ liệu riêng, độc lập với tuần/tìm kiếm/bộ lọc đang chọn trên lịch.
- Ngày, giờ, chuyển ngày và chuyển tuần đều tính theo Asia/Ho_Chi_Minh. Cập nhật thời gian và trạng thái khi trang đang mở; làm mới dữ liệu khi sang ngày, trở lại tab hoặc bấm tải lại.
- Buổi nổi bật: ưu tiên buổi đang dạy; nếu chưa đến giờ thì hiện buổi kế tiếp trong tuần, ngày/giờ/phòng/trường và thời gian còn lại. Nếu nhiều buổi đang dạy, hiển thị số buổi đồng thời và cảnh báo để người dùng đối chiếu.
- Lịch hôm nay: danh sách theo giờ, trạng thái chưa bắt đầu/đang dạy/đã kết thúc, chạm/bấm để mở hộp chi tiết hiện có.
- Tóm tắt: số buổi hôm nay, số buổi chưa kết thúc trong tuần, số giờ còn lại (buổi đang dạy chỉ tính phần chưa diễn ra), số buổi trùng giờ chưa kết thúc.
- Việc cần chú ý: các buổi trùng giờ chưa kết thúc trong tuần hiện tại, nút mở lịch đúng tuần để đối chiếu.
- Ngày không có lịch, tuần trống, lỗi mạng, đang tải và ngày ngoài phạm vi học kỳ có nội dung riêng; không biến lỗi tải thành số liệu bằng 0.
- Dùng API tuần hiện có. Phạm vi buổi kế tiếp và thống kê giới hạn trong tuần hiện tại; không suy đoán lịch của tuần sau từ tuần trống.

## Thực hiện

- [x] Đọc điều hướng, phân quyền, API tuần, đồng hồ và test hiện có.
- [x] Tạo hàm tính ngày/tuần và tổng hợp lịch theo giờ Việt Nam, kiểm tra các mốc thời gian.
- [x] Triển khai trang Hôm nay, điều hướng và phân quyền; nối hộp chi tiết và nút xem lịch tuần.
- [x] Kiểm tra desktop/mobile: độc lập tuần đã chọn, buổi đang dạy/sắp tới, giờ còn lại, cảnh báo, ngày trống, lỗi/thử lại, qua nửa đêm và ADMIN không có menu.
- [x] Build, cập nhật frontend Docker và kiểm tra với dữ liệu thật.

## Tiêu chí nghiệm thu

1. Hôm nay không hiển thị lại lưới lịch tuần; ngày/tuần đang xem trong Lịch giảng dạy không làm sai dữ liệu Hôm nay.
2. Trạng thái buổi học và giờ còn lại đúng tại thời điểm bắt đầu/kết thúc, nửa đêm và Chủ Nhật sang Thứ Hai theo giờ Việt Nam.
3. Danh sách/cảnh báo mở được chi tiết hoặc lịch đúng tuần; bộ lọc cũ không che dữ liệu được mở từ Hôm nay.
4. ADMIN không có menu và không được điều hướng tới Hôm nay; chức năng xem lịch giảng viên của ADMIN vẫn dùng được.
5. Giao diện nhỏ không tràn ngang, có trạng thái tải/lỗi/trống rõ ràng.

## Kiểm tra và kết quả

Hoàn tất ngày 27/10/2026.

- [TodayDashboard](../frontend/src/TodayDashboard.jsx) có luồng tải độc lập, trạng thái tải/lỗi/thử lại, làm mới khi trở lại tab và tự theo ngày hiện tại. Giữ ID điều hướng `overview`, đổi nhãn thành Hôm nay; quyền truy cập chỉ dành cho TEACHER trong [permissions.js](../frontend/src/permissions.js). Điểm vào sau đăng nhập vẫn là Lịch giảng dạy cho TEACHER và Xếp lịch toàn trường cho ADMIN.
- [today.js](../frontend/src/today.js) tính tuần theo mốc học kỳ và giờ Việt Nam, ưu tiên buổi đang dạy, loại buổi đã kết thúc khỏi thời gian/cảnh báo còn lại. Sau khi nhập lịch và lưu mốc mới, [App.jsx](../frontend/src/App.jsx) cập nhật ngay mốc trên tài khoản đang dùng để Hôm nay không lấy tuần theo mốc cũ.
- 4/4 test Node đạt: phân quyền/điểm vào; nửa đêm, Chủ Nhật sang Thứ Hai và ngoài phạm vi học kỳ; giờ còn lại/trạng thái tại biên bắt đầu-kết thúc; buổi đồng thời, ngày trống và hết lịch tuần.
- 20/20 lượt Playwright đạt trên Chromium desktop 1440×1000 và mobile 390×844 (18 kiểm tra điều hướng/lịch/Hôm nay/quyền ADMIN, cộng 2 kiểm tra cập nhật mốc học kỳ sau nhập Excel). Các test thời gian được cố định theo giờ Việt Nam; test nhập Excel chỉ cố định Date và để timer tiếp tục chạy cho việc đọc workbook.
- Build Vite và build frontend trong Docker thành công; container frontend healthy. `git diff --check` đạt. Cảnh báo chunk ExcelJS lớn đã có từ trước.
- Đã đối chiếu trực tiếp localhost:8088 với API/database thật bằng tài khoản test trên desktop và mobile: tuần hiện tại là 12; hôm nay không có buổi dạy, còn 2 buổi trong tuần và 3.8 giờ; buổi tiếp theo là Lập trình mạng ngày 28/10/2026. Hộp chi tiết mở được, nút Xem lịch tuần mở đúng tuần 12, trang không tràn ngang. Đã xem ảnh kết quả cả hai kích thước.
- Các lỗi nhập Excel có sẵn sau merge BEv1 được ghi ở [kế hoạch thẻ lịch ngắn](calendar-short-lesson-plan.md). Không chạy lại toàn bộ bộ test nhập Excel trong tác vụ này; phần kiểm tra nhập lịch mới tập trung vào đồng bộ mốc học kỳ với Hôm nay.
