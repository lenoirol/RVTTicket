#!/usr/bin/env python3
"""
Sinh dữ liệu xe khách mẫu cho RVT Ticket: đội xe + chuyến chạy theo lịch cố định mỗi ngày
(khoảng 600-700 chuyến/ngày toàn mạng), mở bán sẵn.

Cách dùng (cần: pip install pymssql):
    MSSQL_PWD='mật-khẩu' python3 database/seed_bus_trips.py --host 192.168.1.25 --days 12
    tuỳ chọn: --user sa --db RVTTicket --start 2026-11-11 --days 12 --dry-run

Quy tắc đã tuân theo (giống TicketService.createTrip / TripReadiness):
  * mỗi chuyến gắn một phương tiện đã có sơ đồ chỗ; ChoNgoi/SoDoCho sao chép từ ChoPhuongTien;
  * một xe chạy luân phiên A->B, B->A nên điểm kết thúc chuyến trước = điểm đi chuyến sau;
  * giữa hai chuyến của một xe cách nhau >= 45 phút (hệ thống yêu cầu >= 30 phút);
  * giờ trong DB là UTC (giờ Việt Nam - 7);
  * chạy lại nhiều lần an toàn: xe/chuyến đã có thì bỏ qua, chuyến trùng lịch xe thì bỏ qua.
"""
import argparse, os, random, sys
from datetime import datetime, timedelta, date

import pymssql

