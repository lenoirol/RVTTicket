/*
    DE 5.3 + 5.4: QUAN LY DAT VE XE KHACH VA VE TAU/THUYEN
    SQL Server 2019+

    Nguyen tac:
    - Moi DangKyVe la mot hanh khach, mot cho, tren mot chuyen.
    - Gia va phi tren dang ky la anh chup tai thoi diem dat ve.
    - Nghiep vu tao cha + bang con + chi tiet phi phai chay trong cung transaction.
    - File dung cho CSDL moi; khong tu dong xoa du lieu/bang da ton tai.
*/

IF DB_ID(N'RVTTicket') IS NULL
    EXEC(N'CREATE DATABASE RVTTicket');
GO

USE RVTTicket;
GO

SET XACT_ABORT ON;
GO

IF OBJECT_ID(N'dbo.KhachHang', N'U') IS NOT NULL
    THROW 50001, N'Cac bang De 5 da ton tai. Hay dung CSDL moi hoac doi ten CSDL.', 1;
GO

CREATE TABLE dbo.KhachHang (
    KhachHangID INT IDENTITY(1,1) NOT NULL
        CONSTRAINT PK_KhachHang PRIMARY KEY,
    HoTen NVARCHAR(100) NOT NULL,
    SoDienThoai VARCHAR(15) NOT NULL,
    Email VARCHAR(254) NULL,
    NgayTao DATETIME2(0) NOT NULL
        CONSTRAINT DF_KhachHang_NgayTao DEFAULT SYSUTCDATETIME(),
    CONSTRAINT CK_KhachHang_HoTen CHECK (LEN(LTRIM(RTRIM(HoTen))) > 0),
    CONSTRAINT CK_KhachHang_SDT CHECK (
        LEN(SoDienThoai) BETWEEN 10 AND 15
        AND SoDienThoai COLLATE Latin1_General_100_BIN2 NOT LIKE '%[^0-9]%'
    ),
    CONSTRAINT CK_KhachHang_Email CHECK (
        Email IS NULL OR
        (Email NOT LIKE '% %' AND Email LIKE '_%@_%._%')
    )
);
GO

CREATE INDEX IX_KhachHang_SoDienThoai ON dbo.KhachHang(SoDienThoai);
GO

CREATE TABLE dbo.ChuyenDi (
    ChuyenDiID INT IDENTITY(1,1) NOT NULL
        CONSTRAINT PK_ChuyenDi PRIMARY KEY,
    MaChuyen VARCHAR(30) NOT NULL
        CONSTRAINT UQ_ChuyenDi_MaChuyen UNIQUE,
    LoaiVe VARCHAR(12) NOT NULL,
    DonViVanChuyen NVARCHAR(100) NOT NULL,
    NoiDi NVARCHAR(150) NOT NULL,
    NoiDen NVARCHAR(150) NOT NULL,
    KhoiHanh DATETIME2(0) NOT NULL,
    DuKienDen DATETIME2(0) NOT NULL,
    TongSoCho INT NOT NULL,
    GiaCoBan DECIMAL(18,2) NOT NULL,
    TrangThai VARCHAR(20) NOT NULL
        CONSTRAINT DF_ChuyenDi_TrangThai DEFAULT 'MO_BAN',
    CONSTRAINT UQ_ChuyenDi_ID_Loai UNIQUE (ChuyenDiID, LoaiVe),
    CONSTRAINT CK_ChuyenDi_Loai CHECK (LoaiVe IN ('XE_KHACH','TAU_THUYEN')),
    CONSTRAINT CK_ChuyenDi_DonVi CHECK (LEN(LTRIM(RTRIM(DonViVanChuyen))) > 0),
    CONSTRAINT CK_ChuyenDi_NoiDi CHECK (LEN(LTRIM(RTRIM(NoiDi))) > 0),
    CONSTRAINT CK_ChuyenDi_NoiDen CHECK (LEN(LTRIM(RTRIM(NoiDen))) > 0),
    CONSTRAINT CK_ChuyenDi_Tuyen CHECK (LTRIM(RTRIM(NoiDi)) <> LTRIM(RTRIM(NoiDen))),
    CONSTRAINT CK_ChuyenDi_ThoiGian CHECK (DuKienDen > KhoiHanh),
    CONSTRAINT CK_ChuyenDi_TongSoCho CHECK (TongSoCho > 0),
    CONSTRAINT CK_ChuyenDi_Gia CHECK (GiaCoBan >= 0),
    CONSTRAINT CK_ChuyenDi_TrangThai CHECK (
        TrangThai IN ('MO_BAN','DONG_BAN','DA_KHOI_HANH','DA_HUY')
    )
);
GO

