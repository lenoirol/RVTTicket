-- Additive catalogue, reusable vehicle layouts and per-ticket verification tokens.
IF OBJECT_ID(N'dbo.TinhThanh',N'U') IS NULL
BEGIN
    CREATE TABLE dbo.TinhThanh(MaTinh VARCHAR(40) PRIMARY KEY,TenTinh NVARCHAR(100) NOT NULL UNIQUE,TenCu NVARCHAR(300) NOT NULL DEFAULT N'');
    CREATE TABLE dbo.DiaDiem(MaDiem VARCHAR(60) PRIMARY KEY,MaTinh VARCHAR(40) NOT NULL REFERENCES dbo.TinhThanh(MaTinh),TenDiem NVARCHAR(150) NOT NULL UNIQUE);
    CREATE INDEX IX_DiaDiem_Tinh ON dbo.DiaDiem(MaTinh);
END;
IF OBJECT_ID(N'dbo.PhuongTien',N'U') IS NULL
BEGIN
    CREATE TABLE dbo.PhuongTien(
        PhuongTienID INT IDENTITY PRIMARY KEY,MaPhuongTien VARCHAR(30) NOT NULL UNIQUE,
        TenPhuongTien NVARCHAR(100) NOT NULL,SoDangKy NVARCHAR(40) NOT NULL UNIQUE,
        DonViVanChuyen NVARCHAR(100) NOT NULL,LoaiVe VARCHAR(12) NOT NULL CHECK(LoaiVe IN('XE_KHACH','TAU_THUYEN')),
        HoatDong BIT NOT NULL DEFAULT 1
    );
    CREATE TABLE dbo.ChoPhuongTien(
        PhuongTienID INT NOT NULL REFERENCES dbo.PhuongTien(PhuongTienID),MaCho VARCHAR(20) NOT NULL,
        LoaiCho VARCHAR(20) NOT NULL CHECK(LoaiCho IN('GHE_NGOI','GIUONG_NAM','PHO_THONG','VIP')),
        PhuThu DECIMAL(18,2) NOT NULL CHECK(PhuThu BETWEEN 0 AND 1000000),
        Tang INT NOT NULL CHECK(Tang BETWEEN 1 AND 3),Hang INT NOT NULL CHECK(Hang BETWEEN 1 AND 30),
        Cot INT NOT NULL CHECK(Cot BETWEEN 1 AND 6),
        PRIMARY KEY(PhuongTienID,MaCho),UNIQUE(PhuongTienID,Tang,Hang,Cot)
    );
END;
IF COL_LENGTH('dbo.ChuyenDi','PhuongTienID') IS NULL
    ALTER TABLE dbo.ChuyenDi ADD PhuongTienID INT NULL REFERENCES dbo.PhuongTien(PhuongTienID);
IF COL_LENGTH('dbo.ChuyenDi','TenPhuongTienSnapshot') IS NULL
    ALTER TABLE dbo.ChuyenDi ADD TenPhuongTienSnapshot NVARCHAR(100) NULL,SoDangKySnapshot NVARCHAR(40) NULL;
IF COL_LENGTH('dbo.ChuyenDi','MaDiemDi') IS NULL
    ALTER TABLE dbo.ChuyenDi ADD MaDiemDi VARCHAR(60) NULL REFERENCES dbo.DiaDiem(MaDiem),MaDiemDen VARCHAR(60) NULL REFERENCES dbo.DiaDiem(MaDiem);
IF COL_LENGTH('dbo.ChuyenDi','LyDoHuy') IS NULL
    ALTER TABLE dbo.ChuyenDi ADD LyDoHuy NVARCHAR(500) NULL;
IF COL_LENGTH('dbo.DonDatVe','LyDoHuy') IS NULL
    ALTER TABLE dbo.DonDatVe ADD LyDoHuy NVARCHAR(500) NULL;
IF COL_LENGTH('dbo.ThanhToan','HoanLuc') IS NULL
    ALTER TABLE dbo.ThanhToan ADD HoanLuc DATETIME2(0) NULL;
IF COL_LENGTH('dbo.DangKyVe','QrToken') IS NULL
    ALTER TABLE dbo.DangKyVe ADD QrToken VARCHAR(64) NULL;
-- Dynamic batches allow the column to be introduced and then used in one startup.
EXEC(N'UPDATE dbo.DangKyVe SET QrToken=CONVERT(VARCHAR(64),CRYPT_GEN_RANDOM(32),2) WHERE QrToken IS NULL');
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE name='UX_DangKyVe_QrToken' AND object_id=OBJECT_ID('dbo.DangKyVe'))
    EXEC(N'CREATE UNIQUE INDEX UX_DangKyVe_QrToken ON dbo.DangKyVe(QrToken) WHERE QrToken IS NOT NULL');
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE name='CK_ChuyenDi_TrangThai_V4')
BEGIN
    ALTER TABLE dbo.ChuyenDi DROP CONSTRAINT CK_ChuyenDi_TrangThai;
    ALTER TABLE dbo.ChuyenDi ADD CONSTRAINT CK_ChuyenDi_TrangThai_V4 CHECK(TrangThai IN('MO_BAN','DONG_BAN','DA_KHOI_HANH','HOAN_THANH','DA_HUY'));
END;
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE name='CK_DonDatVe_TrangThai_V4')
BEGIN
    ALTER TABLE dbo.DonDatVe DROP CONSTRAINT CK_DonDatVe_TrangThai;
    ALTER TABLE dbo.DonDatVe ADD CONSTRAINT CK_DonDatVe_TrangThai_V4 CHECK(TrangThai IN('GIU_CHO','DA_THANH_TOAN','DA_SU_DUNG','DA_HUY','HET_HAN'));
END;
