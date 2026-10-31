# Quản lý danh mục trên web — chặng 2

Chặng 3 đã nối danh mục vào màn hình xếp lịch; xem [hướng dẫn dùng danh mục khi xếp lịch](./xep-lich-danh-muc-chang-3.md). Mẫu GV nay có thêm cột tùy chọn **Mã hồ sơ lịch cũ** để nối những hồ sơ chưa có tài khoản đã được đối chiếu.

Đăng nhập ADMIN rồi chọn **Danh mục nhà trường**. Giảng viên không có mục điều hướng này; backend cũng kiểm tra quyền ADMIN cho mọi API danh mục.

## 1. Chuẩn bị danh mục

1. Chọn **Thêm trường**, điền mã và tên. Nếu đã có phòng/lịch cũ, dùng đúng mã trường cũ hoặc mở **Đối chiếu dữ liệu cũ**.
2. Tab **Khoa**: thêm các khoa/bộ môn. Khoa khác với khóa sinh viên, ví dụ khóa 2024.
3. Tab **Giảng viên**: thêm mã GV trong trường, họ tên, khoa và loại cơ hữu/thỉnh giảng. Có thể để chưa phân khoa.
4. Nếu đã có tài khoản, nhập **Mã tài khoản GV đã có** để liên kết. Để trống sẽ tự tạo tài khoản MGV cho GV đang hoạt động, mật khẩu ban đầu là họ tên viết liền, chữ thường, không dấu + MGV. Danh mục cũ có nút **Tạo tài khoản còn thiếu**. Giảng viên chỉ đăng nhập bằng tài khoản được cấp, không tự đăng ký; xem [quy tắc tài khoản tự động](./tai-khoan-giang-vien-tu-dong.md).
5. Tab **Phòng học**: thêm mã/tên, loại LT hoặc LAB, sức chứa và tòa nhà.
6. Tab **Phân công môn**: chọn giảng viên, thêm môn–khóa và trọng số LT/TH mặc định. Trọng số 0 nghĩa là không nhận loại đó; phân công đang hoạt động phải có ít nhất một trọng số lớn hơn 0.

Các bản ghi được lưu trong database. Tải lại trang hoặc đăng nhập lại sẽ đọc lại dữ liệu đã lưu; trình duyệt chỉ nhớ trường đang chọn.

## 2. Tìm, sửa và ngừng sử dụng

- Tìm theo mã/tên/thông tin đang hiển thị. Giảng viên có thêm lọc khoa; các bảng có lọc trạng thái và phân trang 20 dòng.
- Chọn **Sửa** ở dòng cần chỉnh. Mã định danh giữ cố định để không làm lệch tham chiếu.
- Bỏ chọn **Đang sử dụng** rồi lưu để ngừng dùng bản ghi. Phòng đã ngừng dùng sẽ không được xếp trong các lần chạy mới; lịch cũ vẫn còn.
- Khi gặp thông báo dữ liệu đã thay đổi, nội dung form được giữ. **Đóng và tải lại** là lựa chọn chủ động để đọc bản mới trước khi sửa tiếp.
- Trường ngừng sử dụng vẫn xem/sửa được dữ liệu cũ; cần kích hoạt lại trường trước khi thêm hoặc nhập danh mục mới.

## 3. Nhập Excel có xem trước

Mở tab **Giảng viên**, **Phòng học** hoặc **Phân công môn** → **Nhập Excel** → **Tải mẫu**.

Quy định chung:

- File `.xlsx`, sheet đầu tiên chứa dữ liệu, dòng 1 là tiêu đề. Tối đa 1000 dòng dữ liệu và 10 MB. Sheet có trên 10000 dòng kể cả định dạng thừa cần được dọn trước khi nhập.
- Các cột có dấu được nhận diện không phân biệt hoa/thường hoặc dấu tiếng Việt. Không đổi tên/nhân đôi cột của mẫu.
- Không dùng công thức trong các cột dữ liệu; dán giá trị trước khi nhập.
- Mã trùng trong một file là lỗi. Với phân công, khóa duy nhất là bộ **GV–môn–khóa sinh viên**.
- Cột tùy chọn để trống giữ dữ liệu cũ. Bản ghi mới dùng mặc định; muốn xóa liên kết khoa/tài khoản hoặc tòa nhà thì dùng biểu mẫu sửa.
- Không xóa các bản ghi vắng trong file. Danh mục giảng viên và lịch cá nhân không bị thay toàn bộ khi nhập lại.

| Loại | Cột bắt buộc | Cột tùy chọn |
|---|---|---|
| Giảng viên | Mã GV, Họ tên | Mã khoa, Loại GV, Mã tài khoản, Trạng thái |
| Phòng | Mã phòng, Loại phòng, Sức chứa | Tên phòng, Tòa nhà, Trạng thái |
| Phân công | Mã GV, Mã môn, Tên môn, Khóa, Trọng số LT, Trọng số TH | Trạng thái |

