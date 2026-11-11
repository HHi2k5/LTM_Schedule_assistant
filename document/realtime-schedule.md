# Cập nhật lịch giảng dạy realtime

Khi admin lưu/xếp lại, bổ sung hoặc xóa lịch, trang giảng viên đang mở tự đọc lại lịch đã lưu. Tuần đang chọn và bộ lọc tìm kiếm được giữ; trang Hôm nay cũng cập nhật. Các thay đổi phòng phát sinh khi xếp giảng viên thỉnh giảng được thông báo cho cả GV bị điều chỉnh phòng.

Sau khi cài bản nâng cấp, tải lại trang một lần để nạp mã frontend mới. Các lần thay đổi lịch tiếp theo không cần F5.

## Luồng hoạt động

1. Frontend đăng ký `GET /api/schedule/events` bằng Bearer token trong header `Authorization`.
2. [ScheduleChangePublisher](../server/src/main/java/com/service/ScheduleChangePublisher.java) gom mã GV bị ảnh hưởng trong transaction. Chỉ phát thông báo sau commit; rollback không phát thông báo.
3. [ScheduleEventBroker](../server/src/main/java/com/service/ScheduleEventBroker.java) gửi sự kiện `schedule-changed` đến các kết nối của GV đó và các ADMIN. Sự kiện chỉ chứa số phiên bản, không chứa nội dung lịch hoặc thông tin GV khác.
4. [Kết nối frontend](../frontend/src/scheduleRealtime.js) gộp thông báo gần nhau và kích hoạt đọc lại API lịch tuần bằng quyền của tài khoản đang đăng nhập. Màn hình nhận dữ liệu mới mà không chuyển trang.

Các điểm ghi lịch được nối vào cơ chế này: nhập lịch Excel cá nhân, xếp lịch toàn khoa, thay thế lịch tự động, bổ sung thỉnh giảng, đổi phòng liên quan, xóa lịch một GV và xóa lịch khoa. Sửa danh mục GV/phòng không tự được coi là sửa lịch nếu không thay đổi buổi học đã lưu.

## Kết nối và phân quyền

- GV không chọn được mã người khác để nghe thông báo; server lấy danh tính từ tài khoản đã xác thực. ADMIN nhận thông báo thay đổi lịch toàn khoa.
- Không truyền JWT qua query string. API đọc lịch hiện có tiếp tục kiểm tra quyền truy cập dữ liệu.
- Server gửi heartbeat mỗi 15 giây. Kết nối được đóng theo hạn JWT hoặc tối đa 5 phút để lần kết nối tiếp theo kiểm tra lại xác thực.
- Khi mất kết nối, frontend thử lại với thời gian chờ tăng từ 1 đến 15 giây. Mỗi lần nối lại thành công đều đọc lại dữ liệu, kể cả khi thông báo bị bỏ lỡ trong thời gian offline.
- Kết nối không có dữ liệu trong 45 giây được hủy để thử lại. Quay lại tab hoặc kết nối mạng trở lại cũng kích hoạt đồng bộ.
- Đăng xuất đóng kết nối và hủy các lần thử lại. HTTP 401 kết thúc phiên đăng nhập; HTTP 403 không tự xóa phiên.
- Ghi ra socket chạy ngoài luồng lưu lịch; một trình duyệt bị ngắt không làm hỏng việc lưu của admin hoặc chặn GV khác.
- Khi backend dừng, kết nối được hoàn tất trước khi web server đóng; frontend tự nối lại khi server sẵn sàng. Các tác vụ hẹn giờ của kết nối đã đóng được hủy và giải phóng ngay.

## Docker/Nginx

[Nginx](../frontend/nginx.conf) có cấu hình riêng cho `/api/schedule/events`: HTTP/1.1, tắt buffering/cache/gzip và timeout đọc 90 giây. API thông thường giữ cấu hình hiện có.

Tham khảo API nền tảng: [Spring SseEmitter 6.1.14](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/web/servlet/mvc/method/annotation/SseEmitter.html), [callback transaction sau commit](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/transaction/support/TransactionSynchronization.html), [cấu hình buffering của Nginx](https://nginx.org/en/docs/http/ngx_http_proxy_module.html#proxy_buffering).

Cơ chế phát thông báo hiện nằm trong một tiến trình backend, phù hợp cấu hình Docker Compose đang dùng một backend. Nếu triển khai nhiều bản sao backend, cần thêm kênh pub/sub dùng chung giữa các bản sao để thông báo đến mọi kết nối.

## Kiểm thử

- Backend: kiểm tra gửi sau commit, không gửi khi rollback, gom thông báo trong transaction, transaction lồng nhau, phân quyền, JWT hết hạn, ngắt kết nối và chỉ báo cho đúng người.
- Tích hợp: HTTP SSE thật kết hợp database H2 ở chế độ MySQL, kiểm tra lưu/đổi phòng/xóa lịch và API tuần trả dữ liệu sau commit. Không dùng database của khoa đang chạy.
- Frontend: parser phân mảnh CRLF/UTF-8, reconnect, heartbeat, token đổi, đăng xuất và tránh reconnect từ phiên đã kết thúc.
- Trình duyệt: hai tài khoản admin/GV; admin xếp và xóa lịch, GV thấy thay đổi nhưng giữ tuần/bộ lọc và không tải lại document. Có ca trang Hôm nay, mất kết nối rồi đồng bộ lại, và đóng kết nối khi đăng xuất; chạy trên desktop/mobile.

Lệnh trong môi trường dev:

```text
server:   mvnw.cmd test
frontend: node --test --test-concurrency=1 src/*.test.js
frontend: npm.cmd run test:browser -- tests/realtime.spec.js --workers=1
```
