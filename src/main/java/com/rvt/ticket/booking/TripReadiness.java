package com.rvt.ticket.booking;

/** Shared sale guard for search, date suggestions, booking and admin opening. */
public final class TripReadiness {
    private TripReadiness() {}
    public static String configured(String c) {
        if(!c.matches("[a-zA-Z][a-zA-Z0-9_]*"))throw new IllegalArgumentException("Invalid SQL alias");
        return "("+c+".PhuongTienID IS NOT NULL AND EXISTS(SELECT 1 FROM dbo.PhuongTien p WHERE p.PhuongTienID="+c+".PhuongTienID AND p.HoatDong=1 AND p.LoaiVe="+c+".LoaiVe)"
            +" AND "+c+".LoaiVe IN('XE_KHACH','TAU_THUYEN') AND "+c+".TongSoCho>0 AND "+c+".TongSoCho=(SELECT COUNT(*) FROM dbo.ChoNgoi q WHERE q.ChuyenDiID="+c+".ChuyenDiID)"
            +" AND "+c+".TongSoCho=(SELECT COUNT(*) FROM dbo.ChoPhuongTien p WHERE p.PhuongTienID="+c+".PhuongTienID)"
            +" AND "+c+".DuKienDen>"+c+".KhoiHanh AND "+c+".GiaCoBan>=1000"
            +" AND LEN(LTRIM(RTRIM("+c+".NoiDi)))>0 AND LEN(LTRIM(RTRIM("+c+".NoiDen)))>0 AND LEN(LTRIM(RTRIM("+c+".DonViVanChuyen)))>0"
            +" AND LTRIM(RTRIM("+c+".NoiDi))<>LTRIM(RTRIM("+c+".NoiDen))"
            +" AND NOT EXISTS(SELECT 1 FROM dbo.ChoNgoi q LEFT JOIN dbo.SoDoCho m ON m.ChuyenDiID=q.ChuyenDiID AND m.MaCho=q.MaCho"
            +" WHERE q.ChuyenDiID="+c+".ChuyenDiID AND (m.MaCho IS NULL OR m.Hang>30 OR q.LoaiVe<>"+c+".LoaiVe"
            +" OR NOT EXISTS(SELECT 1 FROM dbo.ChoPhuongTien p WHERE p.PhuongTienID="+c+".PhuongTienID AND p.MaCho=q.MaCho AND p.LoaiCho=q.LoaiCho AND p.Tang=m.Tang AND p.Hang=m.Hang AND p.Cot=m.Cot)"
            +" OR ("+c+".LoaiVe='XE_KHACH' AND q.LoaiCho NOT IN('GHE_NGOI','GIUONG_NAM'))"
            +" OR ("+c+".LoaiVe='TAU_THUYEN' AND q.LoaiCho NOT IN('PHO_THONG','VIP')))))";
    }
}
