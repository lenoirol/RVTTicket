-- Payment attempts are separate from settled ticket receipts. No legacy rows are rewritten.
IF OBJECT_ID(N'dbo.GiaoDichVNPay',N'U') IS NULL
BEGIN
    CREATE TABLE dbo.GiaoDichVNPay (
        MaThamChieu VARCHAR(40) NOT NULL PRIMARY KEY,
        DonDatVeID INT NOT NULL REFERENCES dbo.DonDatVe(DonDatVeID),
        SoTien DECIMAL(18,2) NOT NULL CHECK(SoTien>0),
        TrangThai VARCHAR(20) NOT NULL DEFAULT 'PENDING'
            CHECK(TrangThai IN('PENDING','SUCCESS','FAILED','REVIEW')),
        TmnCode VARCHAR(8) NOT NULL,
        NgayTao DATETIME2(0) NOT NULL DEFAULT SYSUTCDATETIME(),
        HetHanLuc DATETIME2(0) NOT NULL,
        DiaChiIP VARCHAR(45) NOT NULL,
        MaVNPay VARCHAR(15) NULL,
        MaNganHang VARCHAR(20) NULL,
        MaKetQua VARCHAR(2) NULL,
        TrangThaiVNPay VARCHAR(2) NULL,
        NhanIPNLuc DATETIME2(0) NULL,
        GhiChu NVARCHAR(500) NULL,
        CONSTRAINT CK_VNPay_Han CHECK(HetHanLuc>NgayTao)
    );
    CREATE INDEX IX_VNPay_Don ON dbo.GiaoDichVNPay(DonDatVeID,NgayTao DESC);
    CREATE UNIQUE INDEX UX_VNPay_Receipt ON dbo.GiaoDichVNPay(MaVNPay) WHERE MaVNPay IS NOT NULL;
END;
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE name='CK_ThanhToan_PhuongThuc' AND definition LIKE '%VNPAY%')
BEGIN
    ALTER TABLE dbo.ThanhToan DROP CONSTRAINT CK_ThanhToan_PhuongThuc;
    ALTER TABLE dbo.ThanhToan ADD CONSTRAINT CK_ThanhToan_PhuongThuc
        CHECK(PhuongThuc IN('TIEN_MAT','CHUYEN_KHOAN','THE','VI_DIEN_TU','SO_DU_RVT','VNPAY'));
END;
