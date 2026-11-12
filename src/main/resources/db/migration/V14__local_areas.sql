-- V14: khu vực con trong tỉnh/thành (quận, huyện cũ...) để tìm chuyến theo nơi ở của khách.
-- Mỗi khu vực được phục vụ bởi một thành phố có chuyến (PhucVuBoi) và một điểm đón / trả trung chuyển.
-- Chỉ thêm cột; dữ liệu khu vực được nạp từ data/vietnam-areas.tsv sau khi danh mục tỉnh/thành sẵn sàng.
IF COL_LENGTH('dbo.DiaDiem','PhucVuBoi') IS NULL ALTER TABLE dbo.DiaDiem ADD PhucVuBoi NVARCHAR(150) NULL;
IF COL_LENGTH('dbo.DiaDiem','LaKhuVuc') IS NULL ALTER TABLE dbo.DiaDiem ADD LaKhuVuc BIT NOT NULL CONSTRAINT DF_DiaDiem_LaKhuVuc DEFAULT 0;
IF COL_LENGTH('dbo.DiaDiem','BiDanh') IS NULL ALTER TABLE dbo.DiaDiem ADD BiDanh NVARCHAR(300) NULL;
IF COL_LENGTH('dbo.DiaDiem','ThuTu') IS NULL ALTER TABLE dbo.DiaDiem ADD ThuTu INT NULL;
IF COL_LENGTH('dbo.DiemDonTra','MaDiem') IS NULL ALTER TABLE dbo.DiemDonTra ADD MaDiem VARCHAR(60) NULL REFERENCES dbo.DiaDiem(MaDiem);

-- Bến xe có thật: địa chỉ và tọa độ (nguồn OpenStreetMap), dùng để hiện địa chỉ bến và tìm bến gần vị trí của khách.
-- Dữ liệu nạp từ data/vietnam-bus-stations.tsv.
IF OBJECT_ID('dbo.BenXe','U') IS NULL
    CREATE TABLE dbo.BenXe (
        BenXeID INT IDENTITY PRIMARY KEY,
        TenBen NVARCHAR(150) NOT NULL UNIQUE,
        ThanhPho NVARCHAR(100) NOT NULL,
        DiaChi NVARCHAR(300) NOT NULL,
        ViDo DECIMAL(9,6) NOT NULL CHECK(ViDo BETWEEN 8 AND 24),
        KinhDo DECIMAL(9,6) NOT NULL CHECK(KinhDo BETWEEN 102 AND 110),
        DoChinhXac VARCHAR(12) NOT NULL CHECK(DoChinhXac IN('OSM','CAN_KIEM_TRA','GAN_DUNG')),
        TenTrenBanDo NVARCHAR(200) NULL
    );
