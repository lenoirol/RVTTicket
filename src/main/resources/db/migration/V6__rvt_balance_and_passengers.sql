-- Additive, repeatable upgrade. Historical bookings and fares are not rewritten.
IF COL_LENGTH('dbo.TaiKhoanKhach','SoDuRVT') IS NULL
    ALTER TABLE dbo.TaiKhoanKhach ADD SoDuRVT DECIMAL(18,2) NOT NULL CONSTRAINT DF_Khach_SoDu DEFAULT 0 CONSTRAINT CK_Khach_SoDu CHECK(SoDuRVT>=0);
IF OBJECT_ID('dbo.GiaoDichRVT','U') IS NULL
    CREATE TABLE dbo.GiaoDichRVT (
        GiaoDichID BIGINT IDENTITY PRIMARY KEY,
        TaiKhoanID INT NOT NULL REFERENCES dbo.TaiKhoanKhach(TaiKhoanID),
        DonDatVeID INT NOT NULL REFERENCES dbo.DonDatVe(DonDatVeID),
        MaSuKien VARCHAR(70) NOT NULL UNIQUE,
        SoTien DECIMAL(18,2) NOT NULL CHECK(SoTien<>0),
        SoDuSau DECIMAL(18,2) NOT NULL CHECK(SoDuSau>=0),
        NoiDung NVARCHAR(300) NOT NULL,
        NgayTao DATETIME2(0) NOT NULL DEFAULT SYSUTCDATETIME()
    );
IF OBJECT_ID('dbo.HoanRVTChoNhan','U') IS NULL
    CREATE TABLE dbo.HoanRVTChoNhan (
        DonDatVeID INT PRIMARY KEY REFERENCES dbo.DonDatVe(DonDatVeID),
        SoTien DECIMAL(18,2) NOT NULL CHECK(SoTien>0),
        DaNhan BIT NOT NULL DEFAULT 0,
        NgayTao DATETIME2(0) NOT NULL DEFAULT SYSUTCDATETIME()
    );
IF OBJECT_ID('dbo.ThongBaoKhach','U') IS NULL
    CREATE TABLE dbo.ThongBaoKhach (
        ThongBaoID BIGINT IDENTITY PRIMARY KEY,
        TaiKhoanID INT NOT NULL REFERENCES dbo.TaiKhoanKhach(TaiKhoanID),
        MaSuKien VARCHAR(80) NOT NULL UNIQUE,
        MaDon VARCHAR(30) NOT NULL,
        TieuDe NVARCHAR(100) NOT NULL,
        NoiDung NVARCHAR(500) NOT NULL,
        DaDoc BIT NOT NULL DEFAULT 0,
        NgayTao DATETIME2(0) NOT NULL DEFAULT SYSUTCDATETIME()
    );
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE name='IX_ThongBaoKhach_TaiKhoan' AND object_id=OBJECT_ID('dbo.ThongBaoKhach'))
    CREATE INDEX IX_ThongBaoKhach_TaiKhoan ON dbo.ThongBaoKhach(TaiKhoanID,ThongBaoID DESC);
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE name='IX_GiaoDichRVT_TaiKhoan' AND object_id=OBJECT_ID('dbo.GiaoDichRVT'))
    CREATE INDEX IX_GiaoDichRVT_TaiKhoan ON dbo.GiaoDichRVT(TaiKhoanID,GiaoDichID DESC);
IF COL_LENGTH('dbo.ChuyenDi','GiaTreEm') IS NULL
    ALTER TABLE dbo.ChuyenDi ADD GiaTreEm DECIMAL(18,2) NULL CHECK(GiaTreEm>=1000 AND GiaTreEm<=10000000);
IF COL_LENGTH('dbo.ChuyenDi','GiaCaoTuoi') IS NULL
    ALTER TABLE dbo.ChuyenDi ADD GiaCaoTuoi DECIMAL(18,2) NULL CHECK(GiaCaoTuoi>=1000 AND GiaCaoTuoi<=10000000);
IF COL_LENGTH('dbo.DangKyVe','NgaySinh') IS NULL
    ALTER TABLE dbo.DangKyVe ADD NgaySinh DATE NULL;
IF COL_LENGTH('dbo.DangKyVe','NhomHanhKhach') IS NULL
    ALTER TABLE dbo.DangKyVe ADD NhomHanhKhach VARCHAR(15) NOT NULL DEFAULT 'NGUOI_LON' CHECK(NhomHanhKhach IN('NGUOI_LON','TRE_EM','CAO_TUOI'));
IF COL_LENGTH('dbo.DangKyVe','LoaiGiayTo') IS NULL
    ALTER TABLE dbo.DangKyVe ADD LoaiGiayTo VARCHAR(15) NULL;
-- Keep existing methods, add an explicit internal balance method.
IF OBJECT_ID('dbo.CK_ThanhToan_PhuongThuc','C') IS NOT NULL
    ALTER TABLE dbo.ThanhToan DROP CONSTRAINT CK_ThanhToan_PhuongThuc;
ALTER TABLE dbo.ThanhToan ADD CONSTRAINT CK_ThanhToan_PhuongThuc CHECK(PhuongThuc IN('TIEN_MAT','CHUYEN_KHOAN','THE','VI_DIEN_TU','SO_DU_RVT','VNPAY'));
