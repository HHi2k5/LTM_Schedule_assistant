# Kế hoạch sửa xếp lịch: tiết bắt đầu và buổi thực hành

Ngày lập: 01/11/2026.

Trạng thái: tài liệu quy tắc đã chốt và yêu cầu bổ sung; phần xử lý thiếu GV tại mục 8 đã được triển khai trong mã nguồn. Chuyển từ tiết 5 xuống tiết 7 khi buổi dài hơn 1 tiết; TH ưu tiên cách 14 ngày, dự phòng tối thiểu 7 ngày. Ưu tiên gom cùng môn cho GV theo buổi/ngày; mỗi lớp LT tối đa 3 tiết/ngày, mỗi buổi TH dài 3–4 tiết.

Cập nhật yêu cầu xử lý thiếu GV: lớp chưa phân công được GV không chặn các lớp khác. Phần xử lý tiếp tục xếp lịch, thông báo và xuất Excel được ghi riêng tại mục 8; mục đó thay quy tắc cũ không lưu toàn bộ khi chỉ một lớp chưa xếp đủ.

Người dùng đã làm rõ: trần 3 tiết/ngày chỉ áp dụng cho lớp LT, không áp vào buổi TH 3–4 tiết. Việc gom nhiều lớp cùng môn cho một GV có ưu tiên cao hơn đề xuất cũ “một nhóm TH/GV/ngày”.

## 1. Mục tiêu và quy tắc

| Yêu cầu | Quy tắc dự kiến | Trạng thái |
|---|---|---|
| Buổi bắt đầu tiết 5 và dài hơn 1 tiết | Loại ứng viên tiết 5, ưu tiên thử lại ở tiết 7 với nguyên số tiết; kiểm tra lại toàn bộ điều kiện | Đã chốt tiết 7 |
| GV có thể dạy nhiều môn, nhiều khóa | Giữ phân công theo môn/khóa/LT–TH, nhưng chấm phương án dựa trên lịch chung của GV để gom môn | Yêu cầu mới |
| Ưu tiên một môn/GV/ngày hoặc buổi | Gom các lớp cùng mã môn vào cùng ngày; nếu cần nhiều môn trong ngày thì tách theo buổi, hạn chế đổi môn trong cùng buổi | Ưu tiên mềm |
| Một lớp LT tối đa 3 tiết/ngày | Cộng tất cả các buổi LT của cùng lớp trong ngày, không chỉ giới hạn từng buổi riêng lẻ | Ràng buộc bắt buộc |
| Một buổi TH từ 3–4 tiết | Chia đúng tổng khối lượng thành các buổi TH liên tục dài 3 hoặc 4 tiết | Ràng buộc riêng cho TH; không áp trần LT vào TH |
| Khoảng cách các buổi TH | Ưu tiên ít nhất 14 ngày; nếu không xếp đủ thì thử tối thiểu 7 ngày, không bao giờ dưới 7 ngày | Đã chốt ưu tiên 14, dự phòng 7 ngày |
| Lịch gọn nhất | Trong lượt khoảng cách đang xét, ưu tiên ít môn/GV/ngày, ít môn/GV/buổi, ít buổi và ít khoảng trống; giữ khung tiết/phòng ổn định | Mục tiêu tối ưu, trong giới hạn tìm kiếm |

Trong kế hoạch, `NEXT_START = 7`, `PREFERRED_GAP_DAYS = 14` và `MIN_GAP_DAYS = 7` là các giá trị người dùng đã chốt. Ví dụ cùng thứ ở tuần 1, 3, 5 là phương án ưu tiên; tuần 1, 2, 3 là phương án dự phòng khi không xếp đủ với khoảng cách 14 ngày.

Giới hạn LT là `MAX_THEORY_CLASS_PERIODS_PER_DAY = 3`; độ dài buổi TH là `MIN_PRACTICE_SESSION_PERIODS = 3`, `MAX_PRACTICE_SESSION_PERIODS = 4`. Đây không phải giới hạn GV chỉ được dạy 3 tiết/ngày. “Cùng môn” dùng mã môn trong phạm vi trường; cùng mã môn ở hai khóa vẫn là cùng môn để gom lịch, nhưng hai lớp vẫn học riêng.

Khoảng cách ngày áp dụng cho **cùng một lớp/nhóm thực hành**, không áp dụng chung cho tất cả các lớp mà một GV dạy. Ví dụ GV có thể dạy tổ A thứ Hai, tổ B thứ Ba; hai buổi của riêng tổ A vẫn phải đủ khoảng cách. Nếu một lớp được tách thành các tổ TH thì mỗi tổ là một đơn vị kiểm tra độc lập.

## 2. Hiện trạng đã đối chiếu với mã nguồn

