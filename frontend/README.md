# Frontend lịch giảng dạy

Hướng dẫn chạy đầy đủ, database và cấp quyền ADMIN nằm trong [README gốc](../README.md). Frontend dùng React 19, Vite 6 và ExcelJS; yêu cầu Node.js 22.12+ cùng npm.

## Chạy dev

Bật backend và MySQL trước, sau đó chạy tại thư mục `frontend`:

```powershell
npm.cmd ci
Copy-Item .env.example .env.local
npm.cmd run dev
```

Chỉ sao chép `.env.local` nếu chưa có file này. Mở http://127.0.0.1:5173. Giữ `VITE_API_BASE_URL=` trống; Vite proxy `/api` tới `BACKEND_PROXY_TARGET` (mặc định http://127.0.0.1:8080). Đổi proxy target nếu backend dùng cổng khác, rồi restart Vite. Không commit `.env.local`.

`npm.cmd run build` tạo thư mục `dist`. Docker/Nginx phục vụ bản build và proxy API, bao gồm SSE realtime. `npm.cmd run preview` không có cấu hình API proxy của Vite dev.

## Tài khoản và realtime

- Giảng viên tự đăng ký, nhận mã GV do server cấp và quản lý lịch cá nhân. Khi phân công, dùng đúng mã tài khoản đó.
- ADMIN xếp lịch khoa, quản lý phòng/phân công và tải Excel. Cấp ADMIN trong database theo [README](../README.md#bước-4-tạo-tài-khoản-và-cấp-admin); không có tài khoản ADMIN mặc định.
- ADMIN và GV đổi mật khẩu cá nhân trong Cài đặt. Mật khẩu cần ít nhất 8 ký tự, tối đa 72 byte UTF-8.
- Token ở sessionStorage; backend kiểm tra quyền cho từng API. HTTP 401 kết thúc phiên, HTTP 403 báo thiếu quyền và giữ phiên.
- Khi admin thay đổi lịch, frontend tự đọc dữ liệu mới, giữ tuần và bộ lọc. Trang Hôm nay cũng cập nhật; mất mạng sẽ tự kết nối lại. Sau nâng cấp frontend cần tải lại trang một lần. Xem [luồng realtime](../document/realtime-schedule.md).

## Nhập lịch cá nhân

File `.xlsx` không rỗng, tối đa 10 MB, có hai sheet:

| Sheet | Các cột theo thứ tự |
| --- | --- |
| `MonHoc` | Mã MH, Tên môn học, Nhóm tổ, Số tín chỉ, Thứ, Tiết bắt đầu, Số tiết, Phòng, Thời gian học |
| `TietHoc` | `period`, `start`, `end` |

Tải mẫu từ giao diện. Điền giờ thực của trường dạng `HH:mm` hoặc `HH:mm:ss`; mọi tiết trong lịch phải có trong `TietHoc`. Mốc tuần 1 là Thứ Hai trong năm 2000–2100. Thời gian học nhận chuỗi tuần tối đa 100 tuần hoặc khoảng `dd/MM/yyyy - dd/MM/yyyy`. Giới hạn 1–30 tiết và 1–5000 dòng lịch.

Đọc file, kiểm tra bản xem trước rồi xác nhận lưu. Nhập lại cùng mã trường sẽ thay lịch của trường đó thuộc GV đang đăng nhập. Dùng cùng mốc tuần 1 để tổng hợp nhiều trường. Cảnh báo trùng giờ dựa trên giờ thực của các buổi học, không chỉ số tiết.

## Xếp lịch toàn khoa bằng ba file

ADMIN có thể tải mẫu Excel hoặc nhập nhanh trên giao diện:

| Dữ liệu | Nội dung |
| --- | --- |
| Kế hoạch | STT, Mã môn, Tên học phần, Sĩ số, Khóa, Số lớp mở, Ngày bắt đầu, Ngày kết thúc, Số tín chỉ, Tổng tiết LT, Tổng tiết TH. |
| Giảng viên | Mã môn, Tên học phần, Khóa, Mã GV, Họ tên GV, Trọng số LT, Trọng số TH. |
| Phòng | Theo mẫu phòng tải trên giao diện; có thể dùng danh mục phòng đã lưu đúng trường. |

Ngày nhận `dd/MM/yyyy`, `yyyy-MM-dd` hoặc ô ngày Excel thực. Tín chỉ và hai tổng tiết bắt buộc; ghi 0 cho phần không học. Tổng LT/TH theo đề cương, không suy từ tín chỉ. Trọng số LT/TH chia số lớp/nhóm theo từng môn và khóa; trọng số 0 không nhận loại đó. LT và TH có thể phân cho hai GV khác nhau.

Thuật toán chia nhóm TH theo sức chứa LAB; mỗi nhóm học đủ tổng tiết TH của môn. Lịch kiểm tra xung đột GV, phòng và lớp/nhóm theo ngày/giờ, bao gồm lịch GV ở trường khác. Luồng ba file dùng bảng 12 tiết, tối đa 100 tuần từ mốc tuần 1.

Nếu một lớp thiếu GV hoặc không thể hoàn tất, thuật toán tiếp tục các lớp khác. Kết quả hiển thị các lớp chưa xếp, lý do và khối lượng còn thiếu:

- `SUCCESS`, `saved=true`: tất cả lớp/nhóm đã được xếp và lưu.
- `PARTIAL_SUCCESS`, `saved=true`: lưu các lớp/nhóm hoàn tất; phần chưa xếp hiển thị riêng. Xếp lại kế hoạch thay phần lịch tự xếp của khoa, vì vậy kiểm tra kết quả trước khi dùng.
- `NOT_SAVED`, `saved=false`: không có lớp/nhóm hoàn tất để lưu; lịch cũ được giữ nguyên.

Có thể tải Excel ngay tại kết quả, kể cả khi chỉ có danh sách chưa xếp. File xuất có các sheet lịch, phân công, khối lượng và `LopChuaXep`/`CanhBao` khi có dữ liệu tương ứng. Bản chưa lưu có tiền tố `xem-truoc-chua-luu-`; không coi đó là lịch chính thức. Kết quả dùng mốc tuần/bảng tiết của lần chạy, không lấy giá trị form vừa sửa. Reload hoặc đăng xuất sẽ xóa kết quả tác vụ đang hiển thị.

## Kiểm thử

Sau khi cài dependencies, chạy trong thư mục `frontend`:

```powershell
node --test --test-concurrency=1 src/*.test.js
npm.cmd run build
npx.cmd playwright install chromium
npm.cmd run test:browser -- tests/realtime.spec.js tests/partial-scheduling.spec.js --workers=1
```

Playwright tự bật Vite ở cổng 4179, kiểm tra desktop và mobile với dữ liệu test riêng. Chạy toàn bộ browser test bằng `npm.cmd run test:browser -- --workers=1`. Kết quả/trace nằm trong `test-results`, báo cáo HTML trong `playwright-report`, đều được Git bỏ qua.

Xem [hướng dẫn Docker](../DOCKER.md) và [quyền truy cập, tài khoản, Excel](../document/phan-quyen-va-xuat-excel.md).
