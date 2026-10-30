# Frontend lịch giảng dạy

Chạy tại thư mục frontend:

```powershell
npm.cmd install
npm.cmd run dev
```

Bật backend và MySQL trước. Trong `.env.local` để `VITE_API_BASE_URL=`; Vite chuyển tiếp `/api` tới `http://127.0.0.1:8080`. Khi dùng ngrok chạy `ngrok http 5173` và thêm đúng hostname vào `server.allowedHosts`. File `.env.local` không được commit.

Thanh trên cùng có đồng hồ `HH:mm:ss` cạnh ngày, dùng múi giờ Việt Nam (UTC+7) và tự cập nhật mỗi giây. Ngày cũng cập nhật khi qua nửa đêm. Trên màn hình nhỏ, đồng hồ vẫn hiển thị khi nhãn ngày được ẩn.

## Tài khoản và quyền

Giảng viên tự đăng ký tài khoản (TEACHER) và nhận mã mới do server sinh. Giảng viên chỉ nhập/xem/xóa lịch của mình; quản lý (ADMIN) xếp lịch toàn trường, xem lịch giảng viên và tải Excel. Backend kiểm tra token và quyền.

Từ 27/10/2026 đã bỏ chức năng ADMIN cấp tài khoản giảng viên, gồm trang giao diện và API cũ. Dùng mã GV trên tài khoản đã đăng ký trong danh sách phân công để giảng viên xem đúng lịch. Hồ sơ chưa có tài khoản không tự được liên kết khi một người đăng ký mới. Tài khoản đã tồn tại và chức năng đổi mật khẩu cá nhân được giữ nguyên.

Đã kiểm tra lại sau khi gỡ: 22 test backend liên quan xác thực/quyền/đổi mật khẩu, 21 test frontend và 24 lượt browser test đều pass. Trên Docker thật, API cũ từ chối ADMIN (403), menu đã gỡ, đồng hồ chạy và đổi mật khẩu cá nhân vẫn hiển thị. Tài khoản tạm dùng để kiểm tra đã được xóa.

Xem [hướng dẫn cấp quyền quản lý và phiên đăng nhập](../document/phan-quyen-va-xuat-excel.md). Sau khi nâng cấp từ phiên bản cũ cần đăng nhập lại.

## Đổi mật khẩu

ADMIN và giảng viên mở **Cài đặt → Đổi mật khẩu**. Nhập mật khẩu hiện tại, mật khẩu mới (ít nhất 8 ký tự, tối đa 72 byte UTF-8) và xác nhận khớp. Mật khẩu mới cần khác mật khẩu đang dùng. Form xóa cả ba ô sau khi đổi thành công.

API `POST /api/schedule/auth/change-password` yêu cầu đăng nhập, chỉ nhận `currentPassword` và `newPassword`; tài khoản cần đổi lấy từ token. Sai mật khẩu hiện tại trả 400 để giữ phiên, mật khẩu mới lưu bằng BCrypt. Các phiên JWT đã cấp vẫn hoạt động tới khi hết hạn; tính năng này chưa thu hồi phiên trên thiết bị khác.

Kiểm chứng ngày 27/10/2026: 23 test backend về xác thực/phân quyền/đổi mật khẩu, 21 test frontend và 22 lượt browser test đều pass. Đã kiểm tra thêm luồng đổi mật khẩu trên Docker thật bằng tài khoản tạm: mật khẩu cũ bị từ chối, mật khẩu mới đăng nhập được, form được xóa và phiên hiện tại còn dùng được. Tài khoản tạm được xóa sau kiểm tra; tài khoản người dùng hiện có không đổi mật khẩu.

## Nhập lịch cá nhân

File `.xlsx` không rỗng, tối đa 10 MB, bắt buộc có:

- `MonHoc`: chín cột theo thứ tự Mã MH, Tên môn học, Nhóm tổ, Số tín chỉ, Thứ, Tiết bắt đầu, Số tiết, Phòng, Thời gian học.
- `TietHoc`: ba cột `period`, `start`, `end`. Điền giờ thực của trường theo `HH:mm` (hoặc `HH:mm:ss`). Mẫu để trống, không tự điền giờ mặc định. Mọi tiết dùng trong lịch phải được khai báo.

Mốc tuần 1 phải là Thứ Hai trong năm 2000–2100. Thời gian học nhận chuỗi tối đa 100 tuần, hoặc khoảng ngày `dd/MM/yyyy - dd/MM/yyyy`; khoảng ngày chỉ kích hoạt buổi học đúng thứ nằm trong khoảng. Giới hạn 1–30 tiết và 1–5000 dòng lịch. Đọc file, kiểm tra giờ/tuần rồi xác nhận gửi tới `/api/schedule/upload-excel-teacher`. Đổi file hoặc thông tin trường sẽ hủy xác nhận cũ.