- [Bộ xếp lịch theo tuần](../server/src/main/java/com/service/WeeklyScheduleEngine.java) dùng chung cách rải tiết cho LT và TH. Hàm sắp buổi tính chỉ tiêu theo tuần rồi có thể giảm độ dài buổi xuống 1 tiết; chưa có giới hạn khoảng cách ngày giữa các buổi.
- [Dữ liệu nhu cầu](../server/src/main/java/com/dto/CourseDemandDTO.java) mặc định giới hạn 3 tiết/buổi, 6 tiết/tuần. Chỉ tăng mặc định lên 4 chưa đủ giải quyết việc rải buổi ngắn, và có thể tác động cả LT.
- Vòng sắp buổi chỉ cộng số tiết theo tuần, chưa có bộ đếm tổng tiết/ngày; một lớp có thể được xếp hai buổi không trùng giờ, mỗi buổi 3 tiết trong cùng ngày, thành 6 tiết/ngày.
- Danh sách ứng viên GV được ưu tiên theo phần thiếu chỉ tiêu của từng môn/khóa/LT–TH. Hàm chọn khung chỉ ưu tiên thứ của buổi trước và nhận khung hợp lệ đầu tiên; chưa chấm số môn/GV/ngày hoặc số lần đổi môn.
- Reservation hiện giữ GV, ngày, giờ, lớp/nhóm và loại LT/TH nhưng chưa giữ mã môn để gom lịch. Định danh lớp được sinh theo môn/khóa/dòng kế hoạch; chưa có mã lớp hành chính chung xuyên các môn hoặc liên kết chắc chắn giữa hai dòng LT/TH riêng biệt.
- [Bộ giữ chỗ](../server/src/main/java/com/service/WeeklyReservations.java) đã kiểm tra giờ GV, phòng, lớp/nhóm và phân biệt ngày LT/TH. Chưa có chỉ mục phục vụ ưu tiên một lớp TH/GV/ngày hoặc kiểm tra ngày các buổi của cùng nhóm.
- [Bảng giờ của luồng Excel](../server/src/main/java/com/service/ExcelScheduleService.java) có tiết 5 lúc 11:00–11:50, tiết 6 lúc 12:00–13:00 và tiết 7 lúc 13:00–13:50. Người dùng đã xác nhận chuyển buổi nhiều tiết từ tiết 5 sang tiết 7.
- [Luồng thỉnh giảng](../server/src/main/java/com/service/ScheduleService.java) có thuật toán riêng, đang nhận 1–5 tiết/buổi và có thể xếp nhiều ngày trong tuần. Cần dùng chung quy tắc mới để hai luồng cho kết quả nhất quán.
- Nhánh xếp lịch cũ trong [dịch vụ xếp lịch](../server/src/main/java/com/service/AutoScheduleService.java) vẫn tồn tại. Khi triển khai phải rà soát các endpoint còn dùng nhánh này, tránh đường gọi bỏ qua quy tắc mới.
- Cây mã hiện tại chưa có thư mục kiểm thử backend `server/src/test`; kế hoạch kiểm thử sẽ tạo các ca cần thiết thay vì dựa vào ghi nhận kiểm thử trong tài liệu cũ.

## 3. Quy tắc chi tiết

### 3.1. Đổi ứng viên bắt đầu ở tiết 5

Áp dụng cho buổi tự động xếp của cả LT và TH:

1. Nếu tiết đầu là 5 và số tiết bằng 1: giữ hành vi hiện tại đối với LT; tổng LT của lớp trong ngày vẫn không vượt 3. TH không có buổi 1 tiết.
2. Nếu tiết đầu là 5 và số tiết lớn hơn 1: không chấp nhận ứng viên này; thử `NEXT_START = 7` với nguyên độ dài. Ví dụ tiết 5, dài 3 tiết chuyển thành tiết 7–9.
3. Ở tiết đích, kiểm tra lại đủ tiết trong bảng giờ, tính liên tục, ngày cho phép, GV, phòng, lớp/nhóm, giới hạn tuần và khoảng cách TH.
4. Nếu tiết đích không hợp lệ hoặc bận: thử khung khác. Không tự ghi lịch vào tiết đích khi chưa kiểm tra. Nếu thử độ dài khác, đó phải là phương án chia buổi riêng, giữ đúng tổng tiết, trần LT 3 tiết/ngày và độ dài TH 3–4 tiết/buổi.
5. Tránh thử lặp cùng ứng viên sau khi chuẩn hóa tiết đầu.

Quy tắc này chỉ áp dụng khi **tiết bắt đầu bằng 5**. Không tự mở rộng thành cấm mọi buổi đi qua tiết 5 hoặc cấm tiết 6. Bất kỳ thay đổi ranh giới sáng/chiều nào cần được định nghĩa riêng nếu người dùng yêu cầu.

### 3.2. LT tối đa 3 tiết/lớp/ngày; TH 3–4 tiết/buổi

