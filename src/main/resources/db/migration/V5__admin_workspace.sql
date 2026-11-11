IF OBJECT_ID(N'dbo.TaiKhoanQuanTri',N'U') IS NULL
BEGIN
    CREATE TABLE dbo.TaiKhoanQuanTri (
        TaiKhoanID INT IDENTITY PRIMARY KEY,
        TenDangNhap VARCHAR(40) NOT NULL UNIQUE,
        MatKhau VARCHAR(100) NOT NULL,
        HoTen NVARCHAR(100) NOT NULL,
        NgayTao DATETIME2(0) NOT NULL DEFAULT SYSUTCDATETIME(),
        DoiMatKhauLuc DATETIME2(0) NULL
    );
END;
IF COL_LENGTH('dbo.TaiKhoanKhach','BiKhoa') IS NULL
    ALTER TABLE dbo.TaiKhoanKhach ADD BiKhoa BIT NOT NULL CONSTRAINT DF_TaiKhoanKhach_BiKhoa DEFAULT 0;
IF COL_LENGTH('dbo.TaiKhoanKhach','LyDoKhoa') IS NULL
    ALTER TABLE dbo.TaiKhoanKhach ADD LyDoKhoa NVARCHAR(500) NULL;
IF COL_LENGTH('dbo.TaiKhoanKhach','KhoaLuc') IS NULL
    ALTER TABLE dbo.TaiKhoanKhach ADD KhoaLuc DATETIME2(0) NULL;
IF COL_LENGTH('dbo.TaiKhoanKhach','KhoaBoi') IS NULL
    ALTER TABLE dbo.TaiKhoanKhach ADD KhoaBoi VARCHAR(40) NULL;
IF COL_LENGTH('dbo.NhatKyThaoTac','NoiDung') IS NULL
    ALTER TABLE dbo.NhatKyThaoTac ADD NoiDung NVARCHAR(500) NULL;
