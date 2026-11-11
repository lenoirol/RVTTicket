#!/usr/bin/env python3
"""
Sinh đội tàu cao tốc và lịch chạy mẫu cho 6 tuyến biển trong SeaRouteCatalog, mở bán sẵn.

Cách dùng (cần: pip install pymssql):
    MSSQL_PWD='mật-khẩu' python3 database/seed_sea_trips.py --host 192.168.1.25 --days 12
    tuỳ chọn: --user sa --db RVTTicket --start 2026-11-11 --dry-run

Giống seed_bus_trips.py:
  * mỗi chuyến gắn một tàu có sơ đồ chỗ (tầng 1 phổ thông, tầng 2 VIP); ChoNgoi/SoDoCho chép từ ChoPhuongTien;
  * mỗi tàu chạy khứ hồi trong ngày nên bến kết thúc chuyến trước = bến đi chuyến sau;
  * giá người lớn / trẻ em / cao tuổi và phụ thu VIP lấy theo SeaRouteCatalog (giá mẫu, không phải giá hãng);
  * giờ trong DB là UTC (giờ Việt Nam - 7); chạy lại an toàn: tàu/chuyến đã có thì bỏ qua.
"""
import argparse, os
from datetime import datetime, timedelta, date

import pymssql

# mã, bến A, bến B, phút, người lớn, trẻ em, cao tuổi, giá VIP, cảng A, cảng B
ROUTES = {
    "RG-PQ": ("Rạch Giá", "Phú Quốc", 150, 315000, 225000, 265000, 500000, "Cảng thủy nội địa Rạch Giá", "Cảng Bãi Vòng, Phú Quốc"),
    "HT-PQ": ("Hà Tiên", "Phú Quốc", 80, 216000, 148000, 182000, 330000, "Cảng Hà Tiên", "Cảng Bãi Vòng, Phú Quốc"),
    "VT-CD": ("Vũng Tàu", "Côn Đảo", 240, 790000, 630000, 630000, 1000000, "Cảng Cầu Đá, 09 Hạ Long, Vũng Tàu", "Cảng Bến Đầm, Côn Đảo"),
    "TD-CD": ("Cảng Trần Đề", "Côn Đảo", 135, 390000, 312000, 312000, 590000, "Cảng cá Trần Đề", "Cảng Bến Đầm, Côn Đảo"),
    "PT-PQY": ("Phan Thiết", "Phú Quý", 135, 370000, 275000, 295000, 450000, "Cảng vận tải Phan Thiết", "Cảng Phú Quý"),
    "SK-LS": ("Cảng Sa Kỳ", "Lý Sơn", 60, 182000, 128000, 147000, 238000, "Cảng Sa Kỳ", "Cảng Bến Đình, Lý Sơn"),
}
# Lịch cố định mỗi tàu: tuyến, bến xuất phát (0 = A, 1 = B), giờ các lượt (giờ VN), hãng, cỡ tàu
BOATS = [
    ("RG-PQ", 0, ["07:30", "13:00"], "Biển Ngọc Express", "big"),
    ("RG-PQ", 0, ["08:30", "14:00"], "Hải Âu Cao Tốc", "big"),
    ("RG-PQ", 1, ["07:00", "12:30"], "Biển Ngọc Express", "big"),
    ("RG-PQ", 1, ["09:30", "14:30"], "Sóng Xanh Ferry", "big"),
    ("HT-PQ", 0, ["07:00", "09:30", "12:00", "14:30"], "Hải Âu Cao Tốc", "big"),
    ("HT-PQ", 1, ["07:30", "10:00", "12:30", "15:00"], "Sóng Xanh Ferry", "big"),
    ("HT-PQ", 0, ["08:30", "13:30"], "Biển Ngọc Express", "small"),
    ("VT-CD", 0, ["08:00", "13:30"], "Côn Sơn Express", "big"),
    ("TD-CD", 0, ["08:00", "13:00"], "Côn Sơn Express", "big"),
    ("TD-CD", 1, ["08:30", "13:30"], "Hải Âu Cao Tốc", "big"),
    ("PT-PQY", 0, ["07:00", "13:00"], "Sóng Xanh Ferry", "small"),
    ("SK-LS", 0, ["07:00", "09:00", "11:00", "14:00"], "Lý Sơn Xanh", "small"),
    ("SK-LS", 1, ["07:30", "09:30", "12:30", "15:00"], "Hải Âu Cao Tốc", "small"),
    ("SK-LS", 0, ["08:00", "13:30"], "Lý Sơn Xanh", "small"),
]
# cỡ tàu: (tên, hàng tầng 1, hàng tầng 2 VIP)
SIZES = {"big": ("Tàu cao tốc", 16, 8), "small": ("Tàu cao tốc", 12, 4)}
AMENITIES = "Điều hòa, Ghế ngả, Áo phao, Nhà vệ sinh, Màn hình thông tin"
NOTE = ("Có mặt tại cảng trước giờ tàu chạy 45 phút để làm thủ tục. Mỗi hành khách mang giấy tờ khớp thông tin "
        "đã đặt (CCCD, hộ chiếu, trẻ em dùng giấy khai sinh). Lịch có thể đổi khi thời tiết xấu.")


