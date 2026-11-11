# RVT Ticket

Ứng dụng quản lý đặt vé xe khách và vé tàu/thuyền, xây dựng bằng Spring Boot,
Spring MVC, Thymeleaf và SQL Server. Giao diện tiếng Việt, xanh dương/cam,
Product Sans; logo SVG nằm trong `static/images/logo.svg`.

Bản sửa tính toàn vẹn ngày 04/10/2026: [kết quả, quy trình quản trị và điều kiện còn phải xác nhận](docs/FIXES_2026_10_04.md).
Ứng dụng tự nâng cấp V2–V10 khi khởi động, không yêu cầu nạp lại database cũ.
Chuyến mới luôn đóng bán; phải phân công phương tiện, rà soát sơ đồ/lịch/giá và mở bán riêng.

## Yêu cầu môi trường

- JDK 21 trở lên (project biên dịch Java 21). Nếu máy có nhiều JDK, đặt JAVA_HOME trỏ tới JDK 21 trước khi chạy `mvnw`.
- Docker Desktop và container SQL Server mở cổng `1433`.
- Database `RVTTicket` đã được tạo từ `database/01_base_schema.sql` (chỉ chạy
  script này trên database mới; máy hiện tại đã có database nên không cần nạp lại).

## Chạy bằng IntelliJ IDEA

1. Mở project và chờ IntelliJ tải các Maven dependency.
2. Mở **Run > Edit Configurations**.
3. Chọn cấu hình `RvtTicketApplication`.
4. Thêm biến môi trường `DB_PASSWORD` với mật khẩu SQL Server cục bộ.
   Có thể thêm `ADMIN_PASSWORD` để chọn mật khẩu admin ở lần khởi tạo đầu tiên.
   Bản đồ án mặc định `admin / 123456`; sau khi đã tạo, đổi mật khẩu trong trang
   quản trị, không thay bằng biến môi trường và không dùng mật khẩu đơn giản khi
   đưa website lên Internet. Không còn tài khoản nhân viên.
5. Chạy `RvtTicketApplication` và truy cập `http://localhost:8080`.

Các biến cấu hình hỗ trợ được liệt kê trong `.env.example`. Không commit mật khẩu
hoặc file `.env` vào Git. Spring Boot không tự đọc `.env`; nhập các biến trong
Run Configuration của IntelliJ. Database SQL Server Docker hiện dùng UTC;
ứng dụng lưu thời gian UTC và hiển thị/nhập giờ Việt Nam (UTC+7).

## Chức năng đã triển khai

- Trang chủ với slideshow Phú Quốc, Đà Lạt, Hạ Long, Đà Nẵng; ảnh có nguồn/giấy
  phép, nút trước/sau, tạm dừng và giảm chuyển động. Tìm chuyến theo loại vé,
  điểm đi/đến và ngày.
- Danh mục 34 tỉnh/thành hiện hành và 185 địa điểm ban đầu: đô thị, điểm du lịch,
  bến xe/cảng. Ô gợi ý tìm được không dấu, chọn cả tỉnh hoặc từng địa điểm.
- Quản trị thêm địa điểm mới tại `/admin/dia-diem`. Danh mục tách khỏi lịch chuyến:
  chọn địa điểm không đồng nghĩa có vé bán. Không tự sinh chuyến toàn quốc.
- Lọc theo số chỗ, khung giờ; sắp xếp giờ khởi hành, giá hoặc thời gian đi.
- Chi tiết điểm đón/trả, tiện ích và lưu ý chuyến; quản trị cập nhật được.
- Chọn ghế/giường theo tầng, chỗ phổ thông/VIP; tối đa 8 người mỗi đơn.
- Quản lý phương tiện tại `/admin/phuong-tien`: số đăng ký, đơn vị vận chuyển,
  sơ đồ riêng với mã/loại/phụ thu/vị trí chỗ và ô trống cho lối đi. Biên tập tối đa
  3 tầng/khoang, 30 hàng, 6 cột vị trí, 300 chỗ; sơ đồ được sao chép vào chuyến.
- Tạo chuyến chọn phương tiện đã lưu; kiểm tra trùng lịch trong transaction khóa
  phương tiện. Không sửa phương tiện khi còn chuyến được phân công chưa kết thúc.
- Khách phải đăng nhập trước khi đặt; đơn thuộc đúng tài khoản. Xe khách giữ một
  điểm đi–một điểm đến, không bán chặng trung gian hay nối chuyến.