Với LT, trước khi nhận một khung, tính `tổng tiết LT đã có trong ngày của lớp + số tiết LT đề xuất <= 3`. Cộng cả lịch đã giữ tạm và lịch hiện hữu thuộc cùng lớp LT. Không cho phép lách giới hạn bằng cách đổi GV, đổi phòng, chia hai buổi hoặc chia thành nhiều dòng kết quả.

Trong dữ liệu hiện tại, “lớp/nhóm” là lớp học phần/nhóm TH có định danh, không thể dùng tên “Lớp 01” hay cùng khóa để suy ra cùng tập sinh viên giữa nhiều môn. Để giới hạn tổng LT 3 tiết/ngày cho một lớp hành chính xuyên tất cả các môn thì cần bổ sung mã lớp sinh viên và ánh xạ đầu vào. Không tự tuyên bố đã kiểm soát được giới hạn xuyên môn khi thiếu dữ liệu này.

Không cộng tiết TH vào bộ đếm trần LT 3 tiết/ngày. TH có quy tắc độ dài và khoảng cách riêng; điều kiện không trùng giờ sinh viên và LT/TH khác thứ của cùng GV vẫn giữ như hiện tại. Nếu cần liên kết hai dòng LT/TH nhập riêng để kiểm tra sinh viên, phải dùng định danh ổn định, không ghép chỉ theo môn/khóa hoặc thứ tự dòng.

LT ưu tiên buổi 3 tiết, phần dư có thể là 1–2 tiết; tổng LT mỗi ngày vẫn không quá 3. Với TH, tìm số buổi 3 tiết và 4 tiết sao cho `3 × n3 + 4 × n4 = tổng tiết TH`, ưu tiên ít buổi nhất. Không để dư buổi TH 1–2 tiết và không tăng/giảm tổng khối lượng. Ví dụ cách chia TH:

| Tổng tiết TH | Cách chia ưu tiên | Số buổi |
|---|---|---|
| 7 | 4 + 3 | 2 |
| 8 | 4 + 4 | 2 |
| 9 | 3 + 3 + 3 | 3 |
| 10 | 4 + 3 + 3 | 3 |
| 12 | 3 buổi × 4 | 3 |
| 15 | 4 + 4 + 4 + 3 | 4 |
| 30 | 6 buổi × 4 + 2 buổi × 3 | 8 |

- Các buổi của cùng lớp/nhóm TH trong bảng phải giữ khoảng cách 14/7 ngày.
- Tổng TH 1, 2 hoặc 5 không biểu diễn được bằng các buổi 3–4 tiết: báo rõ dữ liệu không phù hợp, không tự tạo buổi ngắn. Tổng LT tương ứng vẫn xếp được với trần 3 tiết/ngày.
- Nếu TH 12 tiết không có khung 4 tiết phù hợp, thử 4 buổi × 3 thay cho 3 buổi × 4, trong ngân sách tìm kiếm.
- Một lớp LT có thể học 2 + 1 tiết trong ngày nếu cần và các điều kiện khác thỏa; ưu tiên một khối 3 tiết để lịch gọn. Hai buổi TH cùng nhóm không được cùng ngày do khoảng cách tối thiểu 7 ngày.
- Mỗi buổi liên tục trong một khung hợp lệ; không gộp hai đoạn rời nhau hoặc hai phòng thành một buổi.
- Tách giới hạn theo LT/TH: cấu hình LT không được vượt trần 3 tiết/ngày; TH cho phép buổi 4 tiết và phải có giới hạn tuần đủ chứa buổi đó. Chuẩn hóa hoặc báo rõ cấu hình mâu thuẫn ở backend, đồng bộ giao diện; không chỉ đổi số mặc định.

### 3.3. Khoảng cách giữa các buổi TH

