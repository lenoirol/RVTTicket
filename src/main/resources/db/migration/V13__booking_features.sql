-- V13: điểm đón / trả trung chuyển, mã khuyến mãi, đổi vé, phí hủy theo mốc, hóa đơn điện tử.
-- Chỉ thêm cấu trúc và dữ liệu mẫu; không xóa hay sửa vé, đơn, giao dịch đã có.

-- 1. Điểm đón / trả bổ sung theo thành phố (dùng cho xe khách). Điểm mặc định của chuyến vẫn nằm ở ThongTinChuyen.
IF OBJECT_ID('dbo.DiemDonTra','U') IS NULL
    CREATE TABLE dbo.DiemDonTra (
        DiemID INT IDENTITY PRIMARY KEY,
        ThanhPho NVARCHAR(100) NOT NULL,
        TenDiem NVARCHAR(150) NOT NULL,
        DiaChi NVARCHAR(250) NOT NULL,
        LaTrungChuyen BIT NOT NULL DEFAULT 1,
        PhutKhiDon INT NULL,   -- so với giờ khởi hành; âm = đón sớm hơn
        PhutKhiTra INT NULL,   -- so với giờ đến dự kiến; dương = trả sau khi xe về bến
        HoatDong BIT NOT NULL DEFAULT 1,
        CONSTRAINT UQ_DiemDonTra UNIQUE(ThanhPho,TenDiem),
        CONSTRAINT CK_DiemDonTra_Phut CHECK((PhutKhiDon IS NULL OR PhutKhiDon BETWEEN -180 AND 180) AND (PhutKhiTra IS NULL OR PhutKhiTra BETWEEN -180 AND 180))
    );
IF NOT EXISTS(SELECT 1 FROM dbo.DiemDonTra)
    INSERT dbo.DiemDonTra(ThanhPho,TenDiem,DiaChi,LaTrungChuyen,PhutKhiDon,PhutKhiTra) VALUES
    (N'TP. Hồ Chí Minh',N'Văn phòng RVT Quận 1',N'Khu vực trung tâm Quận 1, TP. Hồ Chí Minh',1,-60,45),
    (N'TP. Hồ Chí Minh',N'Văn phòng RVT Quận 10',N'Khu vực Quận 10, TP. Hồ Chí Minh',1,-60,45),
    (N'TP. Hồ Chí Minh',N'Văn phòng RVT Thủ Đức',N'Khu vực TP. Thủ Đức, TP. Hồ Chí Minh',1,-45,40),
    (N'Hà Nội',N'Văn phòng RVT Hoàn Kiếm',N'Khu vực quận Hoàn Kiếm, Hà Nội',1,-60,45),
    (N'Hà Nội',N'Văn phòng RVT Cầu Giấy',N'Khu vực quận Cầu Giấy, Hà Nội',1,-45,40),
    (N'Hà Nội',N'Văn phòng RVT Long Biên',N'Khu vực quận Long Biên, Hà Nội',1,-45,40),
    (N'Cần Thơ',N'Văn phòng RVT Ninh Kiều',N'Khu vực quận Ninh Kiều, Cần Thơ',1,-40,30),
    (N'Cần Thơ',N'Văn phòng RVT Cái Răng',N'Khu vực quận Cái Răng, Cần Thơ',1,-30,30),
    (N'Đà Nẵng',N'Văn phòng RVT Hải Châu',N'Khu vực quận Hải Châu, Đà Nẵng',1,-40,30),
    (N'Đà Nẵng',N'Văn phòng RVT Sơn Trà',N'Khu vực quận Sơn Trà, Đà Nẵng',1,-40,35),
    (N'Nha Trang',N'Văn phòng RVT trung tâm Nha Trang',N'Khu vực trung tâm Nha Trang, Khánh Hòa',1,-40,30),
    (N'Nha Trang',N'Văn phòng RVT Trần Phú',N'Khu vực đường Trần Phú ven biển, Nha Trang',1,-40,30),
    (N'Đà Lạt',N'Văn phòng RVT chợ Đà Lạt',N'Khu vực chợ Đà Lạt, Lâm Đồng',1,-30,25),
    (N'Đà Lạt',N'Văn phòng RVT Hồ Xuân Hương',N'Khu vực hồ Xuân Hương, Đà Lạt',1,-30,25),
    (N'Hải Phòng',N'Văn phòng RVT Lê Chân',N'Khu vực quận Lê Chân, Hải Phòng',1,-40,30),
    (N'Vũng Tàu',N'Văn phòng RVT Bãi Sau',N'Khu vực Bãi Sau, Vũng Tàu',1,-30,25),
    (N'Huế',N'Văn phòng RVT trung tâm Huế',N'Khu vực trung tâm TP. Huế',1,-30,25),
    (N'Quy Nhơn',N'Văn phòng RVT trung tâm Quy Nhơn',N'Khu vực trung tâm Quy Nhơn, Gia Lai',1,-30,25);

