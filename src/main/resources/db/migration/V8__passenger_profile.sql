-- Optional passenger identity, separate from immutable booking snapshots.
IF COL_LENGTH('dbo.TaiKhoanKhach','NgaySinh') IS NULL
    ALTER TABLE dbo.TaiKhoanKhach ADD NgaySinh DATE NULL;
IF COL_LENGTH('dbo.TaiKhoanKhach','LoaiGiayTo') IS NULL
    ALTER TABLE dbo.TaiKhoanKhach ADD LoaiGiayTo VARCHAR(20) NULL;
IF COL_LENGTH('dbo.TaiKhoanKhach','SoGiayTo') IS NULL
    ALTER TABLE dbo.TaiKhoanKhach ADD SoGiayTo NVARCHAR(30) NULL;
IF COL_LENGTH('dbo.TaiKhoanKhach','QuocTich') IS NULL
    ALTER TABLE dbo.TaiKhoanKhach ADD QuocTich NVARCHAR(50) NULL;
