# Kịch bản demo Schedule Assistant

Thời lượng: **10–15 phút**. Phù hợp trình bày đồ án, ưu tiên ba điểm: **xếp lịch theo dữ liệu thực, xử lý lớp chưa xếp được, cập nhật lịch realtime giữa ADMIN và GV**.

## 1. Chuẩn bị trước khi trình bày

### Dữ liệu và tài khoản

Dữ liệu đã kiểm tra tại thời điểm soạn ngày **04/11/2026**:

| Hạng mục | Dữ liệu đang có |
| --- | --- |
| Mã khoa | `PTIT` |
| ADMIN | `admin_test` |
| GV để demo | `GV01` — Đào Ngọc Phong; dự phòng `GV06` — Vũ Đức Thắng |
| Danh mục GV | 15 GV, 18 phân công thuộc 10 môn |
| Phòng hiện tại | **3 phòng LT** `401-A2`, `402-A2`, `403-A2` — 60 chỗ/phòng; **2 phòng TH** `604-LAB-A2`, `605-LAB-A2` — 30 chỗ/phòng |
| Lịch hiện có | `GV01` và `GV06` đều có lịch ở Tuần 1 |
| Mốc Tuần 1 dùng trong demo | **Thứ Hai, 10/08/2026** |

Số phòng hiện tại là **5**, khác cấu hình 10 LT + 5 TH ở bước trước. Khi trình bày, dùng số đang hiện trên web. Nếu cần bộ phòng khác, chuẩn bị trước và chạy thử lại kết quả.

Mật khẩu ADMIN dùng mật khẩu hiện có. Mật khẩu GV nếu chưa đổi sau khi nhập dữ liệu theo quy tắc: họ tên không dấu, bỏ khoảng trắng, viết thường + mã GV viết hoa. Không cần đưa mật khẩu lên slide.

### Ba file sử dụng

| Ký hiệu | File | Mục đích |
| --- | --- | --- |
| **A — gốc** | [ke-hoach-mo-lop-kiem-thu-2.xlsx](../../ke-hoach-mo-lop-kiem-thu-2.xlsx) | 14 dòng kế hoạch LT/TH, từ **10/08/2026 đến 22/11/2026**. Dùng xếp lịch ban đầu và khôi phục sau demo. |
| **B — realtime** | [ke-hoach-demo-realtime-tuan-2.xlsx](demo/ke-hoach-demo-realtime-tuan-2.xlsx) | Giữ khối lượng, dịch toàn bộ ngày học thêm 7 ngày, thành **17/08/2026–29/11/2026**. Giữ mốc Tuần 1 trên form là **10/08/2026**. |
| **C — thiếu GV** | [ke-hoach-demo-thieu-giang-vien.xlsx](demo/ke-hoach-demo-thieu-giang-vien.xlsx) | File A cộng một lớp LT của môn `DEMO_NO_GV`, khóa `2024`, 30 SV, 3 tiết, chưa có phân công GV. |

Hai file B/C đã được tạo và đọc lại để kiểm tra. File A gốc được giữ nguyên. Nếu mang sang máy khác, mang theo cả ba file và chuẩn bị danh mục GV/phòng tương ứng.

### Mở sẵn màn hình

1. Mở web đang chạy, ví dụ `http://localhost:8088`.
2. Cửa sổ thường đăng nhập `admin_test`.
3. Cửa sổ ẩn danh hoặc trình duyệt khác đăng nhập `GV01`.
4. Đặt hai cửa sổ cạnh nhau. Tránh dùng chung một tab để đổi qua lại hai tài khoản.
5. Bên GV mở **Lịch giảng dạy → Tuần 1**, chọn khoa PTIT nếu có nhiều đơn vị; ghi nhận ít nhất một buổi đang hiển thị.
6. Chờ trạng thái **Đang nhận cập nhật lịch trực tiếp**. Kiểm tra hai cửa sổ còn đăng nhập trước lúc trình bày.
7. Chuẩn bị Excel hoặc phần mềm đọc `.xlsx` để mở kết quả tải xuống.

Chạy thử một lượt trước buổi trình bày. Việc bấm xếp lịch sẽ lưu/thay phần lịch tự xếp của khoa theo kết quả trả về. Bản kịch bản này chỉ chuẩn bị tài liệu/file đầu vào, chưa tự chạy các tác vụ đó.

