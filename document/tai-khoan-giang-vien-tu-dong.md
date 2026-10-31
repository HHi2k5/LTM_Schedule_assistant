# Tự tạo tài khoản giảng viên

Cập nhật theo yêu cầu ngày 01/11/2026, thay chính sách chỉ tự đăng ký của kế hoạch ban đầu. Áp dụng cho tài khoản được tạo mới; tài khoản đã liên kết tiếp tục dùng thông tin đăng nhập hiện có.

## Chỉ đăng nhập bằng tài khoản được cấp

Theo yêu cầu tiếp theo, đã bỏ hoàn toàn giao diện và chức năng tự đăng ký. Giảng viên nhận tài khoản từ nhà trường rồi đăng nhập; chưa có tài khoản thì liên hệ quản trị. Đăng nhập bằng tên chưa tồn tại không tạo tài khoản mới.

Đường dẫn cũ `/api/schedule/auth/register` và các đường dẫn con bị chặn cho cả khách, TEACHER và ADMIN; controller/service không còn chức năng đăng ký. Login và đổi mật khẩu tiếp tục dùng được. Quản trị tạo tài khoản qua danh mục giảng viên, nhập Excel hoặc nút tạo tài khoản còn thiếu như bên dưới.

## Quy tắc

- Tên đăng nhập: **MGV**, chuẩn hóa chữ hoa như mã giảng viên trong danh mục.
- Mật khẩu ban đầu: **toàn bộ họ tên viết liền, chữ thường, bỏ dấu và dấu câu + MGV**. Phần MGV giữ nguyên dạng mã đã chuẩn hóa.
- Ví dụ: Nguyễn Văn An, mã `GV01` → tài khoản `GV01`, mật khẩu ban đầu `nguyenvananGV01`.
- `Đ/đ` đổi thành `d`; tên Unicode dựng sẵn hoặc tổ hợp có dấu cho cùng kết quả. Không cắt ngắn mật khẩu; nếu công thức vượt 72 byte hoặc tên không có ký tự Latin/số dùng được, báo lỗi để sửa dữ liệu.
- Vai trò tạo mới luôn là `TEACHER`. Mật khẩu lưu BCrypt, không trả mật khẩu/hash qua API danh mục và không lưu mật khẩu rõ trong nhật ký.
- Sửa họ tên, nhập lại Excel, tạo tài khoản còn thiếu hoặc xếp lịch lại không đặt lại mật khẩu đã tồn tại. Giảng viên đổi mật khẩu trong Cài đặt như trước.

## Khi nào tạo tài khoản

| Luồng | Thời điểm |
|---|---|
| Thêm hoặc sửa GV trong danh mục | Khi lưu GV đang hoạt động trong trường đang hoạt động, nếu chưa liên kết tài khoản. |
| Nhập danh mục GV từ Excel | Preview chỉ kiểm tra. Commit tạo tài khoản cùng hồ sơ; dòng cũ chưa có tài khoản được đánh dấu cập nhật để tạo tài khoản. Nhập lại sau đó không tạo thêm. |
| Tạo hồ sơ thỉnh giảng và phân công | Tài khoản, hồ sơ và các phân công cùng giao dịch; lỗi một phân công rollback toàn bộ. |
| Lịch khoa cũ có GV chưa có tài khoản | Khi xác nhận bản lịch, tạo/nâng hồ sơ lịch thành tài khoản và giữ danh tính lịch. Tạo nháp/xem trước không tự ghi tài khoản. |
| Lưu lịch toàn trường qua Excel/nhập tay | Chỉ khi xếp đủ và lưu thành công. Kết quả chưa lưu không tạo tài khoản. |

Hồ sơ ngừng sử dụng không tự tạo tài khoản từ danh mục. Nếu đã có tài khoản, nhập **Mã tài khoản GV đã có** để liên kết đúng người. Không dùng họ tên để tự ghép hai tài khoản.

## Danh mục cũ

Vào **Danh mục nhà trường → Giảng viên → Tạo tài khoản còn thiếu (N)**. Thao tác áp dụng cho tất cả GV đang hoạt động chưa liên kết trong trường đang chọn, kể cả ngoài bộ lọc đang hiển thị. Cột **Tên đăng nhập** cho biết tài khoản thực tế sau khi lưu.

Server kiểm tra revision từng hồ sơ và ghi cả đợt trong một giao dịch. Lỗi ở một GV không để lại tài khoản hoặc liên kết của các GV trước đó. Lỗi 409 yêu cầu tải lại danh mục; giao diện không tự gửi lại.

Tên đăng nhập là duy nhất toàn hệ thống. Nếu MGV đã thuộc tài khoản khác (kể cả khác chữ hoa/thường), hệ thống báo cần liên kết đúng tài khoản; không ghi đè mật khẩu, không đổi quyền ADMIN, không tự gộp người ở hai trường. Nếu đúng cùng người, liên kết tường minh tài khoản đã có ở trường thứ hai.

Hồ sơ lịch cũ được người quản trị chỉ định sẽ giữ mã nội bộ, mốc tuần và các buổi đã lưu khi thêm khả năng đăng nhập. Mã nội bộ có thể khác MGV; tên đăng nhập của tài khoản tự tạo vẫn là MGV. Luồng lịch cũ đối chiếu tên đăng nhập tự tạo với mã nội bộ để dùng đúng danh tính.

## API và mã nguồn

`POST /api/admin/institutions/{schoolCode}/lecturer-accounts` chỉ dành cho ADMIN:

```json
{
  "lecturers": [
    {"id": 123, "revision": 2}
  ]
}
```

Giới hạn 1–10.000 hồ sơ duy nhất, thuộc trường và đang hoạt động. Response có `created` và các `lecturers`; mỗi hồ sơ trả thêm `accountUsername`, `autoProvisioned`, cùng mã liên kết đã có. Không trả mật khẩu/hash. Một danh sách đã được liên kết, gửi lại với revision hiện hành, không tạo tài khoản thừa.

[LecturerAccountService](../server/src/main/java/com/service/LecturerAccountService.java) dùng chung cho danh mục và lưu lịch. Các thao tác ghi giữ khóa SQL chung. Snapshot nhập danh mục bao gồm trạng thái tài khoản/username liên quan để phát hiện xung đột phát sinh sau preview.

[Migration V9](../server/src/main/resources/db/migration/V9__automatic_lecturer_accounts.sql) thêm cờ nhận biết tài khoản tự tạo. Không tự tạo hay đổi mật khẩu của tài khoản cũ bằng SQL migration; việc tạo/nâng tài khoản do service thực hiện trong giao dịch đã nêu. Cờ này hỗ trợ đối chiếu phiên bản lịch cũ khi danh tính lịch được giữ nguyên nhưng vừa có khả năng đăng nhập.

Chưa áp migration hoặc chạy tạo tài khoản trên database thực của người dùng. Kiểm thử dùng H2 và MySQL container riêng; kết quả được ghi trong [nhật ký bổ sung của kế hoạch](./plan-danh-muc-thinh-giang-va-lich-toan-khoa.md).