- Lượt ưu tiên: hai ngày học bất kỳ của cùng một lớp/nhóm TH phải chênh ít nhất `PREFERRED_GAP_DAYS = 14` ngày.
- Chỉ khi không xếp đủ trong lượt ưu tiên, sau khi đã thử GV, phòng, khung giờ và các cách chia buổi phù hợp trong ngân sách, mới chạy lượt dự phòng với `MIN_GAP_DAYS = 7`. “Lịch quá nhiều” được xác định bằng việc không xếp đủ trong khoảng ngày cho phép, không bằng một ngưỡng số lớp tùy ý.
- Lượt dự phòng vẫn ưu tiên giữ những cặp buổi cách nhau ít nhất 14 ngày; chỉ giảm các khoảng cần thiết và tối thiểu luôn là 7 ngày. Chênh 8–13 ngày cũng hợp lệ ở lượt này, không bắt buộc mọi khoảng phải đúng 7 ngày.
- Có ghi chú nhóm nào dùng khoảng cách dưới 14 ngày và lý do chuyển lượt. Nếu lượt 14 ngày hết ngân sách tìm kiếm thì ghi rõ giới hạn này, không kết luận đã chứng minh không tồn tại phương án 14 ngày.
- So sánh theo ngày thực tế, không chỉ theo số tuần. Chủ nhật bị loại theo quy tắc hiện hành; thứ Bảy tuần này đến thứ Hai tuần sau vẫn chỉ cách 2 ngày.
- Kiểm tra cả khi xếp từ đầu về cuối và khi thử từ cuối về đầu. Không chỉ so với buổi cuối cùng trong danh sách thử nếu danh sách chưa theo thứ tự ngày.
- Áp dụng đồng thời với khoảng ngày học, khoảng TH riêng, chế độ `MIDDLE`, `END`, `ALTERNATE` và giới hạn tiết/tuần.
- Nếu người dùng chọn rõ `ALTERNATE` thì tiếp tục lọc tuần chẵn/lẻ như hiện tại, sau đó kiểm tra khoảng ngày; không âm thầm bỏ lựa chọn này khi chạy lượt 7 ngày. Chính sách ưu tiên 14/dự phòng 7 mặc định dùng `FLEXIBLE`, không tự đặt `ALTERNATE` chỉ để biểu diễn ưu tiên 14 ngày.
- Khi xếp bổ sung cho cùng lớp/nhóm đã có lịch, phải tính cả các buổi đã lưu và giữ định danh nhóm nhất quán. Dữ liệu cũ thiếu định danh cần được đối chiếu rõ, không coi mọi lớp cùng mã môn là cùng nhóm.

### 3.4. Ưu tiên một môn/GV/ngày hoặc một môn/GV/buổi

- Tổng hợp lịch của GV xuyên các môn, các khóa và các phân công LT/TH; không chỉ xem bucket môn/khóa đang xét. Dùng mã môn, không so tên hiển thị có tiền tố LT/TH hay tên lớp.
- Phương án ưu tiên: GV có nhiều lớp cùng môn thì gom các lớp đó vào một ngày, sắp các khối gần nhau; mỗi lớp LT riêng tối đa 3 tiết/ngày, mỗi buổi TH 3–4 tiết.
- Ví dụ GV A dạy LT CSDL cho lớp X khóa 2024 tiết 1–3 và lớp Y khóa 2025 tiết 7–9 cùng ngày: vẫn là một môn/GV/ngày; mỗi lớp LT chỉ học 3 tiết. Nếu là TH, mỗi buổi có thể dài 3 hoặc 4 tiết.
- Nếu cần dạy nhiều môn trong ngày, ưu tiên một môn buổi sáng và môn khác buổi chiều; tránh luân phiên môn A → B → A trong cùng buổi.
- Đây là ưu tiên mềm: nếu gom một môn cả ngày không đủ thì thử gom theo từng buổi, sau đó mới chấp nhận đổi môn trong buổi. Mọi lượt vẫn giữ giờ, phòng, trần LT 3 tiết/ngày, độ dài TH 3–4 tiết/buổi và khoảng cách TH.
- Nhiều lớp/nhóm TH cùng môn được gom cùng ngày nếu GV/phòng không trùng giờ. Quy tắc này thay đề xuất cũ ưu tiên một nhóm TH/GV/ngày để tránh hai mục tiêu mâu thuẫn.
- Định nghĩa buổi theo giờ thực tế và cấu hình trường. Với bảng giờ hiện tại, đề xuất sáng trước 12:00, trưa 12:00–13:00 là khung riêng, chiều từ 13:00; khung đi qua ranh giới được tính vào mọi buổi nó chiếm, không được dùng để né mục tiêu một môn/buổi. Không tự cấm tiết 6 hoặc thay giờ trường.
- Dù hai lớp cùng khóa/cùng môn, không gộp sinh viên hai lớp thành một buổi chung để đạt mục tiêu gom môn.

## 4. Thiết kế thuật toán và thứ tự triển khai

### Bước 1 — Dùng chung quy tắc

Tạo thành phần chính sách dùng chung để kiểm tra tiết đầu, trần LT 3 tiết/lớp/ngày, độ dài TH 3–4 tiết/buổi, khoảng cách TH và mức ưu tiên số môn/GV/ngày/buổi. Các nhánh xếp lịch tự động và thỉnh giảng phải gọi cùng thành phần; không chỉ sửa dữ liệu hiển thị hoặc file Excel xuất ra.

### Bước 2 — Sinh các phương án chia buổi TH

Trong [bộ xếp lịch](../server/src/main/java/com/service/WeeklyScheduleEngine.java), tách cách sắp buổi LT và TH. TH nhận danh sách độ dài buổi 3–4 tiết từ bước chia khối lượng, thay cho chỉ tiêu tiết trung bình mỗi tuần. LT vẫn rải theo tuần nhưng mọi khung LT phải kiểm tra trần tổng LT 3 tiết/lớp/ngày.

