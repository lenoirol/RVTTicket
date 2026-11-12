package com.rvt.ticket.booking;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Đổi vé đã thanh toán sang chuyến khác cùng tuyến (đổi ngày / giờ), mỗi đơn đổi được một lần.
 * Trong một transaction: tạo đơn mới với cùng hành khách, chuyển tiền đã trả của đơn cũ sang đơn mới,
 * thu thêm hoặc hoàn lại phần chênh lệch qua số dư RVT, rồi hủy đơn cũ và trả chỗ.
 */
@Service
public class ExchangeService {
    private final JdbcTemplate db;
    private final TicketService tickets;
    private final WalletService wallet;
    private final NotificationService notifications;
    public ExchangeService(JdbcTemplate db,TicketService tickets,WalletService wallet,NotificationService notifications){
        this.db=db;this.tickets=tickets;this.wallet=wallet;this.notifications=notifications;
    }

    /** Chuyến cùng tuyến, cùng loại vé, đủ chỗ cho cả đơn, trong ngày được chọn. */
    public List<Map<String,Object>> candidates(String code,String phone,LocalDate date){
        var order=tickets.order(code,phone);
        int needed=((List<?>)order.get("tickets")).size();
        return tickets.search((String)order.get("LoaiVe"),(String)order.get("NoiDi"),(String)order.get("NoiDen"),date).stream()
            .filter(t->Objects.equals(t.get("NoiDi"),order.get("NoiDi"))&&Objects.equals(t.get("NoiDen"),order.get("NoiDen")))
            .filter(t->!Objects.equals(t.get("ChuyenDiID"),order.get("ChuyenDiID")))
            .filter(t->((Number)t.get("ConCho")).intValue()>=needed).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public String exchange(String code,String phone,String username,int newTripId,List<String> seats){
        tickets.ownerPhone(code,username);
        var info=tickets.order(code,phone);
        // Khóa theo thứ tự id chuyến để hai yêu cầu đổi chéo không chờ nhau mãi.
        int oldTrip=((Number)info.get("ChuyenDiID")).intValue();
        for(int id:new TreeSet<>(List.of(oldTrip,newTripId)))db.queryForList("SELECT ChuyenDiID FROM dbo.ChuyenDi WITH(UPDLOCK,HOLDLOCK) WHERE ChuyenDiID=?",id);
        db.queryForList("SELECT DonDatVeID FROM dbo.DonDatVe WITH(UPDLOCK,HOLDLOCK) WHERE DonDatVeID=?",info.get("DonDatVeID"));
        var old=tickets.order(code,phone);
        if(!Boolean.TRUE.equals(old.get("CoTheDoiVe")))throw new IllegalArgumentException("Đơn không còn đổi được: chỉ đổi đơn đã thanh toán, chưa lên xe / tàu, chưa đổi lần nào và trước hạn đổi vé.");
        ReservationState.require(old.get("TrangThai"),ReservationState.CANCELLED);
        var target=tickets.trip(newTripId);
        if(newTripId==oldTrip||!Objects.equals(target.get("LoaiVe"),old.get("LoaiVe"))||!Objects.equals(target.get("NoiDi"),old.get("NoiDi"))||!Objects.equals(target.get("NoiDen"),old.get("NoiDen")))
            throw new IllegalArgumentException("Chỉ đổi sang chuyến khác cùng tuyến và cùng loại vé.");
        @SuppressWarnings("unchecked") var passengers=(List<Map<String,Object>>)old.get("tickets");
        if(seats==null||seats.size()!=passengers.size()||new HashSet<>(seats).size()!=seats.size())
            throw new IllegalArgumentException("Chọn đúng "+passengers.size()+" chỗ khác nhau cho "+passengers.size()+" hành khách.");

        var form=new BookingForm();
        form.setName((String)old.get("HoTen"));form.setPhone((String)old.get("SoDienThoai"));form.setEmail((String)old.get("Email"));
        if("XE_KHACH".equals(old.get("LoaiVe"))){form.setPickupId((Integer)old.get("DiemDonID"));form.setDropoffId((Integer)old.get("DiemTraID"));}
        for(int i=0;i<passengers.size();i++){
            var t=passengers.get(i);var p=new BookingForm.Passenger();
            p.setName((String)t.get("TenHanhKhach"));p.setSeat(seats.get(i));
            p.setLuggage(t.get("HanhLy")==null?BigDecimal.ZERO:(BigDecimal)t.get("HanhLy"));
            if(t.get("NgaySinh") instanceof java.sql.Date d)p.setBirthDate(d.toLocalDate());
            if(t.get("LoaiGiayTo")!=null)p.setDocumentType((String)t.get("LoaiGiayTo"));
            p.setDocument((String)t.get("SoGiayTo"));
            if(t.get("QuocTich")!=null)p.setNationality((String)t.get("QuocTich"));
            form.getPassengers().add(p);
        }
        String newCode=tickets.book(newTripId,form,username);
        var fresh=tickets.order(newCode,phone);
        BigDecimal paid=(BigDecimal)old.get("TienSeHoan"),total=(BigDecimal)fresh.get("TongTien");
        // Chênh lệch: dương = khách trả thêm từ số dư RVT; âm = hoàn lại vào số dư RVT.
        wallet.exchangeDifference(fresh,username,paid.subtract(total),code);

        // Đơn mới: mỗi vé ghi một khoản DOI_VE bằng đúng giá trị vé, rồi xác nhận.
        for(var t:db.queryForList("SELECT v.DangKyID,v.GiaVe+ISNULL((SELECT SUM(p.SoTien) FROM dbo.ChiTietPhi p WHERE p.DangKyID=v.DangKyID),0) AS SoTien FROM dbo.DangKyVe v JOIN dbo.DonDatVeChiTiet d ON d.DangKyID=v.DangKyID WHERE d.DonDatVeID=?",fresh.get("DonDatVeID"))){
            if(((BigDecimal)t.get("SoTien")).signum()>0)
                db.update("INSERT dbo.ThanhToan(DangKyID,MaGiaoDich,SoTien,PhuongThuc,TrangThai) VALUES(?,?,?,'DOI_VE','THANH_CONG')",t.get("DangKyID"),"DOI-"+UUID.randomUUID(),t.get("SoTien"));
            db.update("UPDATE dbo.DangKyVe SET TrangThai='DA_THANH_TOAN' WHERE DangKyID=?",t.get("DangKyID"));
        }
        db.update("UPDATE dbo.DonDatVe SET TrangThai='DA_THANH_TOAN',DoiTuDonID=? WHERE DonDatVeID=?",old.get("DonDatVeID"),fresh.get("DonDatVeID"));

        // Đơn cũ: trả chỗ, đánh dấu tiền đã chuyển sang đơn mới.
        db.update("UPDATE v SET TrangThai='DA_HUY',DangChiemCho=0 FROM dbo.DangKyVe v JOIN dbo.DonDatVeChiTiet d ON d.DangKyID=v.DangKyID WHERE d.DonDatVeID=? AND v.TrangThai='DA_THANH_TOAN'",old.get("DonDatVeID"));
        db.update("UPDATE p SET TrangThai='DA_HOAN',HoanLuc=SYSUTCDATETIME() FROM dbo.ThanhToan p JOIN dbo.DonDatVeChiTiet d ON d.DangKyID=p.DangKyID WHERE d.DonDatVeID=? AND p.TrangThai='THANH_CONG'",old.get("DonDatVeID"));
        db.update("UPDATE dbo.DonDatVe SET TrangThai='DA_HUY',HuyLuc=SYSUTCDATETIME(),LyDoHuy=?,DoiSangDonID=?,PhiHuy=0,TienHoan=0 WHERE DonDatVeID=?","Đổi sang đơn "+newCode,fresh.get("DonDatVeID"),old.get("DonDatVeID"));
        String difference=paid.compareTo(total)==0?"Không phát sinh chênh lệch."
            :paid.compareTo(total)>0?"Đã hoàn "+paid.subtract(total).stripTrailingZeros().toPlainString()+" đồng chênh lệch vào số dư RVT."
            :"Đã trừ thêm "+total.subtract(paid).stripTrailingZeros().toPlainString()+" đồng chênh lệch từ số dư RVT.";
        notifications.order(old,"CANCEL","Đơn đã đổi sang chuyến khác","Đơn "+code+" đã được đổi sang đơn "+newCode+". Chỗ cũ đã được trả.");
        notifications.order(fresh,"PAY","Đổi vé thành công","Đơn mới "+newCode+" đã xác nhận. "+difference);
        tickets.audit(username,"DOI_VE",code+" → "+newCode);
        return newCode;
    }
}