CREATE INDEX IX_ChuyenDi_TimKiem
    ON dbo.ChuyenDi(LoaiVe, NoiDi, NoiDen, KhoiHanh, TrangThai);
GO

/* Danh muc cho ngoi giup khong the dat "ghe 100" tren chuyen chi co 40 cho. */
CREATE TABLE dbo.ChoNgoi (
    ChuyenDiID INT NOT NULL,
    MaCho VARCHAR(20) NOT NULL,
    LoaiVe VARCHAR(12) NOT NULL,
    LoaiCho VARCHAR(20) NOT NULL,
    PhuThu DECIMAL(18,2) NOT NULL
        CONSTRAINT DF_ChoNgoi_PhuThu DEFAULT 0,
    TrangThai VARCHAR(15) NOT NULL
        CONSTRAINT DF_ChoNgoi_TrangThai DEFAULT 'SAN_SANG',
    CONSTRAINT PK_ChoNgoi PRIMARY KEY (ChuyenDiID, MaCho),
    CONSTRAINT UQ_ChoNgoi_Chuyen_Ma_Loai UNIQUE (ChuyenDiID, MaCho, LoaiVe),
    CONSTRAINT FK_ChoNgoi_ChuyenDi FOREIGN KEY (ChuyenDiID, LoaiVe)
        REFERENCES dbo.ChuyenDi(ChuyenDiID, LoaiVe),
    CONSTRAINT CK_ChoNgoi_Ma CHECK (LEN(LTRIM(RTRIM(MaCho))) > 0),
    CONSTRAINT CK_ChoNgoi_PhuThu CHECK (PhuThu >= 0),
    CONSTRAINT CK_ChoNgoi_TrangThai CHECK (TrangThai IN ('SAN_SANG','BAO_TRI','NGUNG_DUNG')),
    CONSTRAINT CK_ChoNgoi_LoaiTheoPhuongTien CHECK (
        (LoaiVe = 'XE_KHACH' AND LoaiCho IN ('GHE_NGOI','GIUONG_NAM'))
        OR
        (LoaiVe = 'TAU_THUYEN' AND LoaiCho IN ('PHO_THONG','VIP'))
    )
);
GO

/* Khong cho khai bao nhieu cho hon suc chua cua chuyen. */
CREATE TRIGGER dbo.TR_ChoNgoi_KiemTraSucChua
ON dbo.ChoNgoi
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;

    IF EXISTS (
        SELECT 1
        FROM (SELECT DISTINCT ChuyenDiID FROM inserted) x
        JOIN dbo.ChuyenDi c ON c.ChuyenDiID = x.ChuyenDiID
        CROSS APPLY (
            SELECT COUNT_BIG(*) AS SoChoDaKhaiBao
            FROM dbo.ChoNgoi g
            WHERE g.ChuyenDiID = x.ChuyenDiID
        ) s
        WHERE s.SoChoDaKhaiBao > c.TongSoCho
    )
        THROW 50002, N'So cho khai bao vuot qua suc chua cua chuyen.', 1;
END;
GO

