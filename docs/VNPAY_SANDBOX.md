# VNPay Sandbox cho RVT Ticket

## Trạng thái

Đã triển khai tạo yêu cầu thanh toán, IPN, trang kết quả và màn hình quản trị `/admin/vnpay`.
Đã bật cấu hình merchant Sandbox và Tailscale Funnel sau khi chủ máy phê duyệt. Khóa Sandbox được dùng tạm theo yêu cầu của chủ máy, chỉ lưu trong file riêng; nên xin cấp lại trước khi chia sẻ dự án.
Chưa xác nhận đăng ký IPN với VNPay. Hai giao dịch 425.000đ và 216.000đ do chủ máy thanh toán đã được đối chiếu querydr và cấp vé. Bản mới phân biệt nguồn IPN/QUERYDR; bản ghi cũ được đánh dấu HISTORICAL, không suy đoán lại nguồn bằng tên cột. Không mở cổng router.

Đây là kết nối Sandbox, không phải thu tiền thật hoặc chứng nhận sẵn sàng vận hành thương mại.

## Cấu hình riêng trên máy

Mở `.local/vnpay.properties` ở thư mục project; file này bị Git bỏ qua và không nằm trong JAR.
Khi chuyển sang máy khác hoặc đổi khóa, thay các giá trị tương ứng:

```properties
rvt.vnpay.enabled=false
rvt.vnpay.tmn-code=MA_WEBSITE_TRONG_MAIL
rvt.vnpay.hash-secret=KHOA_MOI_NHAP_TREN_MAY
rvt.vnpay.public-base-url=https://ten-mien-cua-ban
```

Chỉ đổi `enabled=true` khi URL nhận kết quả đã sẵn sàng. Khởi động lại ứng dụng để áp dụng.
Nếu dùng IntelliJ, working directory phải là thư mục RVTTicket; chạy JAR cũng từ thư mục này.
Cũng có thể dùng các biến môi trường `VNPAY_ENABLED`, `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET`, `VNPAY_PUBLIC_BASE_URL` nếu giữ các biểu thức mặc định trong file riêng.
Không đặt secret trong HTML, JavaScript, ảnh chụp, báo cáo, lệnh chia sẻ hoặc file cấu hình được commit.
Không gửi file `.local` khi nộp bài; cung cấp `.env.example` trống và hướng dẫn cấu hình.

## Địa chỉ cần đăng ký

Với URL gốc `https://ten-mien-cua-ban`:

- Return URL: `https://ten-mien-cua-ban/thanh-toan/vnpay/return`
- IPN URL: `https://ten-mien-cua-ban/thanh-toan/vnpay/ipn` (GET, JSON)

Tạo endpoint xong mới gửi IPN URL cho VNPay theo email hướng dẫn. Không tự đoán rằng IPN đã được đăng ký.
IP riêng `192.168.1.35` hoặc IP Tailscale `100.112.242.8` không phải URL công khai để máy chủ VNPay gọi vào.
Nếu dùng Tailscale Funnel, cần phê duyệt việc công khai và giới hạn proxy cho callback trước khi bật; không đưa toàn bộ cổng 8080, SQL Server hay admin mật khẩu đơn giản ra Internet.
Chưa cấu hình trusted proxy nên ứng dụng lấy IP từ kết nối trực tiếp, không tin tùy ý `X-Forwarded-For`.

### Cấu hình đang chạy trên máy Mac

- IPN cần gửi VNPay: `https://phus-macbook-air.tailebd5c8.ts.net/thanh-toan/vnpay/ipn`
- Return: `https://phus-macbook-air.tailebd5c8.ts.net/thanh-toan/vnpay/return`
- Website riêng: `http://100.112.242.8:8080` (điện thoại cần bật Tailscale).

Relay chỉ lắng nghe tại `127.0.0.1:8091`, cho phép GET đúng hai đường dẫn callback. Các trang web/admin/account bị trả 403 trên tên miền công khai. IPN chuyển vào backend nội bộ, không chuyển cookie hoặc header đăng nhập. Return công khai chuyển trình duyệt về website Tailscale riêng; không xác nhận thanh toán bằng Return.

Từ bản sửa ngày 04/10, yêu cầu thanh toán mới dùng Return trực tiếp về đúng origin đã đăng nhập (`localhost`, IP LAN hoặc IP Tailscale) để không mất cookie khi đổi host. Các origin phải liệt kê rõ trong `rvt.vnpay.return-origins` (phân cách dấu phẩy); không tin `X-Forwarded-Host` hay URL tùy ý. Callback IPN vẫn dùng HTTPS Funnel. Cookie phiên đặt SameSite=Lax cho điều hướng GET từ VNPay. Link thanh toán tạo trước bản sửa vẫn dùng Return cũ; không tạo hoặc trả tiền lần hai để sửa phiên.

Các thuộc tính relay trong file riêng:

```properties
rvt.vnpay.relay.enabled=true
rvt.vnpay.relay.port=8091
rvt.vnpay.relay.private-base-url=http://100.112.242.8:8080
```