## 2. Trình tự demo chính

| Thời gian | Thao tác | Lời trình bày gợi ý | Kết quả cần chỉ ra |
| --- | --- | --- | --- |
| 0:00–1:00 | Giới thiệu màn hình và mục tiêu. | “Ứng dụng hỗ trợ khoa quản lý giảng viên, phòng học và xếp lịch từ Excel. Giảng viên xem lịch cá nhân và tự nhận thay đổi khi khoa cập nhật.” | Hai vai trò và luồng dữ liệu của ứng dụng. |
| 1:00–2:00 | So sánh menu của ADMIN và GV. | “ADMIN quản lý nguồn lực và xếp lịch khoa. Giảng viên theo dõi lịch cá nhân; các API cũng kiểm tra quyền theo tài khoản.” | ADMIN có **Quản lý GV & Phòng**, **Xếp lịch của khoa**; GV có lịch cá nhân và nhập lịch dạy. |
| 2:00–3:00 | ADMIN mở **Quản lý GV & Phòng**, mã `PTIT`; xem `GV01` và các môn phụ trách, rồi danh mục phòng. | “Một giảng viên có thể phụ trách nhiều môn, nhiều khóa. Trọng số LT và TH được khai báo riêng. Phòng có loại và sức chứa để thuật toán lựa chọn.” | 15 GV; phân công nhiều môn; phân biệt phòng LT 60 chỗ và LAB 30 chỗ. |
| 3:00–6:00 | Vào **Xếp lịch của khoa**, đặt Tuần 1 **10/08/2026**, chọn file **A**, bấm **Tải File Mở Lớp & Chạy Xếp Lịch Khoa**. | “Kế hoạch khai báo riêng LT/TH, sĩ số, số lớp và khoảng ngày. Hệ thống phân công theo trọng số và tìm lịch theo các ràng buộc GV, phòng, lớp/nhóm.” | Trạng thái đã lưu/chưa lưu, lịch đã xếp và các lớp còn thiếu. Dùng trạng thái thực tế được trả về. |
| 6:00–7:00 | Lọc kết quả theo `GV01`, xem một buổi LT và một buổi TH nếu có; bấm **Xem TKB**. | “LT tối đa 3 tiết cho mỗi lớp trong ngày. TH được chia thành buổi 3–4 tiết; ưu tiên cách 14 ngày và có lượt dự phòng 7 ngày khi cần.” | Phòng, thứ, số tiết và tuần/ngày học cụ thể. Việc gom cùng môn cho GV là ưu tiên khi xếp. |
| 7:00–8:00 | Bấm **Xuất Excel kết quả**, mở file vừa tải. | “Kết quả có thể tải để kiểm tra và sử dụng tiếp. Phần chưa xếp được cũng có báo cáo với lý do.” | Sheet `MonHoc`, `TietHoc`; `LopChuaXep`/`CanhBao` nếu có. |
| 8:00–11:00 | Thực hiện ca realtime ở mục 3 với file **B**. | “Khoa thay đổi kế hoạch; giảng viên đang mở web nhận lịch mới mà không cần F5.” | Tuần 1 bên GV tự hết các buổi của kế hoạch cũ; Tuần 2 có lịch mới nếu GV được xếp. |
| 11:00–13:00 | ADMIN chọn file **C**, giữ mốc **10/08/2026**, chạy lại. | “Một môn chưa có giảng viên sẽ được báo riêng. Những lớp khác vẫn được tiếp tục xử lý và phần hoàn tất vẫn có thể lưu.” | Môn `DEMO_NO_GV` trong bảng chưa xếp, lý do thiếu GV; các lớp khác vẫn có kết quả. Tải Excel báo cáo. |
| 13:00–14:00 | Nếu còn thời gian: giữ file A, đổi mốc Tuần 1 thành **26/10/2026**, bấm xếp lịch; sau đó sửa lại **10/08/2026**. | “Hệ thống kiểm tra khoảng ngày của kế hoạch theo mốc học kỳ, giúp phát hiện dữ liệu nhập không thống nhất.” | Cảnh báo ngày ngay dòng đầu có lỗi; không coi lần chạy này là đã lưu thành công. |
| 14:00–15:00 | Chạy lại file **A** với mốc **10/08/2026** để đưa lịch về bộ dữ liệu ban đầu; kết thúc. | “Luồng chính đi từ quản lý nguồn lực, nhập kế hoạch, xếp lịch, báo cáo kết quả đến đồng bộ lịch cho giảng viên.” | Lịch cuối cùng dùng lại khoảng 10/08–22/11/2026. |