# (điểm A, điểm B, km đường bộ ước lượng, trọng số lưu lượng)
ROUTES = [
    # Hà Nội đi các tỉnh phía Bắc
    ("Hà Nội", "Hải Phòng", 120, 10), ("Hà Nội", "Hạ Long", 160, 8), ("Hà Nội", "Ninh Bình", 95, 6),
    ("Hà Nội", "Thanh Hóa", 150, 6), ("Hà Nội", "Vinh", 295, 6), ("Hà Nội", "Hà Tĩnh", 340, 2),
    ("Hà Nội", "Đồng Hới", 490, 2), ("Hà Nội", "Huế", 660, 2), ("Hà Nội", "Đà Nẵng", 770, 3),
    ("Hà Nội", "Lào Cai", 330, 3), ("Hà Nội", "Sa Pa", 320, 5), ("Hà Nội", "Lạng Sơn", 155, 3),
    ("Hà Nội", "Thái Nguyên", 80, 4), ("Hà Nội", "Việt Trì", 80, 3), ("Hà Nội", "Sơn La", 320, 2),
    ("Hà Nội", "Mộc Châu", 180, 3), ("Hà Nội", "Điện Biên Phủ", 470, 2), ("Hà Nội", "Cao Bằng", 280, 2),
    ("Hà Nội", "Hà Giang", 320, 2), ("Hà Nội", "Bắc Giang", 55, 3), ("Hà Nội", "Nam Định", 90, 4),
    ("Hà Nội", "Thái Bình", 110, 3), ("Hà Nội", "Hòa Bình", 75, 3), ("Hà Nội", "Móng Cái", 350, 1),
    ("Hà Nội", "Cẩm Phả", 190, 2), ("Hà Nội", "Tuyên Quang", 165, 1), ("Hà Nội", "Phủ Lý", 55, 2),
    ("Hà Nội", "Hưng Yên", 65, 2), ("Hải Phòng", "Hạ Long", 80, 2), ("Hải Phòng", "Thanh Hóa", 190, 1),
    # Miền Trung - Tây Nguyên
    ("Vinh", "Đà Nẵng", 470, 1), ("Huế", "Đà Nẵng", 100, 6), ("Đà Nẵng", "Quảng Ngãi", 130, 3),
    ("Đà Nẵng", "Quy Nhơn", 300, 2), ("Đà Nẵng", "Tam Kỳ", 70, 2), ("Đà Nẵng", "Nha Trang", 530, 2),
    ("Đà Nẵng", "Pleiku", 330, 1), ("Đà Nẵng", "Buôn Ma Thuột", 500, 1), ("Quy Nhơn", "Pleiku", 180, 2),
    ("Quy Nhơn", "Nha Trang", 230, 2), ("Quy Nhơn", "Tuy Hòa", 120, 2), ("Tuy Hòa", "Nha Trang", 130, 2),
    ("Đông Hà", "Huế", 75, 2), ("Đồng Hới", "Huế", 160, 2), ("Vinh", "Hà Tĩnh", 50, 2),
    ("Thanh Hóa", "Vinh", 140, 2), ("Quảng Ngãi", "Quy Nhơn", 170, 2), ("Kon Tum", "Pleiku", 50, 1),
    ("Kon Tum", "Đà Nẵng", 280, 1),
    # TP.HCM đi miền Đông, Tây Nam Bộ, Tây Nguyên, duyên hải
    ("TP. Hồ Chí Minh", "Vũng Tàu", 100, 12), ("TP. Hồ Chí Minh", "Cần Thơ", 170, 12),
    ("TP. Hồ Chí Minh", "Mỹ Tho", 70, 8), ("TP. Hồ Chí Minh", "Tây Ninh", 100, 6),
    ("TP. Hồ Chí Minh", "Phan Thiết", 200, 8), ("TP. Hồ Chí Minh", "Đà Lạt", 300, 14),
    ("TP. Hồ Chí Minh", "Nha Trang", 430, 10), ("TP. Hồ Chí Minh", "Buôn Ma Thuột", 350, 6),
    ("TP. Hồ Chí Minh", "Bảo Lộc", 190, 5), ("TP. Hồ Chí Minh", "Long Xuyên", 190, 5),
    ("TP. Hồ Chí Minh", "Châu Đốc", 245, 4), ("TP. Hồ Chí Minh", "Rạch Giá", 250, 4),
    ("TP. Hồ Chí Minh", "Cà Mau", 300, 4), ("TP. Hồ Chí Minh", "Vĩnh Long", 130, 5),
    ("TP. Hồ Chí Minh", "Bến Tre", 85, 5), ("TP. Hồ Chí Minh", "Cao Lãnh", 170, 4),
    ("TP. Hồ Chí Minh", "Sóc Trăng", 230, 3), ("TP. Hồ Chí Minh", "Bạc Liêu", 280, 2),
    ("TP. Hồ Chí Minh", "Trà Vinh", 130, 3), ("TP. Hồ Chí Minh", "Quy Nhơn", 650, 2),
    ("TP. Hồ Chí Minh", "Pleiku", 520, 2), ("TP. Hồ Chí Minh", "Phan Rang–Tháp Chàm", 340, 3),
    ("TP. Hồ Chí Minh", "Đồng Xoài", 130, 2), ("TP. Hồ Chí Minh", "Gia Nghĩa", 230, 2),
    ("TP. Hồ Chí Minh", "Tuy Hòa", 560, 1),
    ("Cần Thơ", "Cà Mau", 180, 3), ("Cần Thơ", "Châu Đốc", 120, 2), ("Cần Thơ", "Rạch Giá", 130, 2),
    ("Cần Thơ", "Sóc Trăng", 60, 2), ("Cần Thơ", "Long Xuyên", 60, 2), ("Cần Thơ", "Bạc Liêu", 110, 1),
    ("Đà Lạt", "Nha Trang", 140, 5), ("Đà Lạt", "Phan Thiết", 160, 2), ("Đà Lạt", "Buôn Ma Thuột", 200, 1),
    ("Nha Trang", "Buôn Ma Thuột", 190, 3), ("Nha Trang", "Phan Rang–Tháp Chàm", 105, 2),
]

