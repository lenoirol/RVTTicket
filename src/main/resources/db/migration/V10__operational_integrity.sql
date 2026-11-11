-- Additive and repeatable. Existing fares, receipts, balances and credentials are not reset.
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF EXISTS(SELECT 1 FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID('dbo.VeTauThuyen') AND name='CK_VeTauThuyen_GiayTo')
    ALTER TABLE dbo.VeTauThuyen DROP CONSTRAINT CK_VeTauThuyen_GiayTo;
IF EXISTS(SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID('dbo.VeTauThuyen') AND name='SoGiayTo' AND system_type_id<>231)
    ALTER TABLE dbo.VeTauThuyen ALTER COLUMN SoGiayTo NVARCHAR(30) NOT NULL;
ALTER TABLE dbo.VeTauThuyen WITH CHECK ADD CONSTRAINT CK_VeTauThuyen_GiayTo CHECK(
    LEN(SoGiayTo) BETWEEN 1 AND 30 AND SoGiayTo NOT LIKE N'% %'
    AND CHARINDEX(NCHAR(9),SoGiayTo)=0 AND CHARINDEX(NCHAR(10),SoGiayTo)=0 AND CHARINDEX(NCHAR(13),SoGiayTo)=0);
IF OBJECT_ID(N'dbo.TR_VeTauThuyen_Document', N'TR') IS NULL
 EXEC(N'CREATE TRIGGER dbo.TR_VeTauThuyen_Document ON dbo.VeTauThuyen AFTER INSERT,UPDATE AS BEGIN SET NOCOUNT ON; END');
EXEC(N'ALTER TRIGGER dbo.TR_VeTauThuyen_Document ON dbo.VeTauThuyen AFTER INSERT,UPDATE AS
BEGIN
 SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM inserted t JOIN dbo.DangKyVe v ON v.DangKyID=t.DangKyID
  WHERE (v.LoaiGiayTo=''CCCD'' AND (DATALENGTH(t.SoGiayTo)<>24 OR t.SoGiayTo COLLATE Latin1_General_100_BIN2 LIKE N''%[^0-9]%''))
     OR (v.LoaiGiayTo=''HO_CHIEU'' AND (LEN(t.SoGiayTo)<6 OR t.SoGiayTo COLLATE Latin1_General_100_BIN2 LIKE N''%[^A-Za-z0-9]%'')))
  THROW 51010, ''Document does not match declared document type'', 1;
END');
IF OBJECT_ID(N'dbo.TR_DangKyVe_DocumentType', N'TR') IS NULL
 EXEC(N'CREATE TRIGGER dbo.TR_DangKyVe_DocumentType ON dbo.DangKyVe AFTER INSERT,UPDATE AS BEGIN SET NOCOUNT ON; END');
