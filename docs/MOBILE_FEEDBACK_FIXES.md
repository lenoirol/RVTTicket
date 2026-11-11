# Sửa lỗi theo 7 ảnh điện thoại — 03/10/2026

## Các thay đổi

1. **Đăng ký / 403:** đăng ký thành công tự đăng nhập và mở hồ sơ. Phiên được đổi ID,
   CSRF được tạo lại, yêu cầu cũ bị xóa. Đăng nhập luôn đi qua `/workspace` để chọn
   đúng khu vực theo vai trò, không quay lại trang quản trị đã lưu đối với khách.
2. **Tên tàu:** dữ liệu cũ có tên RVT được trình bày bằng tên tàu tham khảo theo tuyến:
   Thăng Long, Côn Đảo Express 36, Phú Quốc Express 7, Trưng Trắc, Phú Quốc Express 27.
   Có nhãn tham khảo; không giả định hãng đã xác nhận phương tiện hoặc sơ đồ 40 chỗ.
   Khi quản trị phân công phương tiện hợp lệ, tên được phân công được ưu tiên.
   Mã chuyến cũ có tiền tố DEMO/SEA được hiển thị cho khách dưới dạng `RVT-T<ID>`
   hoặc `RVT-B<ID>`; đây là mã chuyến của RVT, không phải số đăng ký tàu. Mã gốc
   trong database và các vé lịch sử không bị sửa.
3. **Hồ sơ hành khách:** V8 thêm ngày sinh, loại/số giấy tờ và quốc tịch. Lưu là tùy
   chọn. “Tôi là hành khách · Điền từ hồ sơ” điền vào đúng hành khách đang chọn;
   sửa bằng tay sẽ bỏ dấu tích. Không ghi giấy tờ vào localStorage. CCCD đúng 12
   chữ số, giữ số 0 đầu; hộ chiếu và khai sinh có quy tắc riêng trên cả máy chủ
   và trình duyệt. Ngày sinh không bị đẩy rộng khỏi biểu mẫu trên màn hình nhỏ.
4. **Thanh toán điện thoại:** radio cố định 20px; phần chữ co giãn độc lập. Lỗi do
   quy tắc `label > input` đặt radio rộng 100%, nay có quy tắc riêng đúng độ ưu tiên.
   Chưa tích hợp hoặc thu tiền qua cổng ngân hàng. “Thanh toán thử” vẫn được ghi
   rõ ở nơi chọn phương thức và chi tiết giao dịch, không giả thành thanh toán thật.
5. **Sau thanh toán:** thông báo ngắn “Vé đã được xác nhận…”, không còn câu
   “không thu tiền thật” lặp trong thông báo thành công. Chi tiết giao dịch vẫn
   phân biệt giao dịch thử và số dư RVT.
6. **Trang chủ:** slideshow Phú Quốc, Đà Lạt, Hạ Long, Đà Nẵng; tự chuyển mỗi 7 giây,
   nút trước/sau, chọn điểm đến, tạm dừng, thao tác vuốt và phím mũi tên. Tạm dừng
   khi người dùng tương tác, tab bị ẩn hoặc thiết bị bật giảm chuyển động. Khung
   tìm chuyến không che nút điều khiển slideshow. Ảnh có nguồn và giấy phép.
7. **Quản trị / câu chữ:** bỏ dòng “Bản đồ án · Thanh toán mô phỏng” ở thanh bên,
   bỏ nhãn đồ án ở footer, hồ sơ, chính sách và trang tuyến tàu. Giữ thông tin
   đúng về các giới hạn: chưa kết nối hãng / ngân hàng, không nạp hoặc rút tiền.

## Nguồn đối chiếu

- [Đội tàu Phú Quốc Express](https://phuquocexpress.com/doi-tau-phu-quoc-express).
- [Tuyến Phú Quý / Trưng Trắc](https://phuquocexpress.com/en/cang-phu-quy).
- [Phú Quốc Express 27 trên tuyến Lý Sơn](https://phuquocexpress.com/en/ly-son-garlic-is-famous).
- [Thông tin Chính phủ về số định danh cá nhân 12 chữ số](https://xaydungchinhsach.chinhphu.vn/quy-dinh-moi-tu-1-7-2025-su-dung-so-dinh-danh-ca-nhan-thay-cho-ma-so-thue-119241228125210708.htm).
- [Ảnh Hạ Long, Trideep Dutta, CC BY-SA 4.0](https://commons.wikimedia.org/wiki/File:HALONG_BAY_.jpg).
- [Ảnh Cầu Rồng, Christophe95, CC BY-SA 4.0](https://commons.wikimedia.org/wiki/File:Dragon_Bridge_Da_Nang_2.jpg).

## Kiểm tra

- 96 kiểm thử Java, bao gồm SQL Server thực: không lỗi, không bỏ qua.
- 13 kiểm thử JavaScript: không lỗi.
- HTTP: đăng ký → tự đăng nhập → lưu đầy đủ hồ sơ → tạo đơn giữ chỗ thành công,
   không gặp 403. Dùng tài khoản và giấy tờ giả dành riêng cho kiểm thử.
- Trình duyệt 390px: tự điền đủ thông tin; CCCD pattern `[0-9]{12}`; ô ngày sinh
   rộng 316px, trang rộng đúng 390px. Radio 20px và phần chữ 256px, không tràn ngang.
- Ảnh kiểm tra nằm trong `docs/review/mobile-payment-fixed.png`,
   `mobile-passenger-autofill.png`, `mobile-destination-carousel.png` và
   `mobile-admin-sidebar-fixed.png`.
- Trang chủ 320px rộng đúng 320px, không tràn ngang; đáy điều khiển slideshow
   nằm trước khung tìm chuyến 44px. Cả 4 ảnh tải thành công. Quản trị đăng nhập,
   mở / đóng menu điện thoại và đăng xuất thành công.
- Đã dọn đúng tài khoản và đơn giả dành riêng cho kiểm thử, không xóa khách,
   đơn vé, phương tiện hoặc chuyến có sẵn. Dữ liệu giả có thể tạo lại theo quy trình.
- Đây là kiểm tra trình duyệt ở kích thước điện thoại, không phải chạy trực tiếp
   Safari trên chiếc iPhone của người dùng. Cần người dùng thử lại trên thiết bị.

## Bước tiếp theo

Thống nhất cổng thanh toán, môi trường thử / chính thức và đối soát trước khi
triển khai thu tiền. Không cho nút bấm phía khách tự quyết định giao dịch ngân
hàng thành công. Cần xác thực thông báo từ cổng, kiểm tra mã đơn/số tiền, xử lý
gửi lặp, hết hạn giữ chỗ và tiền đến sau khi hết hạn.