Nếu chỉ có 10 phút: giữ các phần **quản lý nguồn lực → xếp lịch → xuất Excel → realtime → thiếu GV**; bỏ ca sai ngày và rút phần giới thiệu.

## 3. Ca realtime cần trình bày rõ

### Trạng thái ban đầu

- ADMIN đã chạy file A với mốc Tuần 1 **10/08/2026**.
- GV01 đang xem **Tuần 1 — 10/08 đến 16/08/2026**, có lịch của khoa PTIT và trạng thái kết nối trực tiếp.
- Có thể nhập bộ lọc “Cơ sở dữ liệu” để cho thấy bộ lọc được giữ khi đồng bộ.

### Thao tác

1. Giữ cửa sổ GV đang mở và đặt cạnh cửa sổ ADMIN.
2. Bên ADMIN chọn file **B — realtime**, mốc Tuần 1 vẫn **10/08/2026**.
3. Bấm chạy xếp lịch và chờ kết quả được lưu. Nếu chỉ là bản xem trước `saved=false`, chưa có thay đổi lịch để trình diễn.
4. Quan sát cửa sổ GV tự cập nhật. **Không F5 và chưa bấm vào cửa sổ GV trong lúc chờ**, để sự thay đổi quan sát được đến từ kết nối trực tiếp; thao tác chuyển focus cũng có cơ chế đọc lại lịch dự phòng.
5. Vì file B chỉ bắt đầu từ **17/08/2026**, Tuần 1 không còn buổi học của bộ kế hoạch đó. Tuần đang chọn và nội dung tìm kiếm vẫn giữ.
6. Sau khi đã thấy tự cập nhật, chuyển bên GV sang **Tuần 2 — 17/08 đến 23/08/2026** để xem lịch đã dịch.
7. Khi chạy lại file A, đưa GV về Tuần 1 để quan sát lịch xuất hiện trở lại.

**Lời nói ngắn:** “Sau khi transaction lưu lịch thành công, server gửi thông báo đến các kết nối liên quan. Giao diện giảng viên tự lấy lại dữ liệu bằng API có xác thực, giữ tuần và bộ lọc đang xem.”

SSE gửi thông báo thay đổi; nội dung lịch được lấy lại qua API. Cơ chế kết nối lại sẽ đồng bộ dữ liệu nếu có thay đổi bị bỏ lỡ khi mất mạng.

## 4. Cách giải thích khi kết quả chỉ xếp được một phần

Với danh mục hiện tại, phòng LT lớn nhất chỉ **60 chỗ**. Trong file A có dòng LT môn Đồ họa máy tính, mã `61FIT3CGR`, sĩ số **100**. Nếu vẫn giữ cấu hình phòng này, lớp đó không có phòng đủ sức chứa.

Đây là tình huống phù hợp để chỉ ra kiểm tra tài nguyên:

> “Lớp 100 sinh viên cần phòng đủ sức chứa. Hệ thống báo phần chưa xếp được, đồng thời tiếp tục xử lý các lớp phù hợp với danh mục phòng đang có.”

| Kết quả | Cách trình bày |
| --- | --- |
| `SUCCESS`, `saved=true` | Các lớp/nhóm của lần chạy đã hoàn tất và được lưu. |
| `PARTIAL_SUCCESS`, `saved=true` | Lưu phần lớp/nhóm đã hoàn tất; mở bảng còn thiếu để giải thích nguyên nhân. |
| `NOT_SAVED`, `saved=false` | Kết quả chưa được lưu; dùng báo cáo để kiểm tra nguyên nhân và dữ liệu/tài nguyên. Không chuyển sang nói lịch đã được cập nhật realtime. |

Trong ca C, phân biệt **thiếu GV** của `DEMO_NO_GV` với **thiếu phòng** của các lớp có sĩ số vượt sức chứa. Đối chiếu lý do thực tế trên giao diện/Excel; không trình bày mọi lỗi là thiếu GV.

## 5. Phương án khi demo gặp trục trặc