STATION = {
    "Hải Phòng": "Bến xe Niệm Nghĩa", "Hạ Long": "Bến xe Bãi Cháy", "Cẩm Phả": "Bến xe Cẩm Phả",
    "Thanh Hóa": "Bến xe phía Bắc Thanh Hóa", "Vinh": "Bến xe phía Bắc Vinh", "Huế": "Bến xe phía Nam Huế",
    "Đà Nẵng": "Bến xe Trung tâm Đà Nẵng", "Nha Trang": "Bến xe phía Nam Nha Trang",
    "Đà Lạt": "Bến xe Liên tỉnh Đà Lạt", "Cần Thơ": "Bến xe Trung tâm Cần Thơ", "Sa Pa": "Bến xe Sa Pa",
    "Vũng Tàu": "Bến xe Vũng Tàu", "Quy Nhơn": "Bến xe Quy Nhơn", "Buôn Ma Thuột": "Bến xe Buôn Ma Thuột",
}
HN_SOUTH = {"Ninh Bình", "Thanh Hóa", "Vinh", "Hà Tĩnh", "Đồng Hới", "Huế", "Đà Nẵng", "Phủ Lý", "Nam Định", "Thái Bình", "Hưng Yên", "Hải Phòng", "Hạ Long", "Cẩm Phả", "Móng Cái"}
HCM_WEST = {"Cần Thơ", "Mỹ Tho", "Long Xuyên", "Châu Đốc", "Rạch Giá", "Cà Mau", "Vĩnh Long", "Bến Tre", "Cao Lãnh", "Sóc Trăng", "Bạc Liêu", "Trà Vinh", "Tây Ninh"}

OPERATORS = ["RVT Express", "Sao Việt Bus", "Biển Xanh Limousine", "Đại Dương Xanh", "Hải Vân Express",
             "Mê Kông Travel", "Bình Minh Bus", "Thăng Long Express", "Cửu Long Bus", "Sông Hương Bus",
             "Tây Nguyên Xanh", "An Phát Bus", "Việt Tiến Express", "Hoa Sen Limousine", "Trường Sơn Bus"]
PLATES = ["29B", "30E", "30F", "29H", "51B", "50H", "51F", "43B", "79B", "49B", "15B", "14B", "35B", "36B", "37B",
          "38B", "73B", "75B", "92B", "65B", "67B", "68B", "63B", "60B", "61B", "72B", "86B", "84B", "71B", "94B", "95B",
          "81B", "82B", "47B", "48B", "34B", "99B", "12B", "20B", "24B", "25B", "26B", "28B"]

# loại xe: (tên hiển thị, loai_cho, số tầng, hàng, cột, hệ số giá)
LAYOUTS = {
    "seat28": ("Xe ghế ngồi 28 chỗ", "GHE_NGOI", 1, 7, [1, 2, 4, 5], 1.10),
    "seat44": ("Xe ghế ngồi 44 chỗ", "GHE_NGOI", 1, 11, [1, 2, 4, 5], 1.00),
    "limo16": ("Xe limousine 16 chỗ", "GHE_NGOI", 1, 8, [1, 3], 1.70),
    "sleep36": ("Xe giường nằm 36 chỗ", "GIUONG_NAM", 2, 6, [1, 3, 5], 1.20),
    "sleep42": ("Xe giường nằm 42 chỗ", "GIUONG_NAM", 2, 7, [1, 3, 5], 1.20),
}
AMENITIES = {
    "GHE_NGOI": "Điều hòa, Nước suối, Wifi, Sạc USB",
    "GIUONG_NAM": "Giường nằm, Điều hòa, Chăn gối, Nước suối, Wifi, Sạc USB",
}
NOTE = "Có mặt tại bến xe trước giờ khởi hành 20 phút, mang theo giấy tờ tùy thân và mã vé. Xe có thể dừng nghỉ dọc đường."


def layout_seats(key):
    _, kind, floors, rows, cols, _ = LAYOUTS[key]
    out = []
    for f in range(1, floors + 1):
        n = 0
        for r in range(1, rows + 1):
            for c in cols:
                n += 1
                out.append(((("A" if f == 1 else "B") + "%02d" % n), kind, 0, f, r, c))
    return out


def minutes_for(km):
    if km <= 150:
        h = km / 42 + 0.25
    elif km <= 400:
        h = km / 50 + 0.4
    else:
        h = km / 55 + 0.75
    return int(round(h * 60 / 5) * 5)


def lit(s):
    return "N'" + str(s).replace("'", "''") + "'"


def station(city, other, depart):
    if city == "Hà Nội":
        if other in HN_SOUTH:
            return "Bến xe Giáp Bát" if hash_stable(other) % 2 else "Bến xe Nước Ngầm"
        return "Bến xe Mỹ Đình"
    if city == "TP. Hồ Chí Minh":
        return "Bến xe Miền Tây" if other in HCM_WEST else "Bến xe Miền Đông"
    return STATION.get(city, "Bến xe " + city)