- Nhập họ tên từng hành khách; vé tàu thêm ngày sinh, loại/số giấy tờ (CCCD,
  hộ chiếu hoặc khai sinh cho trẻ em), quốc tịch. Tuổi tính tại ngày đi ở Việt Nam.
- Xem lại thông tin trước khi giữ chỗ, giữ dữ liệu hành khách khi đổi lựa chọn;
  báo lỗi từng ô bằng tiếng Việt, tự điền thông tin người đặt đã đăng nhập, cảnh
  báo rời form có dữ liệu chưa gửi (không lưu dữ liệu cá nhân vào localStorage).
- Đăng ký thành công tự đăng nhập và mở hồ sơ; đổi ID phiên, tạo lại CSRF. Đăng
  nhập đi qua `/workspace` theo vai trò, không quay lại trang quản trị của yêu
  cầu cũ. Mật khẩu BCrypt; lịch sử đơn thuộc tài khoản (không tự gắn qua số điện thoại).
- Hồ sơ khách, đổi mật khẩu kèm xác minh mật khẩu cũ; kết thúc các phiên cũ khi
  đổi mật khẩu. Sửa hồ sơ không sửa thông tin người đặt trên vé đã tạo.
- Lịch sử đơn tìm theo mã/hành trình, lọc trạng thái và phân trang 10 đơn. Ô tìm
  chuyến có nút đổi chiều và gợi ý ngày còn bán lấy từ chuyến/chỗ thực trong SQL.
- Mỗi biểu mẫu đặt có khóa yêu cầu riêng để gửi lại cùng yêu cầu không tạo đơn
  thứ hai. Sơ đồ cập nhật chỗ mỗi 20 giây; giữ tên/giấy tờ khi có chỗ xung đột.
- Giá vé + phụ thu chỗ + phí hành lý, lưu giá tại thời điểm đặt.
- Giữ chỗ 15 phút, tự giải phóng mỗi phút; chống trùng chỗ bằng transaction,
  khóa chuyến và unique index SQL Server.
- Thanh toán qua **VNPay** hoặc **Thanh toán bằng số dư RVT**; hủy toàn bộ đơn.
  Hủy trước ít nhất 24 giờ (chưa có khách lên): hoàn 100% tiền thực đã thanh toán,
  gồm phí hành lý, vào số dư RVT của chủ đơn. Quản trị hủy chuyến trước khởi hành
  hoàn toàn bộ không phụ thuộc mốc 24 giờ. Đơn chưa trả tiền không sinh tiền hoàn.
- `/tai-khoan/so-du`: số dư và lịch sử cộng/trừ có phân trang. Không nạp/rút/
  chuyển tiền thật, không tự cấp số dư. Khóa tài khoản, giao dịch nguyên tử và
  mã sự kiện duy nhất chống chi vượt số dư, thanh toán lặp và hoàn lặp.
- Pop-up sau thao tác, mục thông báo lưu trong SQL (giữ chỗ, thanh toán, hủy,
  hết hạn). Khi đang mở trang, kiểm tra cập nhật mỗi 20 giây; không gửi email/SMS
  hoặc thông báo hệ điều hành. Hỗ trợ bàn phím và giảm chuyển động.
- In/lưu PDF qua trình duyệt.
- Trang đơn có giờ nên đến bến/cảng, hạn hủy cụ thể, lịch sử thao tác, biên nhận
  thanh toán và tiền hoàn. Lưu lịch khởi hành `.ics` cho đơn đã xác nhận; chỉ chủ
  đơn được tải. Lịch tải về không tự đổi khi chuyến bị hủy.
- Yêu cầu hỗ trợ trong tài khoản: gửi vấn đề, gắn đơn đúng chủ, trao đổi với
  admin, theo dõi chờ phản hồi/đang xử lý/đã giải quyết và mở lại khi hỏi thêm.
  Admin có hàng đợi hỗ trợ; phản hồi tạo thông báo nội bộ, không gửi email/SMS.
- `/huong-dan`, `/bao-mat`: hướng dẫn thao tác và mô tả dữ liệu/phạm vi dịch vụ
  đúng chức năng hiện có. Không có hotline, chứng nhận hay phản hồi khách giả.
- Admin xuất danh sách hành khách CSV theo chuyến; không xuất số giấy tờ/ngày
  sinh/điện thoại. Chuỗi do khách nhập được vô hiệu hóa công thức bảng tính.