-- 2. Mã khuyến mãi.
IF OBJECT_ID('dbo.KhuyenMai','U') IS NULL
    CREATE TABLE dbo.KhuyenMai (
        MaKhuyenMai VARCHAR(30) NOT NULL PRIMARY KEY,
        MoTa NVARCHAR(200) NOT NULL,
        KieuGiam VARCHAR(10) NOT NULL CHECK(KieuGiam IN('PHAN_TRAM','SO_TIEN')),
        GiaTri DECIMAL(18,2) NOT NULL CHECK(GiaTri>0),
        GiamToiDa DECIMAL(18,2) NULL,
        DonToiThieu DECIMAL(18,2) NOT NULL DEFAULT 0,
        SoVeToiThieu INT NOT NULL DEFAULT 1,
        LoaiVe VARCHAR(12) NULL CHECK(LoaiVe IS NULL OR LoaiVe IN('XE_KHACH','TAU_THUYEN')),
        BatDau DATETIME2(0) NOT NULL,
        KetThuc DATETIME2(0) NOT NULL,
        SoLuotToiDa INT NULL,
        MoiTaiKhoanMotLan BIT NOT NULL DEFAULT 0,
        HoatDong BIT NOT NULL DEFAULT 1,
        CONSTRAINT CK_KhuyenMai_ThoiGian CHECK(KetThuc>BatDau),
        CONSTRAINT CK_KhuyenMai_PhanTram CHECK(KieuGiam<>'PHAN_TRAM' OR GiaTri<=50)
    );
IF NOT EXISTS(SELECT 1 FROM dbo.KhuyenMai)
    INSERT dbo.KhuyenMai(MaKhuyenMai,MoTa,KieuGiam,GiaTri,GiamToiDa,DonToiThieu,SoVeToiThieu,LoaiVe,BatDau,KetThuc,SoLuotToiDa,MoiTaiKhoanMotLan) VALUES
    ('RVTMOI',N'Giảm 10% cho đơn đầu tiên của tài khoản, tối đa 50.000 đồng','PHAN_TRAM',10,50000,0,1,NULL,'2026-01-01','2027-01-01',NULL,1),
    ('XEKHACH30K',N'Giảm 30.000 đồng cho đơn xe khách từ 300.000 đồng','SO_TIEN',30000,NULL,300000,1,'XE_KHACH','2026-01-01','2027-01-01',500,0),
    ('TAUBIEN15',N'Giảm 15% cho đơn tàu thuyền từ 2 vé, tối đa 100.000 đồng','PHAN_TRAM',15,100000,0,2,'TAU_THUYEN','2026-01-01','2027-01-01',300,0);

-- Phần giảm giá được lưu như một dòng ChiTietPhi âm (MaPhi = KHUYEN_MAI) để tổng tiền từng vé, VNPay và hoàn tiền dùng chung một nguồn.
IF EXISTS(SELECT 1 FROM sys.check_constraints WHERE name='CK_ChiTietPhi_SoTien' AND definition NOT LIKE '%KHUYEN_MAI%')
    ALTER TABLE dbo.ChiTietPhi DROP CONSTRAINT CK_ChiTietPhi_SoTien;
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE name='CK_ChiTietPhi_SoTien')
    EXEC(N'ALTER TABLE dbo.ChiTietPhi ADD CONSTRAINT CK_ChiTietPhi_SoTien CHECK(SoTien>=0 OR (MaPhi=''KHUYEN_MAI'' AND SoTien<0))');

