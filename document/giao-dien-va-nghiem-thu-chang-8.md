# Chặng 8: màu sắc và nghiệm thu giao diện

Ngày thực hiện: 01/11/2026, nhánh `production`. Tiến độ và kết quả cuối ở [kế hoạch chung](./plan-danh-muc-thinh-giang-va-lich-toan-khoa.md).

## Thay đổi hiển thị

- Dùng bộ màu chung cho đăng nhập, thanh điều hướng, tiêu đề, nút, thẻ số liệu, nhập lịch, danh mục, bản lịch khoa và điều chỉnh.
- Tiêu đề có chuyển sắc nhẹ; panel nền trắng, viền và đổ bóng nhẹ. Danh mục/bản lịch dùng tiêu đề phụ nền trắng để tránh lặp nhiều khối chuyển sắc.
- Điều hướng phân biệt nhóm chức năng bằng biểu tượng có màu; mục đang chọn có nền, viền và trạng thái truy cập hiện hành. Trang Trợ giúp/Cài đặt cũng có trạng thái đang chọn.
- Chữ phụ và trường chỉ đọc đậm hơn; viền input rõ hơn. Nút bị vô hiệu hóa dùng chữ/nền xám đọc được, thay vì giảm toàn bộ opacity.
- Thẻ số liệu dùng nền xanh dương, xanh ngọc, tím và vàng nhạt; số liệu và cảnh báo vẫn ghi bằng chữ.
- Bảng dữ liệu có tiêu đề rõ, zebra/hover nhẹ và cuộn trong vùng riêng. Giữ bố trí giờ thực và các làn buổi học trên lịch cá nhân.
- Cập nhật Trợ giúp theo vai trò, bao gồm danh mục dùng lại, bản nháp, thỉnh giảng, điều chỉnh, xác nhận và lịch sử; luồng Excel cũ vẫn có hướng dẫn.

## Ý nghĩa màu và nhãn

| Nội dung | Màu chữ/nhấn | Cách nhận biết thêm |
|---|---|---|
| Thao tác chính, lý thuyết | Xanh dương `#1D4ED8` | Nhãn LT, tên nút và biểu tượng |
| Hoàn tất, thực hành | Xanh ngọc `#0F766E` | Nhãn TH, trạng thái đã xác nhận/đã lưu |
| Thỉnh giảng | Tím `#6D28D9` | Badge “Thỉnh giảng” |
| Cần xử lý | Nâu cam `#92400E` | Nội dung cảnh báo, số tiết còn thiếu |
| Lỗi/xung đột chặn | Đỏ `#B91C1C` | Biểu tượng cảnh báo, chữ “Trùng lịch”, viền và chi tiết |
| Buổi khóa | Xám đậm `#334155` | Biểu tượng khóa và chữ “Đã khóa” |

Bảng khoa có chú giải LT/TH, thỉnh giảng, trùng lịch và khóa. Một buổi có thể đồng thời có loại LT/TH, trạng thái thỉnh giảng, khóa và xung đột; các nhãn không thay nhau.

Lịch cá nhân có chú giải trường riêng. Màu trường tiếp tục nằm ở viền/nền nhạt và ô màu chú giải; tên môn, phòng và giờ dùng chữ đậm màu để vẫn đọc được khi trường chọn màu trắng, vàng hoặc đen. Tên trường và thông tin buổi vẫn xuất hiện bằng chữ hoặc trong chi tiết.

## Bàn phím và màn hình nhỏ

- Phím Tab đầu tiên có liên kết **Chuyển đến nội dung chính**. Enter đưa focus tới nội dung, bỏ qua các mục điều hướng.
- Nút, liên kết, input/select và summary có viền focus rõ. Nhãn và biểu tượng cảnh báo tiếp tục hiện khi dùng chế độ màu tương phản cao của hệ điều hành.
- Menu mobile giữ focus trong menu; Escape đóng và trả focus về nút mở. Nội dung phía sau tạm ngừng tương tác khi menu mở; đóng menu hoặc hết phiên sẽ khôi phục cuộn trang.
- Hỗ trợ tùy chọn giảm chuyển động của hệ điều hành, tắt transition/animation trang trí.
- Tiêu đề, nhóm nút và footer xuống dòng trên màn hình nhỏ; các thao tác thông thường cao tối thiểu 42px trên mobile, mục điều hướng 44px. Không ép chiều cao này lên các buổi lịch vốn được tính theo thời lượng.
- Bảng khoa dùng danh sách theo ngày trên mobile; bảng rộng khác cuộn trong vùng riêng. Thanh tuần và bộ lọc giữ được chức năng ở 320px.

