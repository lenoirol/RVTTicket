package com.rvt.ticket.booking;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SupportService {
    public static final Map<String,String> TOPICS=Map.of("VE","Thông tin vé","THANH_TOAN","Thanh toán & số dư","HANH_TRINH","Điểm đón & hành trình","KHAC","Vấn đề khác");
    private final JdbcTemplate db;private final AccountService accounts;private final TicketService tickets;private final NotificationService notifications;
    public SupportService(JdbcTemplate db,AccountService accounts,TicketService tickets,NotificationService notifications){this.db=db;this.accounts=accounts;this.tickets=tickets;this.notifications=notifications;}
    public record Page(List<Map<String,Object>> rows,long total,int page,boolean next){}
    public Page list(String username,String status,int page){
        if(!Set.of("","MOI","DANG_XU_LY","DA_GIAI_QUYET").contains(status)||page<1||page>100000)throw new IllegalArgumentException("Bộ lọc hỗ trợ chưa hợp lệ.");
        String from=" FROM dbo.YeuCauHoTro y JOIN dbo.TaiKhoanKhach a ON a.TaiKhoanID=y.TaiKhoanID LEFT JOIN dbo.DonDatVe o ON o.DonDatVeID=y.DonDatVeID WHERE (?='' OR y.TrangThai=?)";
        var args=new ArrayList<Object>(List.of(status,status));
        if(username!=null){from+=" AND a.TenDangNhap=?";args.add(username);}
        long total=db.queryForObject("SELECT COUNT_BIG(*)"+from,Long.class,args.toArray());
        page=Math.min(page,Math.max(1,(int)((total+19)/20)));args.add((page-1)*20);
        var rows=db.queryForList("SELECT y.*,a.HoTen,o.MaDon"+from+" ORDER BY y.CapNhatLuc DESC,y.YeuCauID DESC OFFSET ? ROWS FETCH NEXT 20 ROWS ONLY",args.toArray());
        return new Page(rows,total,page,total>page*20L);
    }
    public Map<String,Object> detail(int id,String username){
        var rows=db.queryForList("SELECT y.*,a.HoTen,a.TenDangNhap,o.MaDon FROM dbo.YeuCauHoTro y JOIN dbo.TaiKhoanKhach a ON a.TaiKhoanID=y.TaiKhoanID LEFT JOIN dbo.DonDatVe o ON o.DonDatVeID=y.DonDatVeID WHERE y.YeuCauID=?",id);
        if(rows.isEmpty()||(username!=null&&!username.equals(rows.getFirst().get("TenDangNhap"))))throw new IllegalArgumentException("Không tìm thấy yêu cầu hỗ trợ của bạn.");
        var result=rows.getFirst();result.put("messages",db.queryForList("SELECT TuQuanTri,NoiDung,NgayTao FROM dbo.TinNhanHoTro WHERE YeuCauID=? ORDER BY TinNhanID",id));return result;
    }
    @Transactional(rollbackFor = Exception.class)
    public int create(String username,String code,String topic,String title,String content){
        checkText(title,2,120,"Tiêu đề");checkText(content,10,2000,"Nội dung");
        if(!TOPICS.containsKey(topic))throw new IllegalArgumentException("Chọn chủ đề hỗ trợ hợp lệ.");
        var account=accounts.profile(username);
        // Bound simultaneous creates per customer; keep all existing conversations.
        db.queryForList("SELECT TaiKhoanID FROM dbo.TaiKhoanKhach WITH (UPDLOCK,HOLDLOCK) WHERE TaiKhoanID=?",account.get("TaiKhoanID"));
        if(db.queryForObject("SELECT COUNT(*) FROM dbo.YeuCauHoTro WHERE TaiKhoanID=? AND TrangThai<>'DA_GIAI_QUYET'",Integer.class,account.get("TaiKhoanID"))>=5)throw new IllegalArgumentException("Bạn đang có 5 yêu cầu chưa kết thúc. Hãy bổ sung nội dung vào yêu cầu đang mở.");
        Integer order=null;
        if(code!=null&&!code.isBlank()){
            code=code.strip().toUpperCase(Locale.ROOT);String phone=tickets.ownerPhone(code,username);
            order=((Number)tickets.order(code,phone).get("DonDatVeID")).intValue();
        }
        int id=tickets.insert("INSERT dbo.YeuCauHoTro(MaYeuCau,TaiKhoanID,DonDatVeID,ChuDe,TieuDe) VALUES(?,?,?,?,?)","HT-"+UUID.randomUUID().toString().substring(0,12).toUpperCase(Locale.ROOT),account.get("TaiKhoanID"),order,topic,title.strip());
        db.update("INSERT dbo.TinNhanHoTro(YeuCauID,TuQuanTri,NoiDung) VALUES(?,0,?)",id,content.strip());return id;
    }
    @Transactional(rollbackFor = Exception.class)
    public void reply(int id,String username,boolean admin,String content,String status){
        checkText(content,2,2000,"Nội dung phản hồi");
        if(admin&&!Set.of("DANG_XU_LY","DA_GIAI_QUYET").contains(status))throw new IllegalArgumentException("Trạng thái xử lý không hợp lệ.");
        db.queryForList("SELECT YeuCauID FROM dbo.YeuCauHoTro WITH (UPDLOCK,HOLDLOCK) WHERE YeuCauID=?",id);
        var request=detail(id,admin?null:username);
        // All authorization is resolved from the server-side principal, not a form role.
        if(admin&&db.queryForObject("SELECT COUNT(*) FROM dbo.TaiKhoanQuanTri WHERE TenDangNhap=?",Integer.class,username)!=1)throw new IllegalArgumentException("Tài khoản không có quyền quản trị.");
        db.update("INSERT dbo.TinNhanHoTro(YeuCauID,TuQuanTri,NoiDung) VALUES(?,?,?)",id,admin,content.strip());
        db.update("UPDATE dbo.YeuCauHoTro SET TrangThai=?,CapNhatLuc=SYSUTCDATETIME() WHERE YeuCauID=?",admin?status:"MOI",id);
        if(admin){tickets.audit(username,"TRA_LOI_HO_TRO",(String)request.get("MaYeuCau"));notifications.support(request);}
    }
    private static void checkText(String value,int min,int max,String label){if(value==null||value.strip().length()<min||value.strip().length()>max)throw new IllegalArgumentException(label+" cần "+min+"–"+max+" ký tự.");}
}