-- 3. Đổi vé: tiền của đơn cũ được chuyển sang đơn mới bằng phương thức DOI_VE.
IF EXISTS(SELECT 1 FROM sys.check_constraints WHERE name='CK_ThanhToan_PhuongThuc' AND definition NOT LIKE '%DOI_VE%')
    ALTER TABLE dbo.ThanhToan DROP CONSTRAINT CK_ThanhToan_PhuongThuc;
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE name='CK_ThanhToan_PhuongThuc')
    EXEC(N'ALTER TABLE dbo.ThanhToan ADD CONSTRAINT CK_ThanhToan_PhuongThuc CHECK(PhuongThuc IN(''TIEN_MAT'',''CHUYEN_KHOAN'',''THE'',''VI_DIEN_TU'',''SO_DU_RVT'',''VNPAY'',''DOI_VE''))');

-- Cột mới của đơn: điểm đón / trả đã chọn, khuyến mãi, đổi vé, phí hủy.
IF COL_LENGTH('dbo.DonDatVe','DiemDonID') IS NULL ALTER TABLE dbo.DonDatVe ADD DiemDonID INT NULL REFERENCES dbo.DiemDonTra(DiemID);
IF COL_LENGTH('dbo.DonDatVe','DiemTraID') IS NULL ALTER TABLE dbo.DonDatVe ADD DiemTraID INT NULL REFERENCES dbo.DiemDonTra(DiemID);
IF COL_LENGTH('dbo.DonDatVe','GioDonDuKien') IS NULL ALTER TABLE dbo.DonDatVe ADD GioDonDuKien DATETIME2(0) NULL;
IF COL_LENGTH('dbo.DonDatVe','GioTraDuKien') IS NULL ALTER TABLE dbo.DonDatVe ADD GioTraDuKien DATETIME2(0) NULL;
IF COL_LENGTH('dbo.DonDatVe','MaKhuyenMai') IS NULL ALTER TABLE dbo.DonDatVe ADD MaKhuyenMai VARCHAR(30) NULL REFERENCES dbo.KhuyenMai(MaKhuyenMai);
IF COL_LENGTH('dbo.DonDatVe','TienGiam') IS NULL ALTER TABLE dbo.DonDatVe ADD TienGiam DECIMAL(18,2) NOT NULL CONSTRAINT DF_DonDatVe_TienGiam DEFAULT 0;
IF COL_LENGTH('dbo.DonDatVe','DoiTuDonID') IS NULL ALTER TABLE dbo.DonDatVe ADD DoiTuDonID INT NULL REFERENCES dbo.DonDatVe(DonDatVeID);
IF COL_LENGTH('dbo.DonDatVe','DoiSangDonID') IS NULL ALTER TABLE dbo.DonDatVe ADD DoiSangDonID INT NULL REFERENCES dbo.DonDatVe(DonDatVeID);
IF COL_LENGTH('dbo.DonDatVe','PhiHuy') IS NULL ALTER TABLE dbo.DonDatVe ADD PhiHuy DECIMAL(18,2) NULL;
IF COL_LENGTH('dbo.DonDatVe','TienHoan') IS NULL ALTER TABLE dbo.DonDatVe ADD TienHoan DECIMAL(18,2) NULL;

-- 4. Hóa đơn điện tử (bản thể hiện mô phỏng cho đồ án).
IF OBJECT_ID('dbo.HoaDon','U') IS NULL
    CREATE TABLE dbo.HoaDon (
        HoaDonID INT IDENTITY PRIMARY KEY,
        DonDatVeID INT NOT NULL UNIQUE REFERENCES dbo.DonDatVe(DonDatVeID),
        KyHieu VARCHAR(10) NOT NULL,
        SoHoaDon VARCHAR(10) NULL,
        MaTraCuu VARCHAR(12) NOT NULL UNIQUE,
        LoaiNguoiMua VARCHAR(10) NOT NULL CHECK(LoaiNguoiMua IN('CA_NHAN','DON_VI')),
        TenNguoiMua NVARCHAR(150) NOT NULL,
        MaSoThue VARCHAR(14) NULL,
        DiaChi NVARCHAR(250) NULL,
        Email VARCHAR(254) NULL,
        TienTruocThue DECIMAL(18,2) NOT NULL,
        ThueSuat DECIMAL(5,2) NOT NULL,
        TienThue DECIMAL(18,2) NOT NULL,
        TongThanhToan DECIMAL(18,2) NOT NULL CHECK(TongThanhToan>0),
        NgayLap DATETIME2(0) NOT NULL DEFAULT SYSUTCDATETIME(),
        CONSTRAINT CK_HoaDon_MST CHECK(MaSoThue IS NULL OR MaSoThue LIKE '[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]%')
    );