Kiểm tra sớm điều kiện cần cho từng lượt: số ngày hợp lệ có thể giữ khoảng cách, tổng buổi tối thiểu và khả năng chứa khối lượng trong khoảng ngày. Nếu khoảng thời gian ngắn hơn `(số buổi - 1) × khoảng cách của lượt` thì bỏ qua lượt đó. Chỉ báo không đủ thời gian bắt buộc khi ngay cả mức tối thiểu 7 ngày cũng không đáp ứng. Vẫn cần tìm kiếm thực tế vì điều kiện này chưa xét phòng và giờ bận.

Với TH 3–4 tiết/buổi và khoảng cách ưu tiên 14 ngày: 15 tiết cần tối thiểu 4 buổi, tối thiểu 42 ngày từ buổi đầu đến buổi cuối; 30 tiết cần tối thiểu 8 buổi, tối thiểu 98 ngày. Nếu khoảng ngày không đủ thì thử mức 7 ngày, tương ứng tối thiểu 21 và 49 ngày. Không tăng buổi TH lên hơn 4 tiết hoặc rút khoảng cách xuống dưới 7 ngày.

### Bước 3 — Chọn lịch theo mức độ hoàn thành và độ gọn

Thử lượt 14 ngày trước, bao gồm cả phương án nới ưu tiên GV/ngày; chỉ chuyển sang lượt 7 ngày khi lượt trước không xếp đủ. Nếu một nhóm phía sau không xếp được vì lịch đã chốt của nhóm trước, thử điều chỉnh có giới hạn các nhóm liên quan trước khi chuyển lượt. Dành ngân sách riêng cho cả hai lượt và hoàn tác phương án thử đầy đủ.

Chấm toàn bộ phương án của một lớp/nhóm, thay vì chốt ngay phương án đầu tiên xếp đủ. Thứ tự so sánh:

1. Xếp đủ tiết và thỏa toàn bộ điều kiện bắt buộc.
2. Ưu tiên phương án đủ tiết ở lượt 14 ngày; trong lượt 7 ngày, giảm số khoảng dưới 14 ngày.
3. Ít ngày GV dạy nhiều môn nhất; trong đó ưu tiên ngày đã có cùng môn, kể cả các lớp khác khóa.
4. Ít buổi GV phải đổi môn nhất, ít lần đổi môn theo thứ tự thời gian nhất.
5. Ít buổi học và ít khoảng trống; ít thay đổi thứ, tiết, phòng giữa các tuần.
6. Ít lệch chỉ tiêu trọng số GV, rồi dùng thứ tự mã GV/ngày/phòng để kết quả lặp lại ổn định.

Giữ phân công LT/TH và một GV phụ trách trọn đơn vị lớp/nhóm. Chỉ chọn trong GV có trọng số tương ứng dương. Chỉ tiêu vẫn tính theo môn/khóa/LT–TH; lịch để chấm độ gọn là lịch chung của GV. Việc nâng mục tiêu gom môn có thể làm phân bổ thực tế lệch chỉ tiêu; cần kiểm thử và phản ánh trong thống kê hiện có. Không tự bổ sung giới hạn tải tổng GV/ngày/tuần hoặc đổi công thức trọng số khi người dùng chưa yêu cầu.

Giữ giới hạn tìm kiếm riêng cho từng lớp/nhóm. Phân ngân sách theo GV, cách chia và lượt ưu tiên để một cách thử không dùng hết ngân sách của các phương án còn lại. Nếu mở rộng đổi chỗ giữa các nhóm đã chốt, giới hạn phạm vi và số lần đổi; không cam kết tối ưu toàn cục với thuật toán hiện tại.

### Bước 4 — Mở rộng giữ chỗ và hoàn tác

Mở rộng [bộ giữ chỗ](../server/src/main/java/com/service/WeeklyReservations.java): thêm mã môn và số tiết của reservation; tổng tiết LT theo lớp/ngày; danh sách ngày TH theo nhóm; số buổi của từng môn theo GV/ngày/buổi. Đếm bằng bộ đếm có tham chiếu để bỏ một lớp cùng môn không xóa nhầm dấu môn còn được lớp khác sử dụng. Mỗi thay đổi tạm thời phải được hoàn tác cùng journal. Phương án thất bại không được để lại giờ bận, số tiết hoặc dấu môn giả.

### Bước 5 — Đồng bộ luồng thỉnh giảng và đầu vào

