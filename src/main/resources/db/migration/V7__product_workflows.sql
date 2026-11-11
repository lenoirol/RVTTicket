-- Additive upgrade: no existing account, booking, price or balance is rewritten.
IF COL_LENGTH('dbo.TaiKhoanKhach','Email') IS NULL
    ALTER TABLE dbo.TaiKhoanKhach ADD Email NVARCHAR(254) NULL;
IF COL_LENGTH('dbo.TaiKhoanKhach','PhienBanDangNhap') IS NULL
    ALTER TABLE dbo.TaiKhoanKhach ADD PhienBanDangNhap INT NOT NULL DEFAULT 0;
IF COL_LENGTH('dbo.DonDatVe','YeuCauDat') IS NULL
    ALTER TABLE dbo.DonDatVe ADD YeuCauDat VARCHAR(36) NULL;
IF COL_LENGTH('dbo.DonDatVe','HuyLuc') IS NULL
    ALTER TABLE dbo.DonDatVe ADD HuyLuc DATETIME2(0) NULL;
IF COL_LENGTH('dbo.ThongBaoKhach','LienKet') IS NULL
    ALTER TABLE dbo.ThongBaoKhach ADD LienKet VARCHAR(150) NULL;
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE name='UX_DonDatVe_YeuCauDat' AND object_id=OBJECT_ID('dbo.DonDatVe'))
    EXEC(N'CREATE UNIQUE INDEX UX_DonDatVe_YeuCauDat ON dbo.DonDatVe(YeuCauDat) WHERE YeuCauDat IS NOT NULL');
IF OBJECT_ID('dbo.YeuCauHoTro','U') IS NULL
    CREATE TABLE dbo.YeuCauHoTro (
        YeuCauID INT IDENTITY PRIMARY KEY,
        MaYeuCau VARCHAR(24) NOT NULL UNIQUE,
        TaiKhoanID INT NOT NULL REFERENCES dbo.TaiKhoanKhach(TaiKhoanID),
        DonDatVeID INT NULL REFERENCES dbo.DonDatVe(DonDatVeID),
        ChuDe VARCHAR(20) NOT NULL CHECK(ChuDe IN('VE','THANH_TOAN','HANH_TRINH','KHAC')),
        TieuDe NVARCHAR(120) NOT NULL,
        TrangThai VARCHAR(20) NOT NULL DEFAULT 'MOI' CHECK(TrangThai IN('MOI','DANG_XU_LY','DA_GIAI_QUYET')),
        NgayTao DATETIME2(0) NOT NULL DEFAULT SYSUTCDATETIME(),
        CapNhatLuc DATETIME2(0) NOT NULL DEFAULT SYSUTCDATETIME()
    );
IF OBJECT_ID('dbo.TinNhanHoTro','U') IS NULL
    CREATE TABLE dbo.TinNhanHoTro (
        TinNhanID BIGINT IDENTITY PRIMARY KEY,
        YeuCauID INT NOT NULL REFERENCES dbo.YeuCauHoTro(YeuCauID),
        TuQuanTri BIT NOT NULL DEFAULT 0,
        NoiDung NVARCHAR(2000) NOT NULL,
        NgayTao DATETIME2(0) NOT NULL DEFAULT SYSUTCDATETIME()
    );
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE name='IX_HoTro_Khach' AND object_id=OBJECT_ID('dbo.YeuCauHoTro'))
    CREATE INDEX IX_HoTro_Khach ON dbo.YeuCauHoTro(TaiKhoanID,CapNhatLuc DESC);
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE name='IX_TinNhanHoTro_YeuCau' AND object_id=OBJECT_ID('dbo.TinNhanHoTro'))
    CREATE INDEX IX_TinNhanHoTro_YeuCau ON dbo.TinNhanHoTro(YeuCauID,TinNhanID);