CREATE TABLE dbo.LichHenTuVan (
    LichHenID INT IDENTITY(1,1) NOT NULL
        CONSTRAINT PK_LichHenTuVan PRIMARY KEY,
    MaLichHen VARCHAR(30) NOT NULL
        CONSTRAINT UQ_LichHenTuVan_Ma UNIQUE,
    KhachHangID INT NOT NULL,
    ChuyenDiID INT NULL,
    LoaiVe VARCHAR(12) NOT NULL,
    ThoiGianHen DATETIME2(0) NOT NULL,
    HinhThuc VARCHAR(15) NOT NULL,
    NoiDung NVARCHAR(500) NULL,
    NhanVienPhuTrach NVARCHAR(100) NULL,
    TrangThai VARCHAR(20) NOT NULL
        CONSTRAINT DF_LichHen_TrangThai DEFAULT 'CHO_XAC_NHAN',
    NgayTao DATETIME2(0) NOT NULL
        CONSTRAINT DF_LichHen_NgayTao DEFAULT SYSUTCDATETIME(),
    CONSTRAINT FK_LichHen_KhachHang FOREIGN KEY (KhachHangID)
        REFERENCES dbo.KhachHang(KhachHangID),
    CONSTRAINT FK_LichHen_ChuyenDi FOREIGN KEY (ChuyenDiID, LoaiVe)
        REFERENCES dbo.ChuyenDi(ChuyenDiID, LoaiVe),
    CONSTRAINT CK_LichHen_Loai CHECK (LoaiVe IN ('XE_KHACH','TAU_THUYEN')),
    CONSTRAINT CK_LichHen_ThoiGian CHECK (ThoiGianHen > NgayTao),
    CONSTRAINT CK_LichHen_HinhThuc CHECK (HinhThuc IN ('TRUC_TIEP','DIEN_THOAI','TRUC_TUYEN')),
    CONSTRAINT CK_LichHen_TrangThai CHECK (
        TrangThai IN ('CHO_XAC_NHAN','DA_XAC_NHAN','DA_TU_VAN','DA_HUY')
    )
);
GO

CREATE INDEX IX_LichHen_LichLamViec
    ON dbo.LichHenTuVan(ThoiGianHen, TrangThai, NhanVienPhuTrach);
GO

/*
   Mot dong = mot hanh khach + mot cho.
   DangChiemCho duoc luu rieng de filtered unique index co dieu kien don gian,
   con CHECK dam bao no luon dong bo voi TrangThai.
*/
CREATE TABLE dbo.DangKyVe (
    DangKyID INT IDENTITY(1,1) NOT NULL
        CONSTRAINT PK_DangKyVe PRIMARY KEY,
    DangKyCode VARCHAR(30) NOT NULL
        CONSTRAINT UQ_DangKyVe_Code UNIQUE,
    KhachHangID INT NOT NULL,
    ChuyenDiID INT NOT NULL,
    LoaiVe VARCHAR(12) NOT NULL,
    TenHanhKhach NVARCHAR(100) NOT NULL,
    MaCho VARCHAR(20) NOT NULL,
    NgayDangKy DATETIME2(0) NOT NULL
        CONSTRAINT DF_DangKyVe_Ngay DEFAULT SYSUTCDATETIME(),
    HanThanhToan DATETIME2(0) NOT NULL,
    TrangThai VARCHAR(20) NOT NULL
        CONSTRAINT DF_DangKyVe_TrangThai DEFAULT 'GIU_CHO',
    DangChiemCho BIT NOT NULL
        CONSTRAINT DF_DangKyVe_DangChiemCho DEFAULT 1,
    GiaVe DECIMAL(18,2) NOT NULL,
    GhiChu NVARCHAR(500) NULL,
    CONSTRAINT UQ_DangKyVe_ID_Loai UNIQUE (DangKyID, LoaiVe),
    CONSTRAINT FK_DangKyVe_KhachHang FOREIGN KEY (KhachHangID)
        REFERENCES dbo.KhachHang(KhachHangID),
    CONSTRAINT FK_DangKyVe_ChuyenDi FOREIGN KEY (ChuyenDiID, LoaiVe)
        REFERENCES dbo.ChuyenDi(ChuyenDiID, LoaiVe),
    CONSTRAINT FK_DangKyVe_ChoNgoi FOREIGN KEY (ChuyenDiID, MaCho, LoaiVe)
        REFERENCES dbo.ChoNgoi(ChuyenDiID, MaCho, LoaiVe),
    CONSTRAINT CK_DangKyVe_Loai CHECK (LoaiVe IN ('XE_KHACH','TAU_THUYEN')),
    CONSTRAINT CK_DangKyVe_Ten CHECK (LEN(LTRIM(RTRIM(TenHanhKhach))) > 0),
    CONSTRAINT CK_DangKyVe_Han CHECK (HanThanhToan > NgayDangKy),
    CONSTRAINT CK_DangKyVe_Gia CHECK (GiaVe >= 0),
    CONSTRAINT CK_DangKyVe_TrangThai CHECK (
        TrangThai IN ('GIU_CHO','DA_THANH_TOAN','DA_SU_DUNG','DA_HUY','HET_HAN')
    ),
    CONSTRAINT CK_DangKyVe_ChiemCho CHECK (
        (TrangThai IN ('GIU_CHO','DA_THANH_TOAN') AND DangChiemCho = 1)
        OR
        (TrangThai IN ('DA_SU_DUNG','DA_HUY','HET_HAN') AND DangChiemCho = 0)
    )
);
GO

