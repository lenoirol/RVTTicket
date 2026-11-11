/* Tìm chuyến không chọn loại phương tiện, số liệu và bảng giờ trang chủ lọc theo TrangThai + KhoiHanh. */
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_ChuyenDi_MoBan_KhoiHanh' AND object_id = OBJECT_ID(N'dbo.ChuyenDi'))
    CREATE INDEX IX_ChuyenDi_MoBan_KhoiHanh ON dbo.ChuyenDi(TrangThai, KhoiHanh) INCLUDE (LoaiVe, NoiDi, NoiDen, GiaCoBan);