- Tra cứu bằng mã đơn và số điện thoại; quyền xem đơn được lưu trong session.
- Quản trị có giao diện riêng: thanh bên, bộ lọc rõ ràng, bảng/phân trang 20 bản
  ghi và các trang chi tiết. `/admin` ưu tiên chuyến trong ngày, hành khách chưa
  lên trên chuyến chưa hoàn thành, đơn tạo trong ngày và thu–hoàn theo ngày thực
  tế. Các chỉ số không giới hạn 100 đơn.
- `/admin/chuyen-di`: tìm/lọc chuyến, danh sách hành khách, mở/đóng bán, xem tác
  động trước khi hủy chuyến. Tạo 1–31 ngày liên tiếp cùng giờ, xem lại trước khi
  lưu; chuyến mới đóng bán. Nếu một chuyến bị lỗi, cả đợt được rollback. Sao chép
  giữ thông tin dịch vụ nhưng dùng mã mới, thời gian mới, sơ đồ từ phương tiện
  hiện tại và kiểm tra lại lịch trùng; không sao chép đơn hay hành khách.
- `/admin/khach-hang`: tìm tài khoản, xem lịch sử đặt vé, khóa có lý do/mở khóa.
  Khóa không hủy hoặc xóa vé; đăng nhập mới bị chặn, phiên cũ kết thúc ở yêu cầu
  tiếp theo. Tra cứu vé không có tài khoản vẫn hoạt động. Không xem mật khẩu.
- `/admin/tai-khoan`: đổi mật khẩu đã mã hóa BCrypt trong SQL Server; không in
  mật khẩu lúc khởi động, không đặt lại khi khởi động lại. `/admin/nhat-ky` lọc
  ngày/thao tác/từ khóa, lưu lý do khóa trong nhật ký, không có nút xóa/sửa.
- Thống kê `/admin/thong-ke` theo khoảng ngày (tối đa 366 ngày), loại vé, tiền
  thu/hoàn/chênh lệch, số vé, tỷ lệ lấp đầy; xuất CSV; không giới hạn
  100 đơn. Tiền hoàn tính theo ngày hoàn. Hoàn cũ thiếu ngày được báo riêng.
- Trạng thái hành trình sắp đi/đang đi/hoàn thành/hủy; toàn bộ hành khách check-in
  thì đơn chuyển đã sử dụng. Quản trị hủy chuyến chưa đi, ghi lý do và hoàn giao
  dịch liên quan, không áp dụng giới hạn hủy tự nguyện của khách.
- Admin tại `/admin/don-ve`: tìm đơn, theo dõi thanh toán/hủy, xác nhận từng hành khách
  lên xe/tàu từ 2 giờ trước đến 2 giờ sau khởi hành (chuyến chưa hoàn thành/hủy);
  nhật ký thao tác quản trị. Hủy ngoại lệ trước khởi hành phải có lý do và chưa
  có hành khách lên; có thể vượt giới hạn hủy tự nguyện của khách.
- QR riêng từng vé đã thanh toán tại trang đơn/in vé; admin kiểm tra tại
  `/admin/kiem-ve`: camera nếu trình duyệt hỗ trợ hoặc tải PNG/JPEG để giải mã
  bằng ZXing 3.5.4. QR dùng token ngẫu nhiên 256 bit, không chứa thông tin cá nhân;
  chỉ quản trị được tra cứu. URL `/staff/**` cũ cũng chỉ cho ADMIN truy cập để
  tương thích liên kết cũ; không còn vai trò đăng nhập STAFF. Quét không tự check-in; POST xác nhận có
  CSRF, kiểm tra lại trạng thái và ngăn dùng lại. Ảnh QR tối đa 4 MB/12 MP.
- Animation/transition cho nút, thẻ chuyến, ghế/giường, hành khách mới, tổng tiền,
  thông báo, hộp xác nhận và dấu kiểm thành công; tôn trọng `prefers-reduced-motion`.
  Nội dung và nút đặt vé không chờ hiệu ứng cuộn.

Quản trị đăng nhập bằng `admin / 123456` ở lần khởi tạo bản đồ án mặc định; mật
khẩu đã đổi trên giao diện sẽ được giữ nguyên. `ADMIN_PASSWORD` chỉ được đọc để
tạo tài khoản khi chưa có. Khách tự đăng ký tại `/dang-ky`. Không dùng mật khẩu
SQL Server làm mật khẩu tài khoản web. Ứng dụng này chưa đủ bảo mật để triển khai
công khai với mật khẩu `123456`; khi triển khai phải đổi mật khẩu quản trị và cấu
hình khóa VNPay riêng ngoài mã nguồn.