/* Moi cho chi co mot dang ky dang giu/da thanh toan, an toan khi co dat dong thoi. */
CREATE UNIQUE INDEX UX_DangKyVe_ChoDangSuDung
    ON dbo.DangKyVe(ChuyenDiID, MaCho)
    WHERE DangChiemCho = 1;
GO

CREATE INDEX IX_DangKyVe_KhachHang ON dbo.DangKyVe(KhachHangID, NgayDangKy DESC);
CREATE INDEX IX_DangKyVe_HetHan ON dbo.DangKyVe(TrangThai, HanThanhToan);
GO

CREATE TABLE dbo.VeXeKhach (
    DangKyID INT NOT NULL
        CONSTRAINT PK_VeXeKhach PRIMARY KEY,
    LoaiVe VARCHAR(12) NOT NULL
        CONSTRAINT DF_VeXeKhach_Loai DEFAULT 'XE_KHACH',
    DiemDon NVARCHAR(150) NOT NULL,
    DiemTra NVARCHAR(150) NOT NULL,
    KhoiLuongHanhLy DECIMAL(6,2) NOT NULL
        CONSTRAINT DF_VeXeKhach_HanhLy DEFAULT 0,
    CONSTRAINT FK_VeXeKhach_DangKy FOREIGN KEY (DangKyID, LoaiVe)
        REFERENCES dbo.DangKyVe(DangKyID, LoaiVe),
    CONSTRAINT CK_VeXeKhach_Loai CHECK (LoaiVe = 'XE_KHACH'),
    CONSTRAINT CK_VeXeKhach_DiemDon CHECK (LEN(LTRIM(RTRIM(DiemDon))) > 0),
    CONSTRAINT CK_VeXeKhach_DiemTra CHECK (LEN(LTRIM(RTRIM(DiemTra))) > 0),
    CONSTRAINT CK_VeXeKhach_Diem CHECK (LTRIM(RTRIM(DiemDon)) <> LTRIM(RTRIM(DiemTra))),
    CONSTRAINT CK_VeXeKhach_HanhLy CHECK (KhoiLuongHanhLy BETWEEN 0 AND 30)
);
GO

CREATE TABLE dbo.VeTauThuyen (
    DangKyID INT NOT NULL
        CONSTRAINT PK_VeTauThuyen PRIMARY KEY,
    LoaiVe VARCHAR(12) NOT NULL
        CONSTRAINT DF_VeTauThuyen_Loai DEFAULT 'TAU_THUYEN',
    SoGiayTo NVARCHAR(30) NOT NULL,
    QuocTich NVARCHAR(60) NULL,
    KhoiLuongHanhLy DECIMAL(6,2) NOT NULL
        CONSTRAINT DF_VeTauThuyen_HanhLy DEFAULT 0,
    CONSTRAINT FK_VeTauThuyen_DangKy FOREIGN KEY (DangKyID, LoaiVe)
        REFERENCES dbo.DangKyVe(DangKyID, LoaiVe),
    CONSTRAINT CK_VeTauThuyen_Loai CHECK (LoaiVe = 'TAU_THUYEN'),
    CONSTRAINT CK_VeTauThuyen_GiayTo CHECK (
        LEN(SoGiayTo) BETWEEN 1 AND 30
        AND SoGiayTo NOT LIKE N'% %'
        AND CHARINDEX(NCHAR(9),SoGiayTo)=0 AND CHARINDEX(NCHAR(10),SoGiayTo)=0 AND CHARINDEX(NCHAR(13),SoGiayTo)=0
    ),
    CONSTRAINT CK_VeTauThuyen_HanhLy CHECK (KhoiLuongHanhLy BETWEEN 0 AND 40)
);
GO

