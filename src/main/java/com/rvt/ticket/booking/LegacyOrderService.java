package com.rvt.ticket.booking;

import java.math.BigDecimal;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** One original ticket becomes one order. No inferred ownership and no historical money is credited. */
@Service
public class LegacyOrderService {
    private final JdbcTemplate db;
    public LegacyOrderService(JdbcTemplate db){this.db=db;}
    @Transactional(rollbackFor = Exception.class) public int importAll(){
        int count=0;
        for(int id:db.queryForList("SELECT DISTINCT v.ChuyenDiID FROM dbo.DangKyVe v WHERE NOT EXISTS(SELECT 1 FROM dbo.DonDatVeChiTiet d WHERE d.DangKyID=v.DangKyID) ORDER BY v.ChuyenDiID",Integer.class))count+=importTrip(id);
        return count;
    }
    @Transactional(rollbackFor = Exception.class) public int importTrip(int tripId){
        db.queryForList("SELECT ChuyenDiID FROM dbo.ChuyenDi WITH(UPDLOCK,HOLDLOCK) WHERE ChuyenDiID=?",tripId);
        var rows=db.queryForList("SELECT v.*,COALESCE((SELECT SUM(f.SoTien) FROM dbo.ChiTietPhi f WHERE f.DangKyID=v.DangKyID),0) AS Phi FROM dbo.DangKyVe v WHERE v.ChuyenDiID=? AND NOT EXISTS(SELECT 1 FROM dbo.DonDatVeChiTiet d WHERE d.DangKyID=v.DangKyID) ORDER BY v.DangKyID",tripId);
        for(var v:rows){
            String code=(String)v.get("DangKyCode");
            if(db.queryForObject("SELECT COUNT(*) FROM dbo.DonDatVe WHERE MaDon=?",Integer.class,code)>0){
                code="LEGACY-"+v.get("DangKyID");int suffix=0;
                while(db.queryForObject("SELECT COUNT(*) FROM dbo.DonDatVe WHERE MaDon=?",Integer.class,code)>0)code="LEGACY-"+v.get("DangKyID")+"-"+(++suffix);
            }
            var key=new GeneratedKeyHolder();final String finalCode=code;
            BigDecimal total=((BigDecimal)v.get("GiaVe")).add((BigDecimal)v.get("Phi"));
            db.update(c->{var s=c.prepareStatement("INSERT dbo.DonDatVe(MaDon,KhachHangID,ChuyenDiID,TrangThai,TongTien,NgayTao,HanThanhToan,GioHanHuy) VALUES(?,?,?,?,?,?,?,24)",java.sql.Statement.RETURN_GENERATED_KEYS);
                Object[] values={finalCode,v.get("KhachHangID"),tripId,v.get("TrangThai"),total,v.get("NgayDangKy"),v.get("HanThanhToan")};
                for(int i=0;i<values.length;i++)s.setObject(i+1,values[i]);return s;},key);
            int order=Objects.requireNonNull(key.getKey()).intValue();
            db.update("INSERT dbo.DonDatVeChiTiet(DonDatVeID,DangKyID) VALUES(?,?)",order,v.get("DangKyID"));
            db.update("UPDATE o SET DiemDonSnapshot=COALESCE(x.DiemDon,m.DiemDon,c.NoiDi),DiemTraSnapshot=COALESCE(x.DiemTra,m.DiemTra,c.NoiDen) FROM dbo.DonDatVe o JOIN dbo.ChuyenDi c ON c.ChuyenDiID=o.ChuyenDiID LEFT JOIN dbo.ThongTinChuyen m ON m.ChuyenDiID=c.ChuyenDiID LEFT JOIN dbo.VeXeKhach x ON x.DangKyID=? WHERE o.DonDatVeID=?",v.get("DangKyID"),order);
            db.update("INSERT dbo.NhatKyThaoTac(NguoiThucHien,HanhDong,DoiTuong,NoiDung) VALUES('SYSTEM','NHAP_VE_GOC',?,N'Gắn vé gốc vào đơn; giữ nguyên giá, biên nhận, chỗ và khách. Chưa gắn tài khoản, chưa cộng tiền hoàn lịch sử.')",code);
        }
        return rows.size();
    }
}