## Database nâng cấp

Lần chạy đầu, ứng dụng bổ sung `DonDatVe`, `DonDatVeChiTiet` và `SoDoCho` từ
`db/migration/V2__orders_and_layout.sql`. Những bảng và dữ liệu cũ được giữ lại.
`DangKyVe` tiếp tục là vé của một hành khách; `DonDatVeChiTiet` gom các vé vào đơn.
Vé cũ không tự động chuyển thành đơn mới.

`V3__operations.sql` bổ sung `ThongTinChuyen`, `QuyTacVe`, `TaiKhoanKhach`,
`NhatKyThaoTac`, liên kết tài khoản và snapshot điểm đón/trả + thời hạn hủy trên
đơn. Nâng cấp theo kiểu thêm bảng/cột có kiểm tra tồn tại, không xóa dữ liệu cũ.
Giá vé và phí hành lý của đơn đã đặt không đổi. Từ V6, chính sách hủy tự nguyện
RVT thống nhất trước ít nhất 24 giờ; cột thời hạn cũ vẫn được lưu làm tham chiếu.

`V4__fleet_and_places.sql` thêm danh mục địa lý, phương tiện/sơ đồ riêng, liên kết
địa điểm/phương tiện vào chuyến, token QR, thời gian hoàn tiền và lý do hủy.
Ràng buộc trạng thái được mở rộng có kiểm tra để hỗ trợ chuyến hoàn thành và
đơn đã sử dụng; vẫn giữ tất cả các trạng thái cũ.

`V5__admin_workspace.sql` thêm tài khoản quản trị, trạng thái/lý do khóa khách
và nội dung nhật ký. Không xóa dữ liệu cũ, không đổi bảng tài khoản khách thành
quản trị. Migration chạy lại có kiểm tra tồn tại; mật khẩu chỉ được seed một lần.

`V6__rvt_balance_and_passengers.sql` thêm số dư, sổ giao dịch RVT, tiền hoàn chờ
nhận cho đơn cũ chưa có tài khoản, thông báo và nhóm tuổi/ngày sinh hành khách.
Giá ưu đãi được khai báo theo chuyến, vé giữ nguyên giá tại lúc đặt. Các tiền
hoàn đã xử lý trước khi bật số dư RVT không được tự ghi lại thành số dư mới.

`V7__product_workflows.sql` thêm hồ sơ email, phiên bản đăng nhập, khóa yêu cầu
đặt vé, thời điểm hủy, liên kết thông báo hỗ trợ và hai bảng hội thoại hỗ trợ.
Migration tự chạy cùng ứng dụng, chỉ thêm cấu trúc có kiểm tra tồn tại. Không
đặt lại tài khoản, mật khẩu, số dư hoặc giá vé lịch sử.

`V8__passenger_profile.sql` thêm hồ sơ ngày sinh, loại/số giấy tờ và quốc tịch.
Khách chọn lưu để dùng nút “Tôi là hành khách · Điền từ hồ sơ” khi đặt vé. CCCD
phải đúng 12 chữ số; hộ chiếu và khai sinh có quy tắc riêng. Không tự sửa dữ
liệu hành khách trên vé đã đặt. Xem [bản sửa theo ảnh điện thoại](docs/MOBILE_FEEDBACK_FIXES.md)
để biết nguồn tên tàu, giới hạn kết nối hãng / ngân hàng và kết quả kiểm thử.