/* Chinh sach phi rieng theo tung loai ve, co thoi gian hieu luc. */
CREATE TABLE dbo.ChinhSachPhi (
    ChinhSachPhiID INT IDENTITY(1,1) NOT NULL
        CONSTRAINT PK_ChinhSachPhi PRIMARY KEY,
    LoaiVe VARCHAR(12) NOT NULL,
    MaPhi VARCHAR(30) NOT NULL,
    TenPhi NVARCHAR(150) NOT NULL,
    KieuTinh VARCHAR(15) NOT NULL,
    GiaTri DECIMAL(18,2) NOT NULL,
    MoTaDieuKien NVARCHAR(500) NOT NULL,
    HieuLucTu DATETIME2(0) NOT NULL,
    HieuLucDen DATETIME2(0) NULL,
    TrangThai VARCHAR(15) NOT NULL
        CONSTRAINT DF_ChinhSachPhi_TrangThai DEFAULT 'HOAT_DONG',
    CONSTRAINT UQ_ChinhSachPhi UNIQUE (LoaiVe, MaPhi, HieuLucTu),
    CONSTRAINT CK_ChinhSachPhi_Loai CHECK (LoaiVe IN ('XE_KHACH','TAU_THUYEN')),
    CONSTRAINT CK_ChinhSachPhi_Ten CHECK (LEN(LTRIM(RTRIM(TenPhi))) > 0),
    CONSTRAINT CK_ChinhSachPhi_Kieu CHECK (KieuTinh IN ('CO_DINH','PHAN_TRAM','THEO_KG')),
    CONSTRAINT CK_ChinhSachPhi_GiaTri CHECK (GiaTri >= 0),
    CONSTRAINT CK_ChinhSachPhi_HieuLuc CHECK (HieuLucDen IS NULL OR HieuLucDen > HieuLucTu),
    CONSTRAINT CK_ChinhSachPhi_TrangThai CHECK (TrangThai IN ('HOAT_DONG','NGUNG_AP_DUNG'))
);
GO

/* Luu snapshot phi da ap dung de gia ve cu khong bi thay doi khi chinh sach doi. */
CREATE TABLE dbo.ChiTietPhi (
    ChiTietPhiID INT IDENTITY(1,1) NOT NULL
        CONSTRAINT PK_ChiTietPhi PRIMARY KEY,
    DangKyID INT NOT NULL,
    ChinhSachPhiID INT NULL,
    MaPhi VARCHAR(30) NOT NULL,
    TenPhi NVARCHAR(150) NOT NULL,
    SoTien DECIMAL(18,2) NOT NULL,
    CONSTRAINT UQ_ChiTietPhi_DangKy_Ma UNIQUE (DangKyID, MaPhi),
    CONSTRAINT FK_ChiTietPhi_DangKy FOREIGN KEY (DangKyID)
        REFERENCES dbo.DangKyVe(DangKyID),
    CONSTRAINT FK_ChiTietPhi_ChinhSach FOREIGN KEY (ChinhSachPhiID)
        REFERENCES dbo.ChinhSachPhi(ChinhSachPhiID),
    CONSTRAINT CK_ChiTietPhi_Ten CHECK (LEN(LTRIM(RTRIM(TenPhi))) > 0),
    CONSTRAINT CK_ChiTietPhi_SoTien CHECK (SoTien >= 0)
);
GO

CREATE TABLE dbo.ThanhToan (
    ThanhToanID INT IDENTITY(1,1) NOT NULL
        CONSTRAINT PK_ThanhToan PRIMARY KEY,
    DangKyID INT NOT NULL,
    MaGiaoDich VARCHAR(50) NOT NULL
        CONSTRAINT UQ_ThanhToan_MaGiaoDich UNIQUE,
    SoTien DECIMAL(18,2) NOT NULL,
    PhuongThuc VARCHAR(20) NOT NULL,
    TrangThai VARCHAR(20) NOT NULL
        CONSTRAINT DF_ThanhToan_TrangThai DEFAULT 'CHO_XU_LY',
    ThoiGian DATETIME2(0) NOT NULL
        CONSTRAINT DF_ThanhToan_ThoiGian DEFAULT SYSUTCDATETIME(),
    CONSTRAINT FK_ThanhToan_DangKy FOREIGN KEY (DangKyID)
        REFERENCES dbo.DangKyVe(DangKyID),
    CONSTRAINT CK_ThanhToan_SoTien CHECK (SoTien > 0),
    CONSTRAINT CK_ThanhToan_PhuongThuc CHECK (
        PhuongThuc IN ('TIEN_MAT','CHUYEN_KHOAN','THE','VI_DIEN_TU')
    ),
    CONSTRAINT CK_ThanhToan_TrangThai CHECK (
        TrangThai IN ('CHO_XU_LY','THANH_CONG','THAT_BAI','DA_HOAN')
    )
);
GO

