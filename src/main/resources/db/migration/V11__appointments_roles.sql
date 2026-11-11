-- Đề 5 (Quản lý đặt vé du lịch): lịch hẹn tư vấn + vai trò RBAC. Chỉ thêm cấu trúc, không xóa hay sửa dữ liệu cũ.

-- Vai trò khách hàng: TRAVELER (mặc định) hoặc AGENT (đại lý đặt hộ khách).
IF COL_LENGTH('dbo.TaiKhoanKhach','VaiTro') IS NULL
    ALTER TABLE dbo.TaiKhoanKhach ADD VaiTro VARCHAR(12) NOT NULL CONSTRAINT DF_TaiKhoanKhach_VaiTro DEFAULT 'TRAVELER';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE name='CK_TaiKhoanKhach_VaiTro')
    EXEC(N'ALTER TABLE dbo.TaiKhoanKhach ADD CONSTRAINT CK_TaiKhoanKhach_VaiTro CHECK(VaiTro IN(''TRAVELER'',''AGENT''))');

-- Vai trò nhân sự nội bộ: ADMIN (mặc định, toàn quyền) hoặc OPERATOR (vận hành: đơn vé, QR, lịch hẹn, hỗ trợ).
IF COL_LENGTH('dbo.TaiKhoanQuanTri','VaiTro') IS NULL
    ALTER TABLE dbo.TaiKhoanQuanTri ADD VaiTro VARCHAR(12) NOT NULL CONSTRAINT DF_TaiKhoanQuanTri_VaiTro DEFAULT 'ADMIN';
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE name='CK_TaiKhoanQuanTri_VaiTro')
    EXEC(N'ALTER TABLE dbo.TaiKhoanQuanTri ADD CONSTRAINT CK_TaiKhoanQuanTri_VaiTro CHECK(VaiTro IN(''ADMIN'',''OPERATOR''))');

-- Lịch hẹn tư vấn (Appointment Booking Workflow).
IF OBJECT_ID('dbo.LichHenDatCho','U') IS NULL
    CREATE TABLE dbo.LichHenDatCho (
        LichHenID INT IDENTITY PRIMARY KEY,
        MaLichHen VARCHAR(20) NOT NULL UNIQUE,
        TaiKhoanID INT NOT NULL REFERENCES dbo.TaiKhoanKhach(TaiKhoanID),
        LoaiVe VARCHAR(12) NOT NULL CHECK(LoaiVe IN('XE_KHACH','TAU_THUYEN')),
        HoTen NVARCHAR(100) NOT NULL,
        SoDienThoai VARCHAR(15) NOT NULL,
        NgayHen DATE NOT NULL,
        KhungGio VARCHAR(5) NOT NULL,
        GhiChu NVARCHAR(500) NULL,
        MaDon VARCHAR(30) NULL,
        TrangThai VARCHAR(12) NOT NULL DEFAULT 'MOI' CHECK(TrangThai IN('MOI','DA_XAC_NHAN','HOAN_THANH','DA_HUY')),
        NgayTao DATETIME2(0) NOT NULL DEFAULT SYSUTCDATETIME(),
        CapNhatLuc DATETIME2(0) NOT NULL DEFAULT SYSUTCDATETIME(),
        XuLyBoi NVARCHAR(60) NULL
    );
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE name='IX_LichHen_Slot' AND object_id=OBJECT_ID('dbo.LichHenDatCho'))
    EXEC(N'CREATE INDEX IX_LichHen_Slot ON dbo.LichHenDatCho(LoaiVe,NgayHen,KhungGio,TrangThai)');
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE name='IX_LichHen_TaiKhoan' AND object_id=OBJECT_ID('dbo.LichHenDatCho'))
    EXEC(N'CREATE INDEX IX_LichHen_TaiKhoan ON dbo.LichHenDatCho(TaiKhoanID,NgayHen DESC)');