- **Loại GV:** `STAFF`/`Cơ hữu`, `VISITING`/`Thỉnh giảng`. Hồ sơ mới để trống sẽ mặc định cơ hữu; bản xem trước thể hiện rõ loại trước khi lưu.
- **Loại phòng:** `THEORY` hoặc `PRACTICE`; vẫn nhận các tên loại phòng cũ do hệ thống xuất. Sức chứa là số nguyên 1–10000.
- **Trạng thái:** `TRUE`/`FALSE` hoặc `1`/`0`. Bản ghi mới để trống mặc định đang sử dụng; bản ghi đã có giữ trạng thái cũ.
- **Mã khoa:** cần tạo khoa đúng trong trường trước khi nhập GV có mã khoa.
- **Mã tài khoản:** chỉ nhận tài khoản TEACHER đã đăng ký; không nhận ADMIN. Một tài khoản chỉ liên kết một hồ sơ trong cùng trường.
- **Phân công:** cần nhập danh mục GV trước; sau đó có thể dùng file giảng viên–môn–khóa hiện tại để nhập phân công. File danh mục GV phải có mỗi mã một dòng, không dùng các dòng lặp GV theo nhiều môn để tạo hồ sơ.

Sau khi chọn file, bấm **Xem trước**. Màn hình hiển thị số dòng thêm mới, cập nhật, giữ nguyên và lỗi; mỗi dòng có thông tin sau nhập, thông tin trước đây khi thay đổi và lỗi tương ứng.

Chỉ khi không có lỗi và có thay đổi mới bật **Xác nhận lưu danh mục**. Server kiểm tra lại file và danh mục hiện hành trước khi lưu. Nếu một dòng lỗi, không lưu dòng nào; nếu lỗi xảy ra giữa lúc ghi, rollback toàn bộ. Nếu dữ liệu thay đổi từ lần xem trước, cần xem trước lại; ứng dụng không tự gửi lại thao tác lưu.

## 4. Đối chiếu dữ liệu cũ

**Đối chiếu dữ liệu cũ** đọc các mã trường đã xuất hiện trong phòng và lịch cá nhân, số phòng, tên trường và những giảng viên đã có lịch.

- Nếu một mã có nhiều tên, chọn hoặc điền tên đúng trước khi tạo danh mục trường. Không tự chọn một tên rồi ghi đè.
- Tạo danh mục bằng đúng mã sẽ cho phép xem/quản lý các phòng cũ của mã đó; không nhân đôi phòng.
- Với trường đã có danh mục, chọn **Mở {mã trường}** để quản lý.
- **Chuẩn bị nhập GV {mã trường}** tạo file từ dữ liệu cũ và đưa sang phần nhập GV. Việc này chưa ghi hồ sơ; vẫn phải **Xem trước** và **Xác nhận lưu danh mục**.
- Chỉ những tài khoản được xác định trực tiếp từ dữ liệu sở hữu lịch và đã đăng ký mới có gợi ý liên kết. Hồ sơ cũ không có username để trống mã tài khoản.
- Khoa và loại GV không được suy đoán từ tên hay khóa sinh viên. Loại trống của hồ sơ mới sẽ mặc định cơ hữu trong bản xem trước; cần điều chỉnh nếu là thỉnh giảng.
- Mã trường cũ chưa hợp lệ được báo để đối chiếu riêng, không tự thay mã lịch đang dùng.

## 5. API bổ sung

Tiền tố `/api/admin/institutions`; các API tạo/đọc/sửa của chặng 1 giữ nguyên, xem [hợp đồng chặng 1](./danh-muc-api-chang-1.md).

| API | Nội dung |
|---|---|
| `GET /legacy` | Gợi ý dữ liệu cũ, chỉ đọc, không chứa thông tin xác thực |
| `POST /{schoolCode}/imports/{kind}/preview` | Multipart `file`, trả số lượng, lỗi từng dòng, thông tin trước/sau và snapshot |
| `POST /{schoolCode}/imports/{kind}/commit` | Multipart `file` và `snapshot` vừa xem trước; server dựng lại đề xuất rồi ghi một giao dịch |

`kind`: `lecturers`, `rooms`, `assignments`. Snapshot ràng buộc nội dung file, trường, loại danh mục và trạng thái dữ liệu liên quan. Thay file hoặc dữ liệu cơ sở sẽ trả 409. Các action/ID/revision của dòng không được nhận như dữ liệu tin cậy từ client.

Ví dụ response xem trước:

```json
{
  "schoolCode": "PTIT",
  "kind": "ROOMS",
  "snapshot": "<SHA-256>",
  "created": 1,
  "updated": 0,
  "unchanged": 0,
  "invalid": 0,
  "canCommit": true,
  "rows": [
    {
      "rowNumber": 2,
      "action": "CREATE",
      "values": { "roomCode": "101-A2", "roomType": "THEORY_STANDARD", "capacity": 60 },
      "previous": {},
      "errors": []
    }
  ]
}
```

## 6. Phạm vi bàn giao

Ở phạm vi chặng 2, giao diện quản trị và nhập danh mục dùng schema V4, chưa thay luồng xếp lịch ba file. **Chặng 3** đã bổ sung schema V5 và luồng lấy giảng viên/phân công trực tiếp từ danh mục để bỏ yêu cầu nạp lại file; xem hướng dẫn được liên kết ở đầu tài liệu.

Không tự sửa dữ liệu database đang dùng trong quá trình viết và kiểm thử. Backend khi triển khai cần có migration V4; kiểm tra cấu hình Flyway của môi trường trước khi chạy ứng dụng cập nhật.