Funnel đang trỏ đúng relay: `tailscale funnel --bg --https=443 http://127.0.0.1:8091`.
Xem trạng thái: `tailscale funnel status`. Tắt khi không sử dụng: `tailscale funnel --https=443 off`.
Không trỏ Funnel vào cổng 8080. Máy Mac, ứng dụng và kết nối mạng phải hoạt động để nhận IPN; không để máy ngủ trong lúc kiểm thử.
Chuyển project không đồng nghĩa chuyển địa chỉ Funnel: bạn của bạn cần tự cấu hình merchant, relay và URL, hoặc dùng website riêng trên máy Mac này qua Tailscale.

## Luồng đã triển khai

1. Khách đăng nhập, tạo đơn giữ chỗ, chọn VNPay. Số tiền lấy từ dữ liệu đơn, bao gồm hành lý; không nhận giá từ trình duyệt.
2. Tạo tham chiếu riêng, ký HMAC-SHA512, chuyển hướng sang Sandbox. Bấm lặp tái sử dụng yêu cầu đang chờ, không tạo nhiều giao dịch đang còn hiệu lực.
3. Thời hạn VNPay kết thúc trước thời hạn giữ chỗ 30 giây; không kéo dài quyền giữ ghế.
4. Return URL chỉ đọc trạng thái, không ghi nhận thanh toán. Chỉ chủ đơn đã đăng nhập mới được mở chi tiết vé từ trang này.
5. IPN kiểm tra chữ ký, merchant, tham chiếu và số tiền. Chỉ khi cả hai mã kết quả là `00` mới xem là thành công.
6. Trong cùng một giao dịch SQL, khóa chuyến rồi đơn rồi yêu cầu VNPay; cập nhật toàn bộ vé và biên nhận. Callback lặp không thu/ghi nhận lần hai.
7. Nếu thành công đến sau khi đơn hết hạn/hủy hoặc đã trả bằng cách khác, ghi `REVIEW`. Không cấp lại ghế và không tự hoàn. Quản trị đối chiếu `/admin/vnpay` với Merchant, ghi lý do và chọn hoàn RVT. Backend chỉ cho phép giao dịch đã xác minh thành công, có mã VNPay; khóa SQL và mã sự kiện riêng chống hoàn trùng. Không gọi API hoàn về ngân hàng. Đơn cũ chưa có chủ tài khoản được lưu khoản hoàn chờ nhận, không biến thành số dư đã cộng.
8. IPN chưa hoàn tất vẫn chờ; trạng thái đảo/hoàn/nghi vấn cần đối soát, không coi là thành công hay tự tạo tiền hoàn.

Thanh toán trực tiếp `DEMO` bị ẩn và chặn ở cả checkout và màn hình quản trị mặc định. Chỉ có thể bật riêng cho kiểm thử nội bộ bằng `RVT_ALLOW_DEMO_PAYMENT=true`; không dùng khi trình diễn kết nối VNPay.
Số dư RVT giữ nguyên quy trình hiện có. Khi một yêu cầu VNPay chưa hết hiệu lực, không cho đổi phương thức để tránh thanh toán kép.
Hủy đơn vẫn được phép theo chính sách; kết quả VNPay đến sau hủy được đưa vào nhóm cần đối chiếu.

## Kiểm thử và giới hạn

Có bài kiểm thử chữ ký theo RFC 4231, mã hóa URL, số tiền, merchant sai, chữ ký sai, tham chiếu sai, callback lặp, thông báo lỗi, hết hạn, hủy, trang Return chỉ đọc, quyền khách/admin và hai kết nối SQL xử lý đồng thời.
Các callback trong bài kiểm thử được ký bằng khóa kiểm thử do project tạo, không phải kết quả thật của VNPay. Riêng giao dịch Sandbox 425.000đ ngày 04/10 đã nhận phản hồi querydr thật từ máy chủ VNPay, kiểm tra chữ ký thành công và cấp vé; không tự tạo callback để xác nhận đơn này.
Các bài kiểm thử tự rollback hoặc chỉ dọn fixture có mã riêng, không xóa đơn/vé của người dùng.

Sau khi cấu hình, cần chạy bằng dữ liệu thanh toán test do VNPay cung cấp: thành công, hủy, thất bại, hết hạn, chờ IPN và đóng trình duyệt trước khi quay về. Kiểm tra đồng thời Merchant Admin, bảng giao dịch RVT và vé đã xác nhận.
Không nhập thẻ/ngân hàng thật trong môi trường này.
Đã bổ sung truy vấn máy chủ `querydr` khi `rvt.vnpay.query.enabled=true`. Mỗi vòng 30 giây nhận tối đa 10 yêu cầu đến lịch, đặt lease SQL nguyên tử và giãn lần thử 30 giây đến 30 phút. Giao dịch chưa có kết quả sau 24 giờ được đưa vào nhóm Cần xử lý, không âm thầm bỏ quên hoặc đánh dấu thất bại. Admin có thể truy vấn lại, giới hạn một lần mỗi 5 phút. Không giữ khóa SQL khi gọi mạng. Phản hồi phải có chữ ký HMAC đúng, khớp merchant/tham chiếu/số tiền/loại thanh toán; mã API `00` không được xem là thanh toán thành công nếu trạng thái giao dịch không phải `00`. Trạng thái đang xử lý/hoàn/đảo/nghi vấn không tự cấp vé. Kết quả hợp lệ đi qua cùng kiểm tra giao dịch SQL, trạng thái đơn/chỗ, thời hạn và chống ghi nhận trùng như IPN. Trang Return chỉ đọc và tự kiểm tra trạng thái mỗi 15 giây tối đa 5 phút.
IPN vẫn cần đăng ký với VNPay. Hoàn ngoại lệ cần admin đối chiếu và xác nhận; không tự động hoàn theo thao tác quay lại trình duyệt. Không dùng cấu hình thử nghiệm này thu tiền thật.