- [Luồng thỉnh giảng](../server/src/main/java/com/service/ScheduleService.java): dùng cùng trần LT 3 tiết/lớp/ngày, TH 3–4 tiết/buổi, khoảng cách TH và mục tiêu gom môn/GV/ngày/buổi; kiểm tra lại nếu có di chuyển lịch cũ để nhường phòng.
- [Giao diện xếp lịch](../frontend/src/AutoSchedulePage.jsx): tách LT tối đa 3 tiết/ngày và TH tự chia 3–4 tiết/buổi. Phần dư 1–2 tiết chỉ áp dụng LT; không cho TH tạo buổi ngắn. Giữ chính sách TH ưu tiên 14/dự phòng 7 ngày.
- [Dữ liệu thỉnh giảng](../server/src/main/java/com/dto/VisitingTeacherRequest.java) và [dữ liệu nhu cầu](../server/src/main/java/com/dto/CourseDemandDTO.java): tách giới hạn LT/ngày và TH/buổi, định nghĩa buổi và định danh lớp khi cần; xử lý tương thích request cũ và snapshot kế hoạch đã lưu.
- [Đọc kế hoạch Excel](../server/src/main/java/com/service/WeeklyPlanExcelReader.java): tổng tiết vẫn là tổng khối lượng của lớp/nhóm; không bắt buộc thêm cột để người dùng tự chia buổi.
- [Xuất Excel](../frontend/src/scheduleExport.js): giữ nguyên số tiết và các ngày/tuần thực tế; không gộp nhầm các buổi có độ dài, lớp hoặc khóa khác nhau.

### Bước 6 — Kiểm tra kết quả và lưu

Trước khi lưu, kiểm tra lại tổng tiết học phần, tổng LT mỗi lớp/ngày, độ dài từng buổi TH, tiết đầu, khoảng cách TH, giờ GV/phòng/lớp và phạm vi ngày trên kết quả cuối cùng. Lớp LT vượt 3 tiết/ngày, buổi TH ngoài 3–4 tiết hoặc khoảng cách TH dưới 7 ngày đều là lỗi bắt buộc. Khoảng cách 7–13 ngày ở lượt dự phòng và đổi môn khi phải nới ưu tiên là kết quả hợp lệ có ghi chú, không được coi là thiếu tiết hay lỗi khiến chặn lưu. Nếu thiếu tiết hoặc vi phạm điều kiện bắt buộc thì trả kết quả chưa lưu và giữ lịch hiện hữu. Luồng thỉnh giảng hiện có thể lưu khi còn thiếu tiết, nên cần điều chỉnh nhất quán với luồng toàn khoa.

Không tự sửa hàng loạt lịch đang lưu theo chính sách mới. Khi người dùng chạy xếp lại/bổ sung, đối chiếu lịch hiện hữu để không tạo xung đột.

## 5. Kiểm thử và điều kiện nghiệm thu