## Tổ chức mã

- [tokens.css](../frontend/src/tokens.css): màu chữ/nền/trạng thái, viền điều khiển, focus, radius, spacing và shadow dùng chung.
- [theme.css](../frontend/src/theme.css): áp dụng token lên giao diện chung, đăng nhập, lịch cá nhân, menu, trợ giúp; media query mobile, giảm chuyển động và forced colors.
- [catalog.css](../frontend/src/catalog.css), [facultyBoard.css](../frontend/src/facultyBoard.css) và [scheduleAdjustment.css](../frontend/src/scheduleAdjustment.css): dùng chung token với các màn hình tải theo nhu cầu; giữ CSS theo khu vực.
- [App](../frontend/src/App.jsx): trạng thái trang/mục đang chọn, liên kết bỏ qua điều hướng, menu mobile và Trợ giúp theo vai trò.
- [AuthPage](../frontend/src/AuthPage.jsx): giao diện đăng nhập theo bộ màu chung, chuyển phần bố trí chính từ inline style sang CSS.

Đổi hệ màu từ token chung; các màu riêng của trường vẫn thuộc dữ liệu lịch và không dùng làm màu trạng thái toàn ứng dụng.

## Phạm vi nghiệm thu

Kiểm thử bổ sung dùng màu render thực từ trình duyệt, bao gồm nền tổ tiên trong suốt và màu `color-mix`, không chỉ so chuỗi CSS:

| Nhóm | Nội dung kiểm tra |
|---|---|
| Tương phản chữ | Tối thiểu 4,5:1 với nút chính, nút xác nhận bị vô hiệu hóa, chữ trường chỉ đọc, lỗi đăng nhập, nội dung trợ giúp, từng nhãn chú giải khoa và tên môn trên nền màu trường trắng/vàng/đen |
| Viền điều khiển | Tối thiểu 3:1 cho viền input đăng nhập trên nền thực |
| Bàn phím | Liên kết bỏ qua điều hướng, vòng Tab trong menu, Escape/trả focus, focus trên trường chọn tuần |
| Tùy chọn hiển thị | Giảm chuyển động, forced colors và nhãn lỗi/khóa còn hiện |
| Responsive | Desktop 1440px, tablet 768px, mobile 390px và chiều rộng hẹp 320px; trang không tràn ngang, bảng cuộn trong vùng riêng |
| Phiên đăng nhập | Hết phiên khi menu mở trả về đăng nhập và không để lại khóa cuộn trang |
| Hồi quy | Danh mục/import, xếp lịch bằng DB/Excel, lịch cá nhân, bảng khoa/xuất, thỉnh giảng, điều chỉnh/khôi phục và xác thực |

Đây là kiểm chứng các thành phần và luồng được nêu, không phải chứng nhận khả năng truy cập cho mọi nội dung hay mọi trình duyệt. Playwright chạy Chromium desktop/mobile với API giả lập; đã xem ảnh đăng nhập, lịch cá nhân, bảng khoa và trợ giúp. Không chạy ứng dụng Java hoặc áp dụng migration vào database thực trong chặng giao diện.

Kết quả hồi quy cuối được ghi trong nhật ký chặng 8 của [kế hoạch](./plan-danh-muc-thinh-giang-va-lich-toan-khoa.md). Các hướng dẫn nghiệp vụ đã bàn giao:

- [Quản lý danh mục](./danh-muc-huong-dan-chang-2.md).
- [Xếp lịch từ danh mục](./xep-lich-danh-muc-chang-3.md).
- [Bản lịch theo khoa](./ban-lich-theo-khoa-chang-4.md).
- [Bảng toàn khoa](./bang-toan-khoa-chang-5.md).
- [Bổ sung thỉnh giảng](./thinh-giang-sau-xep-lich-chang-6.md).
- [Điều chỉnh và khôi phục](./dieu-chinh-cuc-bo-va-khoi-phuc-chang-7.md).
