# Kế hoạch cải thiện thẻ buổi dạy ngắn

## Vấn đề và mục tiêu

Buổi một tiết có chiều cao khoảng 51–62 px theo trục giờ thực tế. Tên môn dài đang chiếm hết thẻ, làm khuất phòng, trường và thông tin tiết. Cần đọc nhanh được giờ/phòng, đồng thời xem đầy đủ thông tin bằng chuột, bàn phím và cảm ứng.

## Phương án

1. Giữ nguyên vị trí, chiều cao theo phút và cách chia cột khi trùng giờ.
2. Tách phần hiển thị thành component thẻ lịch. Đo kích thước thực bằng ResizeObserver để chọn bố cục:
   - Thẻ đủ cao/rộng: giữ thông tin hiện có.
   - Thẻ ngắn: dòng giờ + phòng, tên môn tối đa hai dòng có dấu ba chấm; ẩn tên trường trên thẻ.
   - Thẻ hẹp do chia cột: sắp giờ/phòng theo chiều dọc, ưu tiên phần tên còn vừa.
   - Buổi cực ngắn: ưu tiên tên môn; thông tin đầy đủ có trong popup và hộp chi tiết.
3. Thêm popup khi rê chuột hoặc focus. Hiển thị tên môn/nhóm, trường, ngày, giờ, tiết, phòng và cảnh báo trùng giờ. Dùng portal ra body để tránh bị cắt bởi khung cuộn; giới hạn trong viewport. Cho phép di chuột sang popup, đóng bằng Escape và đóng khi cuộn/đổi kích thước.
4. Bấm/chạm/Enter/Space mở hộp chi tiết sẵn có. Giữ tên truy cập đầy đủ cho mọi thẻ và chỉ báo focus rõ ràng.

## Trình tự thực thi

- [x] Khảo sát component lịch, CSS và test trình duyệt hiện có.
- [x] Triển khai bố cục theo kích thước và popup.
- [x] Kiểm tra trình duyệt: một tiết tên dài, buổi cực ngắn, lớp trùng giờ/chia hẹp, popup tại mép màn hình, bàn phím, thao tác chạm và modal.
- [x] Build frontend và chạy các test lịch liên quan trên desktop/mobile.
- [x] Cập nhật frontend Docker tại localhost:8088 và kiểm tra dữ liệu thật.

## Tiêu chí nghiệm thu

- Thẻ một tiết vẫn có giờ/phòng, tên môn được rút gọn có chủ ý.
- Popup đầy đủ, không bị khung lịch cắt hoặc tràn viewport; Escape đóng popup khi thẻ còn focus.
- Hộp chi tiết mở được và trả focus về thẻ khi đóng.
- Ca liền nhau không bị che, vị trí/kích thước trên trục giờ không đổi; cảnh báo trùng giờ vẫn thấy.
- Mobile không tràn trang; thẻ chạm được để xem chi tiết.

## Phạm vi kiểm tra

Chỉ sửa phần trình bày lịch và tài liệu/test liên quan. Sau lần merge BEv1 trước đó, bộ test dữ liệu frontend đang có 5 lỗi và backend có 1 lỗi liên quan nhập Excel; chúng nằm ngoài thay đổi này. Dùng test lịch có API mock để kiểm tra trường hợp biên, và lịch mẫu đã nhập để kiểm tra bản Docker.

## Kết quả

Hoàn tất ngày 27/10/2026.

- Component [CalendarLesson](../frontend/src/CalendarLesson.jsx) đo độ rộng thẻ, tính số dòng tên môn theo chỗ còn lại, giữ giờ/phòng ở đầu thẻ và chỉ báo trùng giờ. Ca một tiết giữ đúng chiều cao theo phút; thẻ cực ngắn dùng tên rút gọn và thông tin truy cập đầy đủ. Thẻ nhiều tiết tận dụng chiều cao cho tên môn (tối đa sáu dòng), giữ tên trường và số tiết kể cả khi cột hẹp.
- Popup dùng portal, tự đổi vị trí và giới hạn trong viewport; hỗ trợ di chuột sang popup, focus, Escape, cuộn và resize. Việc mở được chờ qua bước tự cuộn khi focus/hover thẻ ngoài màn hình. Popup đóng khi mở modal, giúp Escape đóng hộp chi tiết đúng cách.
- Build Vite thành công, bao gồm build trong image Docker. Cảnh báo kích thước chunk ExcelJS vẫn có từ trước.
- 8/8 lượt Playwright đạt (4 trường hợp × desktop/mobile): trục tiết sau reload/chuyển trường; thẻ 50 phút với ca liền nhau và click/tap; popup ở mép viewport với focus/Enter/Escape/hover/cuộn; lớp trùng giờ chia hẹp, resize và buổi 15 phút với Space mở chi tiết. Chạy tuần tự bằng một worker sau khi máy thiếu bộ nhớ khi chạy đồng thời build/test; runner tái sử dụng Vite đang mở.
- Đã cập nhật riêng frontend Docker, container báo healthy. Kiểm tra thêm bằng Chromium trên localhost:8088 với tài khoản test và API/database thật ở kích thước desktop 1440×1000 và mobile 390×844: ca trưa 12:00–13:00 hiện phòng 505-NT, popup desktop đầy đủ, click/tap mở chi tiết và Escape đóng modal.
- Diagnostics các file thay đổi không có lỗi; `git diff --check` đạt. Các lỗi nhập Excel đã ghi trong phạm vi kiểm tra không được sửa trong tác vụ này.