| Nhóm | Ca cần kiểm tra | Kết quả yêu cầu |
|---|---|---|
| Tiết bắt đầu | Tiết 5, 1 tiết LT | Không bị chuyển bởi quy tắc mới |
| Tiết bắt đầu | LT tiết 5 dài 2/3 tiết; TH tiết 5 dài 3/4 tiết | Thử tiết 7 với nguyên độ dài; buổi TH 4 tiết chuyển thành tiết 7–10 |
| Tiết bắt đầu | Tiết đích bận, thiếu tiết hoặc không liên tục | Tìm khung khác hoặc báo thiếu; không tạo lịch xung đột |
| Chia buổi | Tổng TH 7, 8, 9, 10, 12, 15, 30 | Đúng tổng, mỗi buổi 3–4 tiết; dùng các ví dụ ở mục 3.2 khi khả thi |
| Chia buổi | Tổng TH 1, 2, 5 | Báo rõ không thể chia thành buổi 3–4 tiết; tổng LT 5 vẫn có thể chia 3 + 2 ở hai ngày |
| Chia buổi | TH 12 tiết, chỉ có khung 3 tiết | Thử 4 buổi × 3 thay vì 3 buổi × 4 |
| Tổng ngày | Cùng lớp LT có 3 tiết sáng, đề xuất thêm 3 tiết chiều hoặc đổi GV | Bị loại vì tổng LT 6 tiết; thay GV/phòng không né được trần ngày |
| Tổng ngày | Cùng lớp LT có 2 tiết, đề xuất thêm 1 hoặc 2 tiết LT | Thêm 1 hợp lệ nếu các điều kiện khác thỏa; thêm 2 bị loại |
| Phân biệt LT/TH | Một buổi TH 4 tiết | Hợp lệ nếu các điều kiện khác thỏa, không bị bộ đếm trần LT chặn nhầm |
| Định danh | Hai môn đều có tên “Lớp 01”, hoặc LT/TH ở hai dòng riêng | Không cộng nhầm lớp; liên kết LT/TH đúng bằng định danh ổn định |
| Khoảng cách | Hai buổi chênh 6, 7, 13, 14 và 15 ngày | 6 ngày luôn bị loại; 7/13 chỉ hợp lệ ở lượt dự phòng; 14/15 hợp lệ ở cả hai lượt |
| Khoảng cách | Cả lịch tuần 1, 3, 5 và lịch tuần 1, 2, 3 đều đủ tiết | Chọn lịch tuần 1, 3, 5 ở lượt ưu tiên |
| Khoảng cách | Không đủ khoảng ngày hoặc phòng/giờ cho lượt 14 ngày, nhưng đủ với 7 ngày | Chạy lượt dự phòng, xếp đủ và lưu được; có ghi chú khoảng cách đã giảm |
| Khoảng cách | Lượt 14 ngày hết ngân sách; chỉ cần giảm một khoảng để xếp đủ ở lượt 7 ngày | Ghi rõ giới hạn tìm kiếm; không giảm hàng loạt các khoảng còn giữ được 14 ngày |
| Khoảng cách | Ngay cả khoảng cách 7 ngày cũng không đủ | Báo thiếu và không lưu; không tạo cặp buổi cách dưới 7 ngày |
| Khoảng cách | Request chọn rõ `ALTERNATE` | Giữ bộ lọc tuần đã chọn khi chạy cả hai lượt |
| Khoảng cách | Thứ Bảy tuần trước, thứ Hai tuần sau | Không được coi là đủ một tuần |
| Khoảng cách | Thử ngày theo thứ tự ngược; nhóm đã có lịch bổ sung | Kiểm tra đủ các buổi cũ/mới của đúng nhóm |
| Phạm vi | Tổ A và tổ B cùng môn do một GV dạy | Không áp khoảng cách ngày của tổ A sang tổ B |
| Gom môn | GV dạy cùng mã môn ở hai khóa, mỗi lớp 3 tiết khác giờ cùng ngày | Được phép; được ưu tiên hơn phương án phải đổi sang môn khác |
| Gom môn | Có lịch đủ với một môn cả ngày và lịch đủ với hai môn trong ngày | Ưu tiên một môn/ngày khi các mục tiêu trước ngang nhau |
| Gom môn | Buộc dạy hai môn/ngày, có thể tách sáng/chiều | Ưu tiên một môn/buổi, không xen A → B → A trong cùng buổi |
| Gom môn | Chỉ đủ khi đổi môn trong cùng buổi | Cho phép nới ưu tiên và ghi chú, vẫn đúng trần LT/ngày và độ dài TH/buổi |
| Gom môn | Hai lớp/nhóm TH cùng môn trong cùng ngày | Không bị chặn bởi đề xuất cũ một nhóm TH/GV/ngày; vẫn không trùng giờ |
| Giữ chỗ | Thử thất bại rồi đổi GV/ngày/cách chia | Các chỉ mục hoàn tác đúng, không ảnh hưởng nhóm tiếp theo |
| Hồi quy | LT, phòng LAB, lịch nhiều trường, trọng số 0, LT/TH khác thứ | Các điều kiện đang có vẫn được tôn trọng |
| Lưu/xuất | Đọc Excel → xếp → lưu → tải theo tuần → xuất Excel | Đúng tổng tiết, ngày, lớp/khóa; LT không quá 3 tiết/lớp/ngày, TH giữ buổi 3/4 tiết |
| Thỉnh giảng | Xếp bổ sung và di chuyển lịch cũ | Mọi lịch bị tác động vẫn đúng chính sách; thất bại không ghi dở dữ liệu |
| Hiệu năng | Nhiều lớp/nhóm, nhiều ứng viên, rất ít khung hợp lệ | Kết thúc trong ngân sách; cảnh báo phân biệt hết lượt tìm và không có khung hợp lệ |

Thêm kiểm thử backend tập trung cho chính sách, chia buổi, giữ chỗ và tích hợp lưu dữ liệu. Chạy kiểm thử frontend có liên quan khi thay giao diện/xuất Excel. Với thay đổi thuật toán, cần dữ liệu kiểm thử thể hiện điều kiện nghiệp vụ, không chỉ đối chiếu cấu trúc hàm.

## 6. Kiểm chứng bằng Docker khi triển khai

Theo [hướng dẫn Docker](../DOCKER.md), sau khi sửa và kiểm thử đạt:

1. Build lại backend/frontend cần thiết bằng Docker Compose.
2. Kiểm tra trạng thái dịch vụ và health.
3. Chạy bộ dữ liệu mẫu trong môi trường kiểm thử, đối chiếu lịch tuần và Excel xuất ra theo các ca nghiệm thu.
4. Xác nhận không thay đổi dữ liệu thật khi chỉ kiểm tra build/health. Chỉ ghi hoặc thay lịch thật trong phạm vi người dùng yêu cầu triển khai.

Kế hoạch không cần migration chỉ để kiểm tra tiết đầu và khoảng cách ngày. Chỉ thêm migration nếu bước triển khai xác định cần lưu cấu hình chính sách hoặc bổ sung định danh nhóm còn thiếu.