/* Kiem tra lien bang va luong chuyen trang thai cua dang ky ve. */
CREATE TRIGGER dbo.TR_DangKyVe_KiemTraNghiepVu
ON dbo.DangKyVe
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;

    IF EXISTS (
        SELECT 1
        FROM inserted i
        LEFT JOIN deleted d ON d.DangKyID = i.DangKyID
        WHERE d.DangKyID IS NULL AND i.TrangThai <> 'GIU_CHO'
    )
        THROW 50003, N'Dang ky moi phai bat dau o trang thai GIU_CHO.', 1;

    IF EXISTS (
        SELECT 1
        FROM inserted i
        JOIN deleted d ON d.DangKyID = i.DangKyID
        WHERE i.TrangThai <> d.TrangThai
          AND NOT (
              (d.TrangThai = 'GIU_CHO' AND i.TrangThai IN ('DA_THANH_TOAN','DA_HUY','HET_HAN'))
              OR
              (d.TrangThai = 'DA_THANH_TOAN' AND i.TrangThai IN ('DA_SU_DUNG','DA_HUY'))
          )
    )
        THROW 50004, N'Chuyen trang thai dang ky ve khong hop le.', 1;

    IF EXISTS (
        SELECT 1
        FROM inserted i
        JOIN dbo.ChuyenDi c ON c.ChuyenDiID = i.ChuyenDiID
        JOIN dbo.ChoNgoi g ON g.ChuyenDiID = i.ChuyenDiID AND g.MaCho = i.MaCho
        WHERE i.DangChiemCho = 1
          AND (
              c.TrangThai <> 'MO_BAN'
              OR g.TrangThai <> 'SAN_SANG'
              OR i.NgayDangKy >= c.KhoiHanh
              OR i.HanThanhToan > c.KhoiHanh
          )
    )
        THROW 50005, N'Chuyen/cho khong san sang hoac thoi gian dat ve khong hop le.', 1;
END;
GO

/* Khong cho giam suc chua thap hon so cho da khai bao. */
CREATE TRIGGER dbo.TR_ChuyenDi_KiemTraCapNhatSucChua
ON dbo.ChuyenDi
AFTER UPDATE
AS
BEGIN
    SET NOCOUNT ON;

    IF UPDATE(TongSoCho) AND EXISTS (
        SELECT 1
        FROM inserted i
        CROSS APPLY (
            SELECT COUNT_BIG(*) AS SoChoDaKhaiBao
            FROM dbo.ChoNgoi g
            WHERE g.ChuyenDiID = i.ChuyenDiID
        ) s
        WHERE s.SoChoDaKhaiBao > i.TongSoCho
    )
        THROW 50006, N'Khong the giam suc chua thap hon so cho da khai bao.', 1;
END;
GO

/* Job/Spring Scheduler goi dinh ky de giai phong cho het han. */
CREATE PROCEDURE dbo.sp_CapNhatVeHetHan
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    UPDATE dbo.DangKyVe
    SET TrangThai = 'HET_HAN', DangChiemCho = 0
    WHERE TrangThai = 'GIU_CHO'
      AND HanThanhToan <= SYSUTCDATETIME();

    SELECT @@ROWCOUNT AS SoVeDaHetHan;
END;
GO

CREATE VIEW dbo.vw_ChiTietDangKyVe
AS
    SELECT
        d.DangKyID,
        d.DangKyCode,
        k.HoTen AS KhachHang,
        k.SoDienThoai,
        d.TenHanhKhach,
        d.LoaiVe,
        c.MaChuyen,
        c.NoiDi,
        c.NoiDen,
        c.KhoiHanh,
        d.MaCho,
        g.LoaiCho,
        d.GiaVe,
        ISNULL(p.TongPhi, 0) AS TongPhi,
        d.GiaVe + ISNULL(p.TongPhi, 0) AS TongTien,
        d.TrangThai,
        d.HanThanhToan
    FROM dbo.DangKyVe d
    JOIN dbo.KhachHang k ON k.KhachHangID = d.KhachHangID
    JOIN dbo.ChuyenDi c ON c.ChuyenDiID = d.ChuyenDiID
    JOIN dbo.ChoNgoi g ON g.ChuyenDiID = d.ChuyenDiID AND g.MaCho = d.MaCho
    OUTER APPLY (
        SELECT SUM(ct.SoTien) AS TongPhi
        FROM dbo.ChiTietPhi ct
        WHERE ct.DangKyID = d.DangKyID
    ) p;