def seats(size, vip_extra):
    _, r1, r2 = SIZES[size]
    out = []
    n = 0
    for r in range(1, r1 + 1):
        for c in [1, 2, 4, 5, 6]:
            n += 1; out.append(("A%02d" % n, "PHO_THONG", 0, 1, r, c))
    n = 0
    for r in range(1, r2 + 1):
        for c in [1, 2, 5, 6]:
            n += 1; out.append(("B%02d" % n, "VIP", vip_extra, 2, r, c))
    return out


def lit(s):
    return "N'" + str(s).replace("'", "''") + "'"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--host", default="localhost"); ap.add_argument("--port", type=int, default=1433)
    ap.add_argument("--user", default="sa"); ap.add_argument("--db", default="RVTTicket")
    ap.add_argument("--start"); ap.add_argument("--days", type=int, default=12); ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()
    conn = pymssql.connect(server=args.host, port=args.port, user=args.user, password=os.environ["MSSQL_PWD"],
                           database=args.db, autocommit=True, login_timeout=15, timeout=900)
    cur = conn.cursor()
    cur.execute("SELECT SYSUTCDATETIME()"); now_utc = cur.fetchone()[0]
    start = date.fromisoformat(args.start) if args.start else (now_utc + timedelta(hours=7)).date()
    earliest = now_utc + timedelta(minutes=60)
    cur.execute("SELECT TenDiem, MaDiem FROM dbo.DiaDiem"); place = dict(cur.fetchall())

    fleet = []
    for i, (route, side, times, operator, size) in enumerate(BOATS, 1):
        a, b, minutes, adult, child, senior, vip, *_ = ROUTES[route]
        for p in (a, b):
            if p not in place: raise SystemExit("Thiếu địa điểm: " + p)
        code = "RVT-S%02d" % i
        reg = "VN-%s-%04d" % (route.split("-")[1], 2100 + i * 7)
        name = "%s %d chỗ · %s" % (SIZES[size][0], len(seats(size, 0)), reg)
        fleet.append(dict(code=code, route=route, side=side, times=times, operator=operator, size=size, reg=reg, name=name, vip_extra=vip - adult))

    cur.execute("SELECT MaPhuongTien, PhuongTienID FROM dbo.PhuongTien WHERE MaPhuongTien LIKE 'RVT-S[0-9]%'")
    vid = dict(cur.fetchall())
    new = [f for f in fleet if f["code"] not in vid]
    if not args.dry_run and new:
        cur.execute("INSERT dbo.PhuongTien(MaPhuongTien,TenPhuongTien,SoDangKy,DonViVanChuyen,LoaiVe,HoatDong) VALUES " +
                    ",".join("(%s,%s,%s,%s,'TAU_THUYEN',1)" % (lit(f["code"]), lit(f["name"]), lit(f["reg"]), lit(f["operator"])) for f in new))
        cur.execute("SELECT MaPhuongTien, PhuongTienID FROM dbo.PhuongTien WHERE MaPhuongTien LIKE 'RVT-S[0-9]%'")
        vid = dict(cur.fetchall())
        rows = []
        for f in new:
            for code, kind, extra, tang, hang, cot in seats(f["size"], f["vip_extra"]):
                rows.append("(%d,'%s','%s',%d,%d,%d,%d)" % (vid[f["code"]], code, kind, extra, tang, hang, cot))
        for k in range(0, len(rows), 900):
            cur.execute("INSERT dbo.ChoPhuongTien(PhuongTienID,MaCho,LoaiCho,PhuThu,Tang,Hang,Cot) VALUES " + ",".join(rows[k:k + 900]))
    print("Tàu mới: %d / %d" % (len(new), len(fleet)))

    existing = set()
    if not args.dry_run:
        cur.execute("SELECT MaChuyen FROM dbo.ChuyenDi WHERE MaChuyen LIKE 'RVT-S[0-9]%'"); existing = {r[0] for r in cur.fetchall()}
    trips, per_day = [], {}
    for d in range(args.days):
        day = start + timedelta(days=d)
        for f in fleet:
            a, b, minutes, adult, child, senior, vip, port_a, port_b = ROUTES[f["route"]]
            side = f["side"]
            for leg, hhmm in enumerate(f["times"], 1):
                frm, to, pf, pt = (a, b, port_a, port_b) if side == 0 else (b, a, port_b, port_a)
                side = 1 - side
                h, m = map(int, hhmm.split(":"))
                dep = datetime(day.year, day.month, day.day, h, m) - timedelta(hours=7)
                arr = dep + timedelta(minutes=minutes)
                code = "%s-%s-%d" % (f["code"], (dep + timedelta(hours=7)).strftime("%y%m%d"), leg)
                if dep <= earliest or code in existing: continue
                trips.append((f, code, frm, to, pf, pt, dep, arr, adult, child, senior))
                per_day[day] = per_day.get(day, 0) + 1
    for k in sorted(per_day): print("  %s: %d chuyến tàu" % (k.strftime("%a %d/%m"), per_day[k]))
    print("Tổng chuyến tàu sẽ thêm: %d" % len(trips))
    if args.dry_run or not trips: return

    cur.execute("SELECT ISNULL(MAX(ChuyenDiID),0) FROM dbo.ChuyenDi"); before = cur.fetchone()[0]
    for k in range(0, len(trips), 400):
        vals = []
        for f, code, frm, to, pf, pt, dep, arr, adult, child, senior in trips[k:k + 400]:
            vals.append("(%s,'TAU_THUYEN',%s,%s,%s,'%s','%s',%d,%d,%d,%d,'DONG_BAN',%d,%s,%s,'%s','%s')" % (
                lit(code), lit(f["operator"]), lit(frm), lit(to), dep.strftime("%Y-%m-%d %H:%M:%S"), arr.strftime("%Y-%m-%d %H:%M:%S"),
                len(seats(f["size"], 0)), adult, child, senior, vid[f["code"]], lit(f["name"]), lit(f["reg"]), place[frm], place[to]))
        cur.execute("INSERT dbo.ChuyenDi(MaChuyen,LoaiVe,DonViVanChuyen,NoiDi,NoiDen,KhoiHanh,DuKienDen,TongSoCho,GiaCoBan,GiaTreEm,GiaCaoTuoi,TrangThai,PhuongTienID,TenPhuongTienSnapshot,SoDangKySnapshot,MaDiemDi,MaDiemDen) VALUES " + ",".join(vals))
    cur.execute("SELECT MaChuyen, ChuyenDiID FROM dbo.ChuyenDi WHERE ChuyenDiID>%d" % before); ids = dict(cur.fetchall())
    info = ["(%d,%s,%s,%s,%s)" % (ids[t[1]], lit(t[4]), lit(t[5]), lit(AMENITIES), lit(NOTE)) for t in trips]
    for k in range(0, len(info), 400):
        cur.execute("INSERT dbo.ThongTinChuyen(ChuyenDiID,DiemDon,DiemTra,TienIch,MoTa) VALUES " + ",".join(info[k:k + 400]))
    cur.execute("INSERT dbo.ChoNgoi(ChuyenDiID,MaCho,LoaiVe,LoaiCho,PhuThu) SELECT c.ChuyenDiID,p.MaCho,c.LoaiVe,p.LoaiCho,p.PhuThu FROM dbo.ChuyenDi c JOIN dbo.ChoPhuongTien p ON p.PhuongTienID=c.PhuongTienID WHERE c.ChuyenDiID>%d" % before)
    cur.execute("INSERT dbo.SoDoCho(ChuyenDiID,MaCho,Tang,Hang,Cot) SELECT c.ChuyenDiID,p.MaCho,p.Tang,p.Hang,p.Cot FROM dbo.ChuyenDi c JOIN dbo.ChoPhuongTien p ON p.PhuongTienID=c.PhuongTienID WHERE c.ChuyenDiID>%d" % before)
    cur.execute("UPDATE dbo.ChuyenDi SET TrangThai='MO_BAN' WHERE ChuyenDiID>%d AND TrangThai='DONG_BAN'" % before)
    cur.execute("INSERT dbo.NhatKyThaoTac(NguoiThucHien,HanhDong,DoiTuong,NoiDung) VALUES('SEED','TAO_CHUYEN',%s,%s)",
                ("seed_sea_trips", "Tạo %d chuyến tàu mẫu từ %s, %d ngày." % (len(trips), start, args.days)))
    print("Đã thêm %d chuyến tàu, mở bán." % len(trips))


if __name__ == "__main__":
    main()
