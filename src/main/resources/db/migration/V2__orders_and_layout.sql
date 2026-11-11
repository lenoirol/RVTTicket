-- Additive upgrade: preserves all existing tickets and customer records.
IF OBJECT_ID(N'dbo.DonDatVe', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.DonDatVe (
        DonDatVeID INT IDENTITY PRIMARY KEY,
        MaDon VARCHAR(30) NOT NULL UNIQUE,
        KhachHangID INT NOT NULL REFERENCES dbo.KhachHang(KhachHangID),
        ChuyenDiID INT NOT NULL REFERENCES dbo.ChuyenDi(ChuyenDiID),
        TrangThai VARCHAR(20) NOT NULL DEFAULT 'GIU_CHO',
        TongTien DECIMAL(18,2) NOT NULL CHECK (TongTien >= 0),
        NgayTao DATETIME2(0) NOT NULL DEFAULT SYSUTCDATETIME(),
        HanThanhToan DATETIME2(0) NOT NULL,
        CONSTRAINT CK_DonDatVe_TrangThai CHECK (TrangThai IN ('GIU_CHO','DA_THANH_TOAN','DA_HUY','HET_HAN')),
        CONSTRAINT CK_DonDatVe_Han CHECK (HanThanhToan > NgayTao)
    );
    CREATE TABLE dbo.DonDatVeChiTiet (
        DonDatVeID INT NOT NULL REFERENCES dbo.DonDatVe(DonDatVeID),
        DangKyID INT NOT NULL UNIQUE REFERENCES dbo.DangKyVe(DangKyID),
        PRIMARY KEY (DonDatVeID, DangKyID)
    );
    CREATE INDEX IX_DonDatVe_HetHan ON dbo.DonDatVe(TrangThai, HanThanhToan);
END;
IF OBJECT_ID(N'dbo.SoDoCho', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.SoDoCho (
        ChuyenDiID INT NOT NULL,
        MaCho VARCHAR(20) NOT NULL,
        Tang INT NOT NULL CHECK (Tang BETWEEN 1 AND 3),
        Hang INT NOT NULL CHECK (Hang > 0),
        Cot INT NOT NULL CHECK (Cot BETWEEN 1 AND 6),
        PRIMARY KEY (ChuyenDiID, MaCho),
        UNIQUE (ChuyenDiID, Tang, Hang, Cot),
        FOREIGN KEY (ChuyenDiID, MaCho) REFERENCES dbo.ChoNgoi(ChuyenDiID, MaCho)
    );
END;