Kết quả regression của bản mới và giới hạn kiểm tra: [FIXES_2026_10_04.md](FIXES_2026_10_04.md). Các số kiểm thử bên dưới là lịch sử các bản trước, không phải tổng của bản hiện tại.

## Lịch sử kiểm tra các bản trước ngày 04/10/2026

- Bản đóng gói tại thời điểm tích hợp ban đầu: 157 kiểm thử Java, 0 lỗi/0 bỏ qua; thêm 6 kiểm thử chữ ký/trạng thái truy vấn và allowlist Return, 1 kiểm thử SQL giữ tham chiếu và Return đúng origin.
- 15 kiểm thử JavaScript đạt. Màn hình quản trị và trang kết quả được mở trong trình duyệt; kiểm tra độ rộng điện thoại 390 px không tràn ngang.
- Sao lưu COPY_ONLY/CHECKSUM trước thay đổi, kiểm tra RESTORE VERIFYONLY thành công. File nằm trong container `sqlserver`: `/var/opt/mssql/data/RVTTicket_before_vnpay_20261004.bak`. Không chia sẻ backup vì chứa dữ liệu riêng.
- Fingerprint và số hàng trước/sau trên DonDatVe (4), DangKyVe (6), ThanhToan (4), GiaoDichRVT (1) giữ nguyên; không còn fixture VNPay hoặc yêu cầu thanh toán thử trong database.
- Funnel đã bật và kiểm tra HTTPS: IPN không có chữ ký trả `97`, `/admin` trả 403, Return trả 302 về website riêng. Đây là kiểm tra kết nối, không phải giao dịch thanh toán thành công.
- Merchant Sandbox đã bật trên máy này. Giao dịch Sandbox 425.000đ đã được xác nhận qua querydr; chưa xác nhận đăng ký/nhận IPN từ VNPay. Các kịch bản ngân hàng từ chối, người dùng đóng tab và hủy tại gateway vẫn cần kiểm thử thực tế riêng.
- Khi chạy lại bộ kiểm thử, dùng `./mvnw -Drvt.vnpay.query.enabled=false -Drvt.vnpay.relay.enabled=false -Drvt.vnpay.enabled=false test` để tránh tranh cổng 8091 và không gọi VNPay trong kiểm thử. Các bài kiểm thử VNPay tự dùng cấu hình riêng.
- Ảnh giao diện: `docs/review/vnpay-admin-laptop.png`, `docs/review/vnpay-admin-mobile.png`.

## Tài liệu chính thức

- [Hướng dẫn PAY, Return và IPN của VNPay](https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html)
- [HMAC-SHA512 và phiên bản 2.1.0](https://sandbox.vnpayment.vn/apis/docs/chuyen-doi-thuat-toan/changeTypeHash.html)
- [Tailscale Funnel và quyền truy cập công khai](https://tailscale.com/docs/features/tailscale-funnel)

## Triển khai Render (11/11/2026)

- Website: `https://rvtticket.onrender.com` (Render Free, Frankfurt). Merchant Sandbox mới `ON9Z9THO` đăng ký đúng tên miền này; mã cũ gắn với tên miền Tailscale nên VNPay trả lỗi 71 "Website này chưa được phê duyệt".
- Từ Render (Đức), gọi `querydr` tới sandbox VNPay bị `HttpConnectTimeoutException` (từ máy ở Việt Nam thì kết nối được). IPN chưa được VNPay đăng ký.
- Vì vậy bật `VNPAY_RETURN_SETTLES=true`: trang Return có chữ ký HMAC hợp lệ đi qua đúng hàm `settle` như IPN (chữ ký, merchant, tham chiếu, số tiền, cả hai mã `00`, mã giao dịch VNPay chưa dùng, đơn còn giữ chỗ), khóa SQL như cũ, ghi `NguonXacNhan='RETURN'`. Return/IPN lặp lại trả `02`, không ghi nhận hai lần. Mặc định `false` để giữ nguyên thiết kế chỉ tin IPN/querydr.
- Giới hạn: nếu khách đóng trình duyệt trước khi về Return thì vẫn cần IPN (đã đề nghị đăng ký `https://rvtticket.onrender.com/thanh-toan/vnpay/ipn`) hoặc đối soát thủ công.
- Kiểm thử SQL `VnpayReturnSettlementIntegrationTests` cần `RVT_SQL_TEST=true` và SQL Server; chưa chạy được trong lần triển khai này.
