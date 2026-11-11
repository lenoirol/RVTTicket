IF OBJECT_ID(N'dbo.ThongTinChuyen',N'U') IS NULL
BEGIN
    CREATE TABLE dbo.ThongTinChuyen (
        ChuyenDiID INT PRIMARY KEY REFERENCES dbo.ChuyenDi(ChuyenDiID),
        DiemDon NVARCHAR(300) NOT NULL,
        DiemTra NVARCHAR(300) NOT NULL,
        TienIch NVARCHAR(500) NOT NULL DEFAULT N'',
        MoTa NVARCHAR(1000) NOT NULL DEFAULT N''
    );
END;
IF OBJECT_ID(N'dbo.QuyTacVe',N'U') IS NULL
BEGIN
    CREATE TABLE dbo.QuyTacVe (
        LoaiVe VARCHAR(12) PRIMARY KEY CHECK(LoaiVe IN ('XE_KHACH','TAU_THUYEN')),
        MienPhiKg DECIMAL(6,2) NOT NULL CHECK(MienPhiKg>=0),
        ToiDaKg DECIMAL(6,2) NOT NULL,
        GiaMoiKg DECIMAL(18,2) NOT NULL CHECK(GiaMoiKg>=0),
        GioHanHuy INT NOT NULL CHECK(GioHanHuy BETWEEN 1 AND 168),
        CONSTRAINT CK_QuyTacVe_Kg CHECK(ToiDaKg>=MienPhiKg AND ((LoaiVe='XE_KHACH' AND ToiDaKg<=30) OR (LoaiVe='TAU_THUYEN' AND ToiDaKg<=40)))
    );
    INSERT dbo.QuyTacVe VALUES('XE_KHACH',20,30,10000,24),('TAU_THUYEN',25,40,15000,24);
END;
IF OBJECT_ID(N'dbo.TaiKhoanKhach',N'U') IS NULL
BEGIN
    CREATE TABLE dbo.TaiKhoanKhach (
        TaiKhoanID INT IDENTITY PRIMARY KEY,
        TenDangNhap VARCHAR(40) NOT NULL UNIQUE,
        MatKhau VARCHAR(100) NOT NULL,
        HoTen NVARCHAR(100) NOT NULL,
        SoDienThoai VARCHAR(15) NOT NULL,
        NgayTao DATETIME2(0) NOT NULL DEFAULT SYSUTCDATETIME()
    );
END;
IF COL_LENGTH('dbo.DonDatVe','TaiKhoanID') IS NULL
    ALTER TABLE dbo.DonDatVe ADD TaiKhoanID INT NULL REFERENCES dbo.TaiKhoanKhach(TaiKhoanID);
IF COL_LENGTH('dbo.DonDatVe','GioHanHuy') IS NULL
    ALTER TABLE dbo.DonDatVe ADD GioHanHuy INT NOT NULL DEFAULT 24;
IF OBJECT_ID(N'dbo.NhatKyThaoTac',N'U') IS NULL
BEGIN
    CREATE TABLE dbo.NhatKyThaoTac (
        NhatKyID BIGINT IDENTITY PRIMARY KEY,
        NguoiThucHien VARCHAR(40) NOT NULL,
        HanhDong VARCHAR(40) NOT NULL,
        DoiTuong NVARCHAR(100) NOT NULL,
        NgayTao DATETIME2(0) NOT NULL DEFAULT SYSUTCDATETIME()
    );
END;
IF COL_LENGTH('dbo.DonDatVe','DiemDonSnapshot') IS NULL
    ALTER TABLE dbo.DonDatVe ADD DiemDonSnapshot NVARCHAR(150) NULL;
IF COL_LENGTH('dbo.DonDatVe','DiemTraSnapshot') IS NULL
    ALTER TABLE dbo.DonDatVe ADD DiemTraSnapshot NVARCHAR(150) NULL;