Nhập lại cùng mã trường sẽ thay lịch của trường đó thuộc giảng viên đang đăng nhập. Giữ mốc tuần 1 của lịch đang có để đồng bộ các trường. API lịch tuần chưa cung cấp cờ “đã có lịch ở tuần khác”; khi tuần 1 rỗng, server sẽ kiểm tra ràng buộc mốc lúc lưu và báo lỗi nếu khác mốc đã dùng. Frontend không coi tuần rỗng là hồ sơ mới.

Lịch tuần, thống kê và cảnh báo trùng giờ lấy từ backend. Cột **Tiết** hiện đầy đủ các số tiết trong `schools[].periods` của API lịch tuần, kể cả tiết không có lớp; tải lại trang hoặc đổi thiết bị không cần nhập lại file để có bảng tiết. Chọn **Tiết tham chiếu** để dùng bảng giờ của trường khác; vị trí các buổi dạy vẫn theo giờ thực tế. Bảng giờ trả về từ server được ưu tiên hơn bản nhớ cũ trên thiết bị. localStorage chỉ dự phòng khi kết nối phiên bản backend cũ chưa trả trường `schools`.

## Xếp lịch và tải kết quả

Luồng ba file xếp đủ tổng tiết LT/TH theo đề cương, trong khoảng ngày cụ thể của từng dòng kế hoạch. Số tiết mỗi buổi có thể thay đổi giữa các tuần. LT và TH có thể do hai GV khác nhau phụ trách; các ngày trong tuần dạy LT/TH của cùng GV được tách riêng. Kiểm tra xung đột GV/phòng/lớp-nhóm theo ngày và giờ thực tế, kể cả lịch GV tại trường khác. Mỗi nhóm TH học đủ khối lượng TH của môn, không chia số tiết cho số nhóm.

Quản lý tải mẫu mới từ giao diện, hoặc dùng ba bảng nhập nhanh:

- Kế hoạch: STT, Mã môn, Tên học phần, Sĩ số, Khóa, Số lớp mở, Tuần bắt đầu — từ ngày, Tuần kết thúc — đến ngày, Số tín chỉ, Tổng tiết LT, Tổng tiết TH. Ngày nhận `dd/MM/yyyy`, `yyyy-MM-dd` hoặc ô ngày Excel thực. Tín chỉ và cả hai tổng tiết bắt buộc; ghi 0 cho phần không học. Môn 3 tín có thể khai 45/0, 30/30 hoặc 30/45 theo đề cương; không ép tổng tiết bằng tín chỉ × 15.
- Kế hoạch chỉ có 11 cột trên. Việc chia buổi, phân bổ tuần và chia nhóm TH do thuật toán xử lý; không đưa các cột cấu hình này vào file Excel hoặc bảng nhập nhanh. Nhóm TH dựa vào sức chứa LAB thực tế: 50 SV có LAB 50 chỗ học chung; LAB lớn nhất 30 chỗ thì chia hai nhóm 25 SV. Không áp giới hạn mặc định 30 SV/nhóm; nếu có quy định giới hạn riêng của học phần thì vẫn phải tuân theo.
- Giảng viên bắt buộc: Mã môn, Tên học phần, Khóa, Mã GV, Họ tên giảng viên, Trọng số LT, Trọng số TH. Chia số lớp LT giữa các GV theo trọng số LT, chia số nhóm TH theo trọng số TH, làm tròn phần dư lớn nhất cho tổng nhu cầu cùng môn/khóa qua mọi dòng kế hoạch. Trọng số 0 không nhận loại đó; thiếu dữ liệu phải sửa, không tự sinh GV. Môn/khóa khác nhau có thể có bộ trọng số khác nhau.
- Phòng: giữ mẫu cũ; có thể bỏ file khi danh mục đúng trường đã được kiểm tra và có dữ liệu.