| Tình huống | Xử lý |
| --- | --- |
| Lỗi ngày ở dòng 2 | Kiểm tra mốc Tuần 1 là **10 tháng 8 năm 2026**. Nếu ô ngày dùng `MM/DD/YYYY`, nó hiển thị **08/10/2026**. File B đổi ngày học, không đổi mốc Tuần 1. |
| GV không có lịch trên màn hình | Chọn đúng `GV01`, khoa PTIT và Tuần 1 của file A hoặc Tuần 2 của file B; xóa bộ lọc không phù hợp. |
| Không thấy kết nối trực tiếp | Kiểm tra còn đăng nhập và backend còn hoạt động. Khi kết nối lại thành công, giao diện sẽ đọc lại lịch; nếu phải F5 để khôi phục thì thực hiện lại ca realtime từ đầu trước khi kết luận. |
| Xếp lịch trả `saved=false` | Mở lý do chưa xếp, kiểm tra GV/phòng/ngày. Chuyển sang minh họa báo cáo; chỉ diễn ca realtime khi có thay đổi thực sự được lưu. |
| Mạng chia sẻ ngrok chậm | Ưu tiên demo trực tiếp `localhost:8088` trên máy đang chạy Docker. |
| Cần rút ngắn thời gian | Mở lịch đang có, trình diễn realtime với file B và ca thiếu GV với file C; dùng bản Excel kết quả đã tải trong buổi chạy thử để giải thích cấu trúc báo cáo nếu cần. |

## 6. Câu hỏi có thể gặp

**Một GV dạy nhiều môn được phân chia thế nào?**  
Phân công môn/khóa có trọng số LT và TH riêng. Thuật toán chia lớp/nhóm theo trọng số, sau đó xét khả năng xếp và xung đột tài nguyên. Có ưu tiên gom cùng môn trong ngày/buổi cho GV.

**Có luôn xếp được tất cả lớp không?**  
Kết quả phụ thuộc phòng, GV, khoảng ngày và ràng buộc. Hệ thống báo rõ lớp chưa hoàn tất, tiếp tục các lớp khác và lưu phần hoàn tất nếu có.

**Khoảng cách buổi TH có bắt buộc 14 ngày không?**  
Ưu tiên 14 ngày, có lượt dự phòng 7 ngày khi cần. Buổi TH chia 3–4 tiết; không trình bày 14 ngày là điều kiện cứng trong mọi trường hợp.

**Vì sao buổi nhiều tiết không bắt đầu ở tiết 5?**  
Đây là quy tắc đã cấu hình: nếu bắt đầu ở tiết 5 và có hơn một tiết, chuyển ứng viên bắt đầu sang tiết 7; vẫn kiểm tra các ràng buộc còn lại.

**Realtime hoạt động bằng gì?**  
SSE gửi thông báo từ backend đến trình duyệt. Sau khi lưu thành công, GV liên quan và ADMIN nhận thông báo; frontend lấy lại lịch qua API. Khi mất kết nối, frontend thử nối lại và đồng bộ.

**Tính năng nào có thể demo thêm nếu còn thời gian?**  
Trang Hôm nay, xem danh sách/thẻ lịch, nhập lịch cá nhân, xuất lịch `.ics`, giao diện mobile, xếp bổ sung thỉnh giảng. Chạy thử trước các nhánh bổ sung và dùng tài khoản/dữ liệu riêng nếu cần thay mật khẩu hoặc thêm lịch.

## 7. Lời mở đầu và kết thúc

**Mở đầu, khoảng 30 giây:**

> “Em xin demo ứng dụng quản lý và xếp lịch giảng dạy. Khoa chuẩn bị danh mục giảng viên, môn phụ trách và phòng học; sau đó nhập kế hoạch mở lớp để hệ thống phân công, xếp lịch và báo những lớp chưa hoàn tất. Giảng viên xem lịch cá nhân và tự nhận thay đổi khi khoa cập nhật.”

**Kết thúc, khoảng 20 giây:**

> “Qua các bước vừa rồi, ứng dụng đã thể hiện luồng nhập kế hoạch, xếp lịch có kiểm tra nguồn lực, xuất báo cáo và cập nhật lịch trực tiếp cho giảng viên. Các lớp chưa xếp được có lý do cụ thể để khoa bổ sung dữ liệu hoặc điều chỉnh tài nguyên.”