EXEC(N'ALTER TRIGGER dbo.TR_DangKyVe_DocumentType ON dbo.DangKyVe AFTER UPDATE AS
BEGIN
 SET NOCOUNT ON;
 IF NOT UPDATE(LoaiGiayTo) RETURN;
 IF EXISTS(SELECT 1 FROM inserted v JOIN deleted old ON old.DangKyID=v.DangKyID
  JOIN dbo.VeTauThuyen t ON t.DangKyID=v.DangKyID WHERE ISNULL(v.LoaiGiayTo,'''')<>ISNULL(old.LoaiGiayTo,'''') AND
   ((v.LoaiGiayTo=''CCCD'' AND (DATALENGTH(t.SoGiayTo)<>24 OR t.SoGiayTo COLLATE Latin1_General_100_BIN2 LIKE N''%[^0-9]%''))
    OR (v.LoaiGiayTo=''HO_CHIEU'' AND (LEN(t.SoGiayTo)<6 OR t.SoGiayTo COLLATE Latin1_General_100_BIN2 LIKE N''%[^A-Za-z0-9]%''))))
  THROW 51010, ''Document does not match declared document type'', 1;
END');
IF COL_LENGTH('dbo.TaiKhoanQuanTri','PhienBanDangNhap') IS NULL
 ALTER TABLE dbo.TaiKhoanQuanTri ADD PhienBanDangNhap INT NOT NULL DEFAULT 0;
IF COL_LENGTH('dbo.GiaoDichVNPay','SoLanTruyVan') IS NULL
BEGIN
 ALTER TABLE dbo.GiaoDichVNPay ADD SoLanTruyVan INT NOT NULL DEFAULT 0,
  TruyVanGanNhatLuc DATETIME2(0) NULL, TruyVanTiepTheoLuc DATETIME2(0) NULL,
  CanDoiSoat BIT NOT NULL DEFAULT 0, LoiTruyVan NVARCHAR(150) NULL,
  NguonXacNhan VARCHAR(12) NULL, KetQuaNhanLuc DATETIME2(0) NULL,
  XuLyDoiSoat VARCHAR(20) NULL, XuLyLuc DATETIME2(0) NULL,
  NguoiXuLy NVARCHAR(80) NULL, GhiChuXuLy NVARCHAR(500) NULL;
END;
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('dbo.GiaoDichVNPay') AND name='IX_VNPay_Reconcile')
 EXEC(N'CREATE INDEX IX_VNPay_Reconcile ON dbo.GiaoDichVNPay(TmnCode,TrangThai,CanDoiSoat,TruyVanTiepTheoLuc,NgayTao) INCLUDE(SoLanTruyVan)');
EXEC(N'UPDATE dbo.GiaoDichVNPay SET NguonXacNhan=''HISTORICAL'',KetQuaNhanLuc=NhanIPNLuc WHERE TrangThai<>''PENDING'' AND NguonXacNhan IS NULL');
IF OBJECT_ID('dbo.HoanVNPayChoNhan','U') IS NULL
 CREATE TABLE dbo.HoanVNPayChoNhan(
  MaThamChieu VARCHAR(40) NOT NULL PRIMARY KEY REFERENCES dbo.GiaoDichVNPay(MaThamChieu),
  DonDatVeID INT NOT NULL REFERENCES dbo.DonDatVe(DonDatVeID),
  SoTien DECIMAL(18,2) NOT NULL CHECK(SoTien>0), DaNhan BIT NOT NULL DEFAULT 0,
  NgayTao DATETIME2(0) NOT NULL DEFAULT SYSUTCDATETIME());
-- Change local-clock defaults to UTC without guessing or shifting historical dates.
DECLARE @utcTable SYSNAME,@utcColumn SYSNAME,@utcConstraint SYSNAME,@utcSql NVARCHAR(MAX);
DECLARE utc_defaults CURSOR LOCAL FAST_FORWARD FOR
 SELECT OBJECT_NAME(d.parent_object_id),c.name,d.name FROM sys.default_constraints d
 JOIN sys.columns c ON c.object_id=d.parent_object_id AND c.column_id=d.parent_column_id
 WHERE OBJECT_SCHEMA_NAME(d.parent_object_id)='dbo'
 AND LOWER(d.definition) LIKE '%sysdatetime()%' AND LOWER(d.definition) NOT LIKE '%sysutcdatetime()%';
OPEN utc_defaults;
FETCH NEXT FROM utc_defaults INTO @utcTable,@utcColumn,@utcConstraint;
WHILE @@FETCH_STATUS=0
BEGIN
 SET @utcSql=N'ALTER TABLE dbo.'+QUOTENAME(@utcTable)+N' DROP CONSTRAINT '+QUOTENAME(@utcConstraint)
  +N'; ALTER TABLE dbo.'+QUOTENAME(@utcTable)+N' ADD CONSTRAINT '+QUOTENAME(@utcConstraint)+N' DEFAULT SYSUTCDATETIME() FOR '+QUOTENAME(@utcColumn);
 EXEC sp_executesql @utcSql;
 FETCH NEXT FROM utc_defaults INTO @utcTable,@utcColumn,@utcConstraint;
END;
CLOSE utc_defaults; DEALLOCATE utc_defaults;
COMMIT TRANSACTION;