def hash_stable(s):
    return sum(ord(c) * (i + 1) for i, c in enumerate(s))


def build_fleet(rng, target_legs):
    """Chọn số xe cho từng tuyến sao cho tổng số chuyến/ngày xấp xỉ target_legs."""
    info = []
    for a, b, km, w in ROUTES:
        m = minutes_for(km)
        legs = 4 if m <= 210 else 2 if m <= 390 else 1
        info.append((a, b, km, w, m, legs))
    k = 0.2
    while True:
        total = sum(max(1, round(w * k)) * legs for a, b, km, w, m, legs in info)
        if total >= target_legs:
            break
        k += 0.02
    fleet = []
    for a, b, km, w, m, legs in info:
        for _ in range(max(1, round(w * k))):
            fleet.append(dict(a=a, b=b, km=km, m=m, legs=legs))
    rng.shuffle(fleet)
    plates = set()
    for i, v in enumerate(fleet, 1):
        m, legs = v["m"], v["legs"]
        turn = rng.choice([45, 60, 75])
        v["turn"] = turn
        if legs > 1:
            span = legs * m + (legs - 1) * turn
            latest = 23 * 60 + 40 - span
            early = 4 * 60 + 30
            if latest < early:
                legs = 2 if m * 2 + turn <= 19 * 60 else 1
                v["legs"] = legs
                span = legs * m + (legs - 1) * turn
                latest = 23 * 60 + 40 - span
        if v["legs"] > 1:
            hi = min(latest, 11 * 60 if v["legs"] == 4 else 14 * 60)
            lo = min(max(5 * 60, early), hi)
            v["start"] = rng.randrange(lo, hi + 1, 5)
        elif m > 9 * 60:
            v["start"] = rng.randrange(17 * 60, 21 * 60 + 31, 5)
        else:
            v["start"] = rng.randrange(5 * 60 + 30, 10 * 60 + 1, 5)
        h = v["m"] / 60
        if v["legs"] == 1 and h > 9:
            v["type"] = rng.choice(["sleep36", "sleep42"])
        elif v["legs"] == 1:
            v["type"] = rng.choice(["sleep36", "seat44", "sleep42", "seat44"])
        elif v["legs"] == 2:
            v["type"] = rng.choice(["seat44", "seat44", "seat28", "limo16", "sleep36"] if v["km"] > 200 else ["seat44", "seat28", "limo16"])
        else:
            v["type"] = rng.choice(["seat28", "seat28", "seat44", "limo16", "limo16"])
        while True:
            plate = "%s-%03d.%02d" % (rng.choice(PLATES), rng.randrange(100, 1000), rng.randrange(0, 100))
            if plate not in plates:
                plates.add(plate)
                break
        v["plate"] = plate
        v["code"] = "RVT-X%03d" % i
        v["operator"] = rng.choice(OPERATORS)
        v["side"] = rng.choice([0, 1])  # 0: đang ở A, 1: đang ở B
    return fleet


def price_for(km, key):
    rate = max(450.0, 950 - 0.3 * km)
    return int(round(km * rate * LAYOUTS[key][5] / 5000.0) * 5000)