GO

/* Audit: ket qua dung phai la 0 dong. Service tao cha + con trong cung @Transactional. */
CREATE VIEW dbo.vw_DangKyThieuChiTietLoaiVe
AS
    SELECT d.DangKyID, d.DangKyCode, d.LoaiVe
    FROM dbo.DangKyVe d
    LEFT JOIN dbo.VeXeKhach x ON x.DangKyID = d.DangKyID
    LEFT JOIN dbo.VeTauThuyen t ON t.DangKyID = d.DangKyID
    WHERE (d.LoaiVe = 'XE_KHACH' AND (x.DangKyID IS NULL OR t.DangKyID IS NOT NULL))
       OR (d.LoaiVe = 'TAU_THUYEN' AND (t.DangKyID IS NULL OR x.DangKyID IS NOT NULL));
GO

/* =========================== DU LIEU MAU =========================== */
BEGIN TRY
    BEGIN TRANSACTION;

    DECLARE @Now DATETIME2(0) = SYSUTCDATETIME();

    INSERT dbo.KhachHang(HoTen, SoDienThoai, Email)
    VALUES (N'Nguyễn Văn An', '0901234567', 'an@example.com');
    DECLARE @KhachHangID INT = SCOPE_IDENTITY();

    INSERT dbo.ChuyenDi(
        MaChuyen, LoaiVe, DonViVanChuyen, NoiDi, NoiDen,
        KhoiHanh, DuKienDen, TongSoCho, GiaCoBan
    )
    VALUES (
        'XK001', 'XE_KHACH', N'Nhà xe Minh Họa', N'TP. Hồ Chí Minh', N'Đà Lạt',
        DATEADD(DAY, 7, @Now), DATEADD(HOUR, 176, @Now), 40, 250000
    );
    DECLARE @ChuyenXeID INT = SCOPE_IDENTITY();

    INSERT dbo.ChuyenDi(
        MaChuyen, LoaiVe, DonViVanChuyen, NoiDi, NoiDen,
        KhoiHanh, DuKienDen, TongSoCho, GiaCoBan
    )
    VALUES (
        'TT001', 'TAU_THUYEN', N'Hãng tàu Minh Họa', N'Rạch Giá', N'Phú Quốc',
        DATEADD(DAY, 8, @Now), DATEADD(HOUR, 195, @Now), 100, 350000
    );
    DECLARE @ChuyenTauID INT = SCOPE_IDENTITY();

    INSERT dbo.ChoNgoi(ChuyenDiID, MaCho, LoaiVe, LoaiCho, PhuThu)
    VALUES
        (@ChuyenXeID, 'A01', 'XE_KHACH', 'GHE_NGOI', 0),
        (@ChuyenXeID, 'B01', 'XE_KHACH', 'GIUONG_NAM', 80000),
        (@ChuyenTauID, 'P001', 'TAU_THUYEN', 'PHO_THONG', 0),
        (@ChuyenTauID, 'V001', 'TAU_THUYEN', 'VIP', 150000);

    INSERT dbo.LichHenTuVan(
        MaLichHen, KhachHangID, ChuyenDiID, LoaiVe, ThoiGianHen,
        HinhThuc, NoiDung, NhanVienPhuTrach, TrangThai, NgayTao
    )
    VALUES (
        'LH001', @KhachHangID, @ChuyenXeID, 'XE_KHACH', DATEADD(DAY, 1, @Now),
        'DIEN_THOAI', N'Tư vấn điểm đón và hành lý', N'Nhân viên A', 'DA_XAC_NHAN', @Now
    );

    INSERT dbo.ChinhSachPhi(
        LoaiVe, MaPhi, TenPhi, KieuTinh, GiaTri, MoTaDieuKien, HieuLucTu
    )
    VALUES
        ('XE_KHACH', 'PHI_GIUONG_NAM', N'Phụ thu giường nằm', 'CO_DINH', 80000,
         N'Áp dụng khi khách chọn chỗ giường nằm.', DATEADD(DAY, -1, @Now)),
        ('XE_KHACH', 'PHI_HANH_LY_XE', N'Phí hành lý xe khách vượt 20 kg', 'THEO_KG', 10000,
         N'Tính trên mỗi kg vượt quá 20 kg; tổng hành lý tối đa 30 kg.', DATEADD(DAY, -1, @Now)),
        ('TAU_THUYEN', 'PHI_HANG_VIP', N'Phụ thu hạng VIP', 'CO_DINH', 150000,
         N'Áp dụng khi khách chọn chỗ hạng VIP.', DATEADD(DAY, -1, @Now)),
        ('TAU_THUYEN', 'PHI_CANG', N'Phí cảng', 'CO_DINH', 25500,
         N'Áp dụng cho mỗi hành khách tàu/thuyền.', DATEADD(DAY, -1, @Now)),
        ('TAU_THUYEN', 'PHI_HANH_LY_TAU', N'Phí hành lý tàu/thuyền vượt 25 kg', 'THEO_KG', 12000,
         N'Tính trên mỗi kg vượt quá 25 kg; tổng hành lý tối đa 40 kg.', DATEADD(DAY, -1, @Now));

    INSERT dbo.DangKyVe(
        DangKyCode, KhachHangID, ChuyenDiID, LoaiVe, TenHanhKhach,
        MaCho, NgayDangKy, HanThanhToan, GiaVe
    )
    VALUES (
        'DKX001', @KhachHangID, @ChuyenXeID, 'XE_KHACH', N'Nguyễn Văn An',
        'A01', @Now, DATEADD(MINUTE, 30, @Now), 250000
    );
    DECLARE @VeXeID INT = SCOPE_IDENTITY();

    INSERT dbo.VeXeKhach(DangKyID, DiemDon, DiemTra, KhoiLuongHanhLy)
    VALUES (@VeXeID, N'Bến xe Miền Đông', N'Bến xe Đà Lạt', 10);

    INSERT dbo.DangKyVe(
        DangKyCode, KhachHangID, ChuyenDiID, LoaiVe, TenHanhKhach,
        MaCho, NgayDangKy, HanThanhToan, GiaVe
    )
    VALUES (
        'DKT001', @KhachHangID, @ChuyenTauID, 'TAU_THUYEN', N'Nguyễn Văn An',
        'P001', @Now, DATEADD(MINUTE, 60, @Now), 350000
    );
    DECLARE @VeTauID INT = SCOPE_IDENTITY();

    INSERT dbo.VeTauThuyen(DangKyID, SoGiayTo, QuocTich, KhoiLuongHanhLy)
    VALUES (@VeTauID, '012345678901', N'Việt Nam', 15);

    DECLARE @PhiCangID INT = (
        SELECT TOP (1) ChinhSachPhiID
        FROM dbo.ChinhSachPhi
        WHERE LoaiVe = 'TAU_THUYEN' AND MaPhi = 'PHI_CANG'
        ORDER BY HieuLucTu DESC
    );

    INSERT dbo.ChiTietPhi(DangKyID, ChinhSachPhiID, MaPhi, TenPhi, SoTien)
    VALUES (@VeTauID, @PhiCangID, 'PHI_CANG', N'Phí cảng', 25500);

    INSERT dbo.ThanhToan(DangKyID, MaGiaoDich, SoTien, PhuongThuc, TrangThai)
    VALUES (@VeTauID, 'GD001', 375500, 'CHUYEN_KHOAN', 'THANH_CONG');

    UPDATE dbo.DangKyVe
    SET TrangThai = 'DA_THANH_TOAN', DangChiemCho = 1
    WHERE DangKyID = @VeTauID;

    COMMIT;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK;
    THROW;
END CATCH;
GO

/* Kiem tra nhanh: audit phai rong; view chi tiet phai co 2 dong. */
SELECT * FROM dbo.vw_ChiTietDangKyVe ORDER BY DangKyID;
SELECT * FROM dbo.vw_DangKyThieuChiTietLoaiVe;
GO