Chạy lại kế hoạch thay các dòng `WEEKLY_AUTO` của toàn trường, dọn lịch tự xếp của GV rút khỏi phân công và giữ các dòng nhập riêng/trường khác. Bản chụp khối lượng, khoảng ngày và các trọng số được lưu trong `plan_snapshot` của dòng lịch. Giới hạn 100 tuần từ mốc tuần 1, 5000 lớp/nhóm và 100000 tiết mỗi lần; thuật toán tìm kiếm có giới hạn riêng từng lớp/nhóm và trả xem trước nếu chưa xếp đủ. Tra cứu xung đột theo tài nguyên/ngày; một lớp chạm giới hạn không làm các nhóm phía sau bị bỏ qua. Mẫu ba file cũ thiếu ngày/tổng tiết/trọng số phải chuyển sang mẫu mới. API một file phân công cũ vẫn giữ hợp đồng riêng.

- **Đã lưu lịch** (`saved=true`, `SUCCESS`): có thể xuất Excel và mở lịch giảng viên tại tuần hoạt động đầu tiên.
- **Bản xem trước — chưa lưu** (`saved=false`, `NOT_SAVED`): lịch cũ được giữ nguyên, nút xem lịch đã lưu bị khóa. File xuất có tiền tố `xem-truoc-chua-luu-`; cần kiểm tra cảnh báo rồi sửa đầu vào.
- Không xếp được buổi nào: hiện cảnh báo và khóa xuất Excel.

Excel kết quả có `MonHoc` gồm chín cột lịch và hai cột Mã GV/Tên GV, cùng `TietHoc`; luồng mới có thêm `PhanCongGiangVien` và `KhoiLuongLopNhom`. Các đoạn khác số tiết/phòng/GV được giữ riêng; ngày xuất của luồng mới có đủ bốn chữ số năm. Giao diện chỉ hiển thị lịch kết quả với bộ lọc tuần/ngày; đã bỏ hai bảng phân công theo trọng số và khối lượng theo đề cương. Khi có lỗi hoặc thiếu tiết, hiển thị một cảnh báo ngắn gọn, kèm số lớp/nhóm và tổng tiết còn thiếu nếu có. Thông tin trường/mốc tuần lấy từ response lần chạy. Luồng ba file dùng bảng 12 tiết và suy ra phạm vi tuần từ khoảng ngày; nhập lịch cá nhân hỗ trợ bảng giờ thực tới 30 tiết và 100 tuần.

Schema mới bổ sung loại buổi, định danh lớp/nhóm và bản chụp kế hoạch qua migration `V3__weekly_planner_metadata.sql`. Môi trường dùng Flyway cần bật `DB_MIGRATIONS_ENABLED=true` khi triển khai; môi trường dev dùng Hibernate update sẽ tự thêm cột. Chưa chạy migration lên DB dev chỉ để kiểm thử; các kiểm thử lưu dùng DB H2 riêng.

Lỗi mạng/500 khi khôi phục phiên sẽ giữ token và có nút thử lại. API bảo vệ trả 401 sẽ đưa về đăng nhập; 403 giữ phiên và báo thiếu quyền. Không tự gửi lại thao tác ghi khi chưa biết kết quả lần trước.

## Kiểm tra

```powershell
npm.cmd test
npx.cmd playwright install chromium
npm.cmd run test:browser
npm.cmd run build
```

Cập nhật xếp lịch theo đề cương: 29 kiểm thử dữ liệu/API và 10 lượt kiểm thử trình duyệt liên quan xếp lịch (desktop 1440px/mobile 390px) đã qua. Backend có 112 kiểm thử qua, 11 ca MySQL/Testcontainers chưa bật; có ca mẫu mặc định trên bản sao lịch Docker đã ẩn danh, ca nhiều lịch không liên quan làm hết giới hạn tìm kiếm trên mã cũ và ca chia nhóm theo LAB thực tế. Kiểm thử trình duyệt dùng API mock theo contract để không ghi DB dev; kiểm thử lưu backend dùng H2 riêng. Ảnh và trace khi lỗi được sinh trong `test-results/`, báo cáo HTML trong `playwright-report/` (được gitignore). Chạy trình duyệt bằng Chromium; chưa kiểm tra Safari/Firefox.

Đã bỏ dependency `@vitejs/plugin-react` không được cấu hình sử dụng và có peer dependency xung đột Vite 6. Trang ADMIN/ExcelJS được tải theo nhu cầu; chunk ExcelJS vẫn lớn. `npm audit` còn báo 2 mục moderate liên quan ExcelJS/uuid; chưa áp dụng đề xuất hạ major ExcelJS vì cần đánh giá tương thích riêng.

Xem [kế hoạch và kết quả tinh chỉnh frontend](../document/frontend-refinement-plan.md), [bản sửa backend](../document/backend-fixes.md). Để chạy toàn bộ ứng dụng trong Docker, xem [DOCKER.md](../DOCKER.md).