Tên tỉnh/thành tham chiếu [Cổng thông tin Chính phủ](https://xaydungchinhsach.chinhphu.vn/chi-tiet-34-don-vi-hanh-chinh-cap-tinh-tu-12-6-2025-119250612141845533.htm),
danh mục hiện hành sau Nghị quyết 202/2025/QH15. Slug trong danh mục là mã nội bộ,
không phải mã hành chính chính thức. 185 điểm là danh mục gợi ý du lịch/đô thị/bến,
không tuyên bố bao gồm toàn bộ xã/phường hoặc được tất cả hãng vận tải khai thác.
Các tên tỉnh cũ và tên đô thị được lưu để hỗ trợ tìm kiếm, không gắn nhãn tỉnh hiện hành.

Ứng dụng tạo 28 chuyến mẫu và 1.008 chỗ cho 7 ngày, **chỉ một lần**. Sau khi các
chuyến mẫu đã qua ngày, quản trị tạo chuyến mới để tiếp tục trình diễn.
Sơ đồ mẫu không phải sơ đồ được xác nhận của hãng vận tải thực tế. Chuyến cũ chưa
có dữ liệu vị trí hiển thị danh sách chỗ. Có ba phương tiện mẫu (RVT-MAU) để
bắt đầu từ xe ngồi 28 chỗ, xe nằm 36 chỗ hoặc tàu 40 chỗ; có thể tạo sơ đồ riêng
bằng trình biên tập. Chuyến mẫu cũ giữ nguyên và chưa gán phương tiện.

Nghiệp vụ dữ liệu sử dụng Spring JDBC để kiểm soát truy vấn và khóa SQL Server
tường minh; service được quản lý giao dịch bằng Spring `@Transactional`.
Chính sách động được đọc từ `QuyTacVe`, kiểm tra và tính phí qua `FarePolicy`.
Bảng `ChinhSachPhi` cũ được giữ làm tham chiếu, không phải nguồn cấu hình của luồng
mới. Định mức hành lý không được vượt ràng buộc SQL gốc: xe 30 kg, tàu 40 kg.

## Đề 5: Quản lý đặt vé du lịch (bổ sung theo yêu cầu giảng viên)

Kiến trúc 4 tầng: Presentation (Thymeleaf) → Controller (Spring MVC + Spring Security)
→ Service (giao dịch, máy trạng thái, Strategy) → Persistence (JdbcTemplate + Spring Data
JPA, HikariCP, SQL Server).

**Strategy Pattern theo loại vé** (`com.rvt.ticket.strategy`): giao diện `TicketStrategy`,
hai cài đặt `BusTicketStrategy` (xe khách) và `BoatShipTicketStrategy` (tàu/thuyền),
chọn qua `TicketStrategyRegistry` theo mã loại vé. Mỗi strategy có quy tắc riêng về hành
lý, giấy tờ, nhóm giá, chi tiết vé và lịch hẹn. Thêm loại vé mới chỉ cần thêm một
lớp `@Component` mới (Open/Closed). Đề cho phép chọn 2 trong 4 phương tiện; bản này
cài xe khách và tàu/thuyền, chưa cài máy bay và tàu hỏa.

**Máy trạng thái** (`com.rvt.ticket.booking`): `ReservationState` gồm CREATED (giữ chỗ),
CONFIRMED (đã thanh toán), USED, CANCELLED, EXPIRED; chuyển trái quy tắc bị từ chối.
`AppointmentState`: NEW → CONFIRMED → DONE, hoặc NEW/CONFIRMED → CANCELLED.

**Controller**: `TicketCatalogController` (danh mục vé), `ReservationController` (giữ chỗ,
POST rồi redirect 303 sang trang đơn theo mô hình PRG), `PaymentController` (thanh toán),
`AppointmentController` (đặt lịch hẹn tại `/lich-hen`, quản trị tại `/admin/lich-hen`),
`TransactionApiController` (REST `GET /api/don-ve/{ma}/giao-dich`, chỉ chủ đơn hoặc quản trị).
Mã redirect 303 được cấu hình trong `RedirectSeeOtherConfig`.

**Entity JPA** (`com.rvt.ticket.domain`): `Ticket`, `Reservation`, `UserAccount`,
`TransDetail`, `Appointment`. Repository nằm ở `com.rvt.ticket.repository`. Giao dịch ghi
dùng `@Transactional(rollbackFor = Exception.class)`. HikariCP cấu hình trong
`application.properties` (pool `RVTTicketPool`, biến `DB_POOL_MAX`, `DB_POOL_MIN_IDLE`).

**Phân quyền (RBAC)**: ROLE_TRAVELER và ROLE_AGENT cho khách/đại lý (ROLE_CUSTOMER giữ để
tương thích), ROLE_OPERATOR và ROLE_ADMIN cho nội bộ. Tài khoản OPERATOR chỉ được tạo khi
đặt biến môi trường `OPERATOR_PASSWORD`. Admin mặc định `admin / 123456` (chỉ để chạy thử).

**Lịch hẹn tư vấn (Workflow 4)**: kiểm tra khung giờ và quy tắc ngày theo loại vé, giới hạn
số lượt mỗi khung giờ (khóa bằng `sp_getapplock` để tránh vượt chỗ khi đồng thời), cấp mã
lịch hẹn, và hiển thị cho nhân viên ở trang `/admin/lich-hen` để xác nhận, hoàn thành hoặc hủy.

**Database**: migration `V11__appointments_roles.sql` chạy tự động khi khởi động; thêm cột
`VaiTro` cho hai bảng tài khoản và bảng `LichHenDatCho`. Bảng cũ `LichHenTuVan` trong script
gốc được giữ nguyên, không dùng.

**Chạy bằng dòng lệnh** (Windows): `mvnw.cmd -B clean package -DskipTests`, rồi
`java -jar target\rvtticket-0.0.1-SNAPSHOT.jar` với biến môi trường `DB_PASSWORD`, `DB_HOST`,
`DB_PORT`, `DB_NAME`, `DB_USERNAME`. Dùng JDK 21. SQL Server cũ (2012) có thể cần cấu hình
TLS riêng cho kết nối loopback; SQL Server 2017 trở lên không cần.

**Kiểm thử**: `StateMachineTests` và `TicketStrategyTests` không cần cơ sở dữ liệu.
Chạy `mvnw.cmd test`; các test SQL cần `RVT_SQL_TEST=true` và `DB_PASSWORD`.

## Kiểm thử

```bash
./mvnw test
```

Test mặc định dùng H2 trong bộ nhớ và không yêu cầu SQL Server đang chạy. Kết nối
SQL Server thật được kiểm tra khi chạy ứng dụng với biến `DB_PASSWORD`.

Kiểm thử tích hợp SQL Server: đặt `DB_PASSWORD` và `RVT_SQL_TEST=true`, rồi chạy
`./mvnw test`. Các test đơn vé chạy trong transaction và rollback dữ liệu thử.
Kiểm tra: nhiều hành khách, trùng chỗ, giấy tờ, phí, thanh toán lặp, hủy/hoàn,
hết hạn/giải phóng chỗ, tạo sơ đồ, các trang MVC và phân quyền/CSRF, đăng nhập
thật qua Spring Security, lịch sử riêng tư, snapshot chính sách/điểm đón và
ngăn bán lại chỗ đã lên xe/tàu, catalogue toàn quốc, giữ nguyên vị trí trong sơ đồ
phương tiện, lịch trùng/tiếp giáp, hủy chuyến/hoàn, vòng đời đơn/chuyến, tạo/giải mã
QR, truy cập QR đúng đơn/phân quyền và tải ảnh, thống kê đúng ngày Việt Nam và
ngày hoàn khác ngày thu, báo lỗi và tự điền thông tin khách.

Kiểm thử Java bổ sung cho quản trị: phân quyền chỉ ADMIN, BCrypt/khởi tạo không
reset mật khẩu, khóa và kết thúc phiên khách nhưng giữ đơn, phân trang vượt 100
đơn, UTC/giờ Việt Nam, xem lại không ghi dữ liệu, tạo/sao chép/rollback cả đợt,
mở bán qua HTTP, hủy ngoại lệ phải có lý do và chặn vé đã sử dụng. Bộ đọc QR được
kiểm thử thêm với nhiều mẫu token khác nhau, gồm ảnh QR xuất trực tiếp.

Kiểm thử tính tổng, chọn chỗ, danh mục và sơ đồ (cần Node):

```bash
node --test src/test/js/*.test.mjs
```

Lần rà soát ban đầu 03/10/2026 đã chạy 87 bài Java với SQL Server thật (không bỏ qua)
và 11 bài JavaScript. Đợt sửa theo 7 ảnh điện thoại cùng ngày đã chạy lại 96 bài
Java và 13 bài JavaScript, tất cả vượt qua. Đã kiểm tra trực quan ở màn hình laptop và
điện thoại: trang chủ, tìm chuyến, chọn/bỏ chỗ không mất dữ liệu hành khách,
giá trẻ em, vé/QR, hồ sơ, hội thoại hỗ trợ, danh mục phương tiện/địa điểm và
menu/bảng quản trị. Chi tiết và ảnh tại `docs/PRODUCT_REVIEW.md`.

Đợt sửa giao diện và dữ liệu chuyến 03/10/2026: **116 bài Java và 15 bài
JavaScript đạt**. Đã đồng bộ mã chuyến khách/admin/CSV, sửa phản hồi nhập liệu,
kiểm tra mở bán ngay lúc thao tác, bổ sung phân công phương tiện và tạm đóng hai
chuyến cũ thiếu cấu hình mà không thay đổi vé/đơn/giao dịch. Xem
[kết quả và giới hạn còn lại](docs/UI_TRIP_DATA_FIXES_2026_10_03.md).

Phân công chuyến có sẵn: vào **Chuyến đi → Chi tiết → Đóng bán**, khai báo xe/tàu
và sơ đồ đúng tại **Phương tiện**, rồi chọn **Lưu phân công** trong chi tiết chuyến.
Chuyến vẫn đóng bán sau khi phân công; rà soát lịch, giá, điểm đón/trả rồi mở bán.
Nếu đã từng có vé, sơ đồ phương tiện phải khớp toàn bộ mã chỗ, loại và vị trí;
không tự chuyển vị trí/giá của khách. Hai chuyến cũ thiếu sơ đồ cần xử lý riêng,
không ghép sơ đồ tự đoán. Lịch tham khảo tạo ở lần cài mới cũng được lưu đóng bán.

Kiểm thử tự động không thay thế kiểm tra thiết bị thật. Trước buổi nộp, thử
thêm Safari/Chrome trên điện thoại qua Tailscale, in/PDF trên máy giảng viên,
Escape/Tab trong hộp xác nhận, chế độ giảm chuyển động và camera quét QR.
Camera là phần chưa kiểm tra trực tiếp trên thiết bị.
[BarcodeDetector](https://developer.mozilla.org/en-US/docs/Web/API/BarcodeDetector)
không được hỗ trợ trên mọi trình duyệt và cần secure context; ảnh QR là phương
án thay thế. HTTP qua địa chỉ LAN thường không cấp camera: dùng localhost trên
máy chủ hoặc cấu hình HTTPS khi kiểm tra trên điện thoại.

## Tuyến tàu, giá mẫu và kịch bản trình diễn

`/tuyen-tau` giới thiệu 6 tuyến: Rạch Giá/Hà Tiên–Phú Quốc, Vũng Tàu/Trần Đề–
Côn Đảo, Phan Thiết–Phú Quý, Sa Kỳ–Lý Sơn. Lựa chọn dựa trên tuyến hãng công bố
và nhu cầu chọn cảng/đi cùng gia đình, không phải xếp hạng lượng khách.
Nguồn chính thức tham khảo ngày 03/10/2026:
[Phú Quốc](https://phuquocexpress.com/dat-ve-tau-cao-toc-di-phu-quoc),
[Côn Đảo](https://phuquocexpress.com/gia-ve-tau-cao-toc-di-con-dao),
[Phú Quý](https://phuquocexpress.com/gia-ve-tau-cao-toc-di-phu-quy),
[Lý Sơn](https://phuquocexpress.com/gia-ve-tau-cao-toc-di-ly-son),
[đặt vé Superdong](https://online.superdong.com.vn/Booking).

Giá ECO từng chuyến có mức trẻ em/cao tuổi riêng. Nếu quản trị để trống, dùng
giá mẫu 80% người lớn, làm tròn 1.000 ₫ (không tuyên bố là chính sách hãng).
VIP không giảm theo tuổi. Trong RVT, dưới 12 tuổi trả giá trẻ em với ghế riêng,
12–59 tuổi trả giá người lớn, từ 60 tuổi giá cao tuổi. Dưới 12 tuổi cần người
từ 18 tuổi đi cùng đơn. Chưa hỗ trợ trẻ dưới 6 tuổi miễn phí ngồi chung ghế.
Giá Lý Sơn mẫu đã gộp phí cảng theo nhóm, không cộng phí cảng lần nữa.
Chính sách hoàn 100% trước 24 giờ là chính sách đồ án RVT, không phải của hãng.

Khi khởi động lần đầu sau cập nhật, tự tạo 36 chuyến `RVT-SEA-*` trong 3 ngày
kể từ ngày mai, đủ 6 tuyến x 2 chiều, sơ đồ 40 ghế **mẫu**, không phải bố cục
tàu thật. Vũng Tàu/Trần Đề–Côn Đảo có giá cuối tuần riêng (thứ 6–CN), chưa
tự áp dụng lịch lễ. Giờ chạy mẫu do RVT đặt, không phải lịch hãng; không tự
xóa/sinh lại mỗi ngày. Quản trị dùng chức năng tạo/sao chép chuyến để cập nhật
lịch mới, nhập sơ đồ đúng phương tiện trước khi sử dụng ngoài đồ án.

Để kiểm tra số dư: đăng ký/đăng nhập → chọn chuyến đã được mở bán và còn hơn 24 giờ → thanh toán qua VNPay bằng thẻ kiểm thử
→ hủy đơn → mở “Số dư RVT” → đặt đơn mới với tổng tiền không vượt số dư → chọn
“Thanh toán bằng số dư RVT”. Tài khoản mới có số dư 0.

Đơn cũ không có tài khoản vẫn tra cứu được. Khi hủy, tiền hoàn được lưu chờ
nhận; khách đăng nhập, tra cứu mã đơn + điện thoại rồi bấm “Gắn đơn cũ vào tài
khoản”, chỉ khi điện thoại tài khoản khớp và đơn chưa thuộc tài khoản khác.
Không tự gắn hàng loạt theo số điện thoại. Các vé gốc không có `DonDatVe`
được gắn thành đơn riêng khi nâng cấp, giữ nguyên vé và biên nhận; không tự gắn
tài khoản hay quy đổi tiền hoàn lịch sử vào tài khoản chưa xác minh.

SQL nâng cấp tại `src/main/resources/db/migration/V6__rvt_balance_and_passengers.sql`
được chạy tự động, chỉ thêm cấu trúc và mở rộng phương thức thanh toán; không
đặt lại database hoặc thay đổi giá vé lịch sử. **Không chạy lại script tạo
database gốc trên dữ liệu hiện tại.** Kiểm thử đồng thời dùng dữ liệu `RVT-QA-*`
và tài khoản `rvt_qa_*` có tên ngẫu nhiên, chỉ dọn chính dữ liệu do test tạo.

## Xem trên điện thoại qua Tailscale

Máy hiện tại đã truy cập được website tại `http://100.112.242.8:8080/` qua địa
chỉ Tailscale. Trên điện thoại, bật Tailscale trong cùng mạng riêng rồi mở địa
chỉ này trong trình duyệt. Ứng dụng Java, SQL Server Docker và máy Mac cần
tiếp tục chạy; máy ngủ hoặc ứng dụng dừng thì địa chỉ không còn phục vụ web.
Không chạy thêm một ứng dụng khác cùng cổng 8080 trong IntelliJ khi bản hiện
tại đang chạy. Không cần mở cổng router hoặc công khai website để xem thử.

Camera QR trên điện thoại có thể không hoạt động với HTTP; vẫn có ô tải ảnh
QR để kiểm tra. Đây không phải lỗi của luồng đặt vé. Không bỏ qua cảnh báo
bảo mật trình duyệt hoặc mở công khai website chỉ để cấp quyền camera.

## Giới hạn còn lại

Chưa có cấu hình VNPay môi trường production, gửi email/SMS, khôi phục mật khẩu
qua email, API hãng vận tải, định tuyến bản đồ hoặc bố cục ngoài giới hạn lưới hiện tại.
Lịch hẹn tư vấn đã được bổ sung theo Đề 5 (xem mục bên dưới). Sơ đồ người quản trị nhập phải được đối chiếu với
phương tiện thực tế; ứng dụng không tự xác nhận tính đúng ngoài đời.
Không có đánh giá, số lượng khách, chứng nhận hoặc cam kết đối tác giả.
Ảnh điểm đến không phải ảnh của xe/tàu. Nguồn ảnh nằm ở `/nguon-anh`.

Nếu đưa lên Internet thật: cần HTTPS, bí mật cấu hình riêng, giới hạn đăng nhập/
tra cứu dùng lưu trữ chung thay cho bộ đếm một tiến trình, xác minh liên hệ và
luồng khôi phục mật khẩu; sao lưu và migration có
phiên bản; quy trình bảo vệ dữ liệu hành khách. Bản này phục vụ trình diễn cục bộ.
# VNPay

Đã tích hợp yêu cầu thanh toán, Return/IPN, truy vấn đối chiếu querydr và quản trị giao dịch tại `/admin/vnpay`.
Return cho yêu cầu mới giữ đúng origin đã đăng nhập trong allowlist; không lấy trang chuyển hướng làm căn cứ cấp vé.
Chưa kích hoạt merchant nếu chưa nhập khóa riêng và thiết lập HTTPS callback.
Tích hợp VNPay bị tắt mặc định cho đến khi người vận hành nhập cấu hình riêng.
Khi chuyển sang máy khác, xem [hướng dẫn bàn giao](RUN_ON_FRIEND_MACHINE.md);
không sao chép file cấu hình khóa riêng của người khác.