def batches(rows, n=500):
    for i in range(0, len(rows), n):
        yield rows[i:i + n]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--host", default="localhost")
    ap.add_argument("--port", type=int, default=1433)
    ap.add_argument("--user", default="sa")
    ap.add_argument("--db", default="RVTTicket")
    ap.add_argument("--start", help="YYYY-MM-DD (giờ VN), mặc định hôm nay")
    ap.add_argument("--days", type=int, default=12)
    ap.add_argument("--target", type=int, default=680, help="số chuyến/ngày tối đa của đội xe")
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    conn = pymssql.connect(server=args.host, port=args.port, user=args.user, password=os.environ["MSSQL_PWD"],
                           database=args.db, autocommit=True, login_timeout=15, timeout=900)
    cur = conn.cursor()
    cur.execute("SELECT SYSUTCDATETIME()")
    now_utc = cur.fetchone()[0]
    now_vn = now_utc + timedelta(hours=7)
    start = date.fromisoformat(args.start) if args.start else now_vn.date()
    earliest_utc = now_utc + timedelta(minutes=60)

    rng = random.Random(20261111)
    fleet = build_fleet(rng, args.target)
    print("Đội xe: %d xe, tối đa %d chuyến/ngày" % (len(fleet), sum(v["legs"] for v in fleet)))

    cur.execute("SELECT TenDiem, MaDiem FROM dbo.DiaDiem")
    place_id = dict(cur.fetchall())
    for v in fleet:
        for city in (v["a"], v["b"]):
            if city not in place_id:
                sys.exit("Địa điểm không có trong DiaDiem: " + city)

    # ---- phương tiện ----
    cur.execute("SELECT MaPhuongTien, PhuongTienID FROM dbo.PhuongTien WHERE MaPhuongTien LIKE 'RVT-X%'")
    vid = dict(cur.fetchall())
    new_vehicles = [v for v in fleet if v["code"] not in vid]
    if not args.dry_run:
        for chunk in batches(new_vehicles):
            vals = ",".join("(%s,%s,%s,%s,'XE_KHACH',1)" % (lit(v["code"]), lit(LAYOUTS[v["type"]][0] + " · " + v["plate"]),
                                                          lit(v["plate"]), lit(v["operator"])) for v in chunk)
            cur.execute("INSERT dbo.PhuongTien(MaPhuongTien,TenPhuongTien,SoDangKy,DonViVanChuyen,LoaiVe,HoatDong) VALUES " + vals)
        cur.execute("SELECT MaPhuongTien, PhuongTienID FROM dbo.PhuongTien WHERE MaPhuongTien LIKE 'RVT-X%'")
        vid = dict(cur.fetchall())
        have = {r[0] for r in (cur.execute("SELECT DISTINCT PhuongTienID FROM dbo.ChoPhuongTien") or cur.fetchall())}
        seat_rows = []
        for v in new_vehicles:
            pid = vid[v["code"]]
            if pid in have:
                continue
            for code, kind, surcharge, f, r, c in layout_seats(v["type"]):
                seat_rows.append("(%d,'%s','%s',%d,%d,%d,%d)" % (pid, code, kind, surcharge, f, r, c))
        for chunk in batches(seat_rows, 900):
            cur.execute("INSERT dbo.ChoPhuongTien(PhuongTienID,MaCho,LoaiCho,PhuThu,Tang,Hang,Cot) VALUES " + ",".join(chunk))
    print("Phương tiện mới: %d" % len(new_vehicles))

    # ---- lịch đã có của các xe RVT-X (để không đè lịch) ----
    existing_codes, busy = set(), {}
    if not args.dry_run:
        cur.execute("SELECT MaChuyen, PhuongTienID, KhoiHanh, DuKienDen, NoiDen FROM dbo.ChuyenDi WHERE MaChuyen LIKE 'RVT-X%' AND TrangThai<>'DA_HUY'")
        for code, pid, k, d, dest in cur.fetchall():
            existing_codes.add(code)
            busy.setdefault(pid, []).append((k, d, dest))

    # ---- sinh chuyến ----
    trips = []
    per_day = {}
    offrng = random.Random(777)
    for day_i in range(args.days):
        day = start + timedelta(days=day_i)
        skip_p = offrng.uniform(0.02, 0.08)
        for v in fleet:
            if offrng.random() < skip_p:
                continue
            side = v["side"]
            t = datetime(day.year, day.month, day.day) + timedelta(minutes=v["start"])
            for leg in range(1, v["legs"] + 1):
                frm, to = (v["a"], v["b"]) if side == 0 else (v["b"], v["a"])
                dep_vn = t
                arr_vn = t + timedelta(minutes=v["m"])
                t = arr_vn + timedelta(minutes=v["turn"])
                side = 1 - side
                dep_utc, arr_utc = dep_vn - timedelta(hours=7), arr_vn - timedelta(hours=7)
                if dep_utc <= earliest_utc:
                    continue
                code = "%s-%s-%d" % (v["code"], dep_vn.strftime("%y%m%d"), leg)
                if code in existing_codes:
                    continue
                pid = vid.get(v["code"], 0)
                if any(k < arr_utc + timedelta(minutes=30) and d + timedelta(minutes=30) > dep_utc for k, d, _ in busy.get(pid, [])):
                    continue
                trips.append((v, code, frm, to, dep_utc, arr_utc))
                per_day[day] = per_day.get(day, 0) + 1
            v["side"] = side if v["legs"] % 2 else v["side"]
    for d in sorted(per_day):
        print("  %s: %d chuyến" % (d.strftime("%a %d/%m"), per_day[d]))
    print("Tổng chuyến sẽ thêm: %d" % len(trips))
    if args.dry_run or not trips:
        return

    cur.execute("SELECT ISNULL(MAX(ChuyenDiID),0) FROM dbo.ChuyenDi")
    before = cur.fetchone()[0]
    for chunk in batches(trips):
        vals = []
        for v, code, frm, to, dep, arr in chunk:
            seats = len(layout_seats(v["type"]))
            vals.append("(%s,'XE_KHACH',%s,%s,%s,'%s','%s',%d,%d,'DONG_BAN',%d,%s,%s,%s,%s)" % (
                lit(code), lit(v["operator"]), lit(frm), lit(to), dep.strftime("%Y-%m-%d %H:%M:%S"),
                arr.strftime("%Y-%m-%d %H:%M:%S"), seats, price_for(v["km"], v["type"]), vid[v["code"]],
                lit(LAYOUTS[v["type"]][0] + " · " + v["plate"]), lit(v["plate"]),
                lit(place_id[frm]).replace("N'", "'"), lit(place_id[to]).replace("N'", "'")))
        cur.execute("INSERT dbo.ChuyenDi(MaChuyen,LoaiVe,DonViVanChuyen,NoiDi,NoiDen,KhoiHanh,DuKienDen,TongSoCho,GiaCoBan,TrangThai,PhuongTienID,TenPhuongTienSnapshot,SoDangKySnapshot,MaDiemDi,MaDiemDen) VALUES " + ",".join(vals))
    cur.execute("SELECT MaChuyen, ChuyenDiID FROM dbo.ChuyenDi WHERE ChuyenDiID>%d" % before)
    ids = dict(cur.fetchall())
    info_rows = []
    for v, code, frm, to, dep, arr in trips:
        kind = LAYOUTS[v["type"]][1]
        info_rows.append("(%d,%s,%s,%s,%s)" % (ids[code], lit(station(frm, to, True)), lit(station(to, frm, False)), lit(AMENITIES[kind]), lit(NOTE)))
    for chunk in batches(info_rows):
        cur.execute("INSERT dbo.ThongTinChuyen(ChuyenDiID,DiemDon,DiemTra,TienIch,MoTa) VALUES " + ",".join(chunk))
    cur.execute("""INSERT dbo.ChoNgoi(ChuyenDiID,MaCho,LoaiVe,LoaiCho,PhuThu)
        SELECT c.ChuyenDiID,p.MaCho,c.LoaiVe,p.LoaiCho,p.PhuThu FROM dbo.ChuyenDi c
        JOIN dbo.ChoPhuongTien p ON p.PhuongTienID=c.PhuongTienID WHERE c.ChuyenDiID>%d""" % before)
    cur.execute("""INSERT dbo.SoDoCho(ChuyenDiID,MaCho,Tang,Hang,Cot)
        SELECT c.ChuyenDiID,p.MaCho,p.Tang,p.Hang,p.Cot FROM dbo.ChuyenDi c
        JOIN dbo.ChoPhuongTien p ON p.PhuongTienID=c.PhuongTienID WHERE c.ChuyenDiID>%d""" % before)
    cur.execute("UPDATE dbo.ChuyenDi SET TrangThai='MO_BAN' WHERE ChuyenDiID>%d AND TrangThai='DONG_BAN'" % before)
    cur.execute("INSERT dbo.NhatKyThaoTac(NguoiThucHien,HanhDong,DoiTuong,NoiDung) VALUES('SEED','TAO_CHUYEN',%s,%s)",
                ("seed_bus_trips", "Tạo %d chuyến xe khách mẫu từ %s, %d ngày." % (len(trips), start, args.days)))
    print("Đã thêm %d chuyến, mở bán." % len(trips))


if __name__ == "__main__":
    main()