## 7. Thứ tự công việc đề xuất

1. Dùng tiết đích 7, TH ưu tiên 14/dự phòng tối thiểu 7 ngày, trần LT 3 tiết/lớp/ngày, TH 3–4 tiết/buổi và ưu tiên gom môn cho GV.
2. Viết kiểm thử tổng LT/ngày, TH 3–4 tiết/buổi, định danh lớp/nhóm và gom môn xuyên khóa.
3. Sửa chính sách, chỉ mục giữ chỗ, chấm điểm gom môn và thuật toán LT/TH theo tuần.
4. Đồng bộ thỉnh giảng, nhánh cũ còn sử dụng và kiểm tra trước lưu.
5. Đồng bộ giao diện, kiểm tra xuất Excel và hồi quy LT.
6. Kiểm chứng Docker và ghi nhận kết quả thực tế.

Hoàn thành khi mỗi lớp LT mới không quá 3 tiết/ngày, mỗi buổi TH dài 3–4 tiết, đúng tổng khối lượng và khoảng cách TH đã chốt, không có buổi nhiều tiết bắt đầu tiết 5, ưu tiên gom môn cho GV/ngày/buổi được thể hiện trong cách chọn phương án, và kết quả lưu/xuất khớp lịch đã kiểm tra. Phạm vi kiểm soát lớp phải đúng với định danh có trong dữ liệu; chưa coi là kiểm soát lớp hành chính xuyên môn nếu chưa bổ sung ánh xạ.

## 8. Bổ sung: tiếp tục xếp khi thiếu giảng viên và tải kết quả

Theo yêu cầu mới, thiếu GV là kết quả của từng lớp/nhóm, không phải lỗi làm hủy toàn bộ kế hoạch:

- [Đọc Excel](../server/src/main/java/com/service/WeeklyPlanExcelReader.java) giữ lại dòng chưa có GV với danh sách ứng viên rỗng. Các lỗi định dạng, ngày hoặc số liệu không hợp lệ vẫn được kiểm tra như trước.
- [Bộ xếp lịch](../server/src/main/java/com/service/WeeklyScheduleEngine.java) xử lý mọi lớp/nhóm. Chỉ giữ chỗ và lưu một lớp khi lớp đó đã xếp đủ tổng tiết; lớp thử dở được hoàn tác để không chặn giờ GV/phòng của lớp sau.
- Kết quả có `unscheduledClasses` với mã môn, tên học phần, khóa, lớp/nhóm, LT/TH, số tiết thiếu, mã lý do và mô tả. Phân biệt `NO_TEACHER`, `NO_ROOM` và `NO_AVAILABLE_SLOT`.
- Có lớp xếp đủ và có lớp chưa xếp: lưu những lớp đã xếp đủ, trả `saved=true`, `status=PARTIAL_SUCCESS`. Không có lớp nào xếp đủ: `saved=false`, `status=NOT_SAVED`, không thay lịch đang lưu. Lỗi vi phạm điều kiện bắt buộc trong kết quả vẫn chặn lưu.
- [Màn hình xếp lịch](../frontend/src/AutoSchedulePage.jsx) giữ kết quả mở sau khi chạy, hiển thị bảng lớp chưa xếp và lý do; hỗ trợ xem TKB của các lớp đã lưu.
- [Xuất Excel](../frontend/src/scheduleExport.js) có lịch đã xếp trong `MonHoc`, bảng giờ trong `TietHoc`, danh sách chưa xếp trong `LopChuaXep` và cảnh báo trong `CanhBao`. Vẫn tải được báo cáo khi toàn bộ lớp đều thiếu GV; không tạo buổi học giả cho các lớp này.
- Kiểm thử backend bao gồm đọc file thiếu GV nhưng môn khác vẫn được lưu, trọng số 0, hết giờ, thiếu phòng, hoàn tác lớp thử dở và toàn bộ lớp thiếu GV. Kiểm thử trình duyệt bao gồm trạng thái lưu một phần, không có phân công nào, bảng cảnh báo và tải/readback Excel trên desktop/mobile.

Kiểm chứng phần bổ sung: 6 ca backend qua; hai tình huống lưu một phần và toàn bộ lớp thiếu GV đều qua trên desktop và mobile, bao gồm tải và đọc lại file XLSX. Build frontend đạt. Kiểm thử trình duyệt dùng API mô phỏng, không ghi dữ liệu thử vào lịch khoa đang chạy. Người dùng xác nhận hai thay đổi là chủ đích: mẫu mở lớp tách dòng LT/TH với một cột số tiết, và ADMIN vào trang lịch giảng dạy mặc định. Đã cập nhật kỳ vọng kiểm thử tương ứng; toàn bộ 32/32 test frontend qua.
