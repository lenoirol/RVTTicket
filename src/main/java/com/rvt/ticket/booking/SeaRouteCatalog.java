package com.rvt.ticket.booking;

import java.util.List;

/** Dated research presets, not an operator API or a live timetable. */
public final class SeaRouteCatalog {
    private SeaRouteCatalog(){}
    public record Route(String code,String from,String to,String portFrom,String portTo,int minutes,int adult,int child,int senior,int vip,String note,String source){}
    public static List<Route> routes(){return List.of(
        new Route("RG-PQ","Rạch Giá","Phú Quốc","Cảng thủy nội địa Rạch Giá","Cảng Bãi Vòng · Phú Quốc",150,315000,225000,265000,500000,"Lựa chọn cảng đất liền cho hành trình từ miền Tây. Thời gian là ước tính, không phải cam kết giờ đến.","https://phuquocexpress.com/dat-ve-tau-cao-toc-di-phu-quoc"),
        new Route("HT-PQ","Hà Tiên","Phú Quốc","Cảng Hà Tiên","Cảng Bãi Vòng · Phú Quốc",80,216000,148000,182000,330000,"Hải trình ngắn hơn tuyến từ Rạch Giá; chọn cảng phù hợp vị trí xuất phát.","https://phuquocexpress.com/dat-ve-tau-cao-toc-di-phu-quoc"),
        new Route("VT-CD","Vũng Tàu","Côn Đảo","Cảng Cầu Đá · 09 Hạ Long, Vũng Tàu","Cảng Bến Đầm · Côn Đảo",240,790000,630000,630000,1000000,"Giá mẫu ngày thường; cuối tuần dùng giá khác do quản trị khai báo, không tự xem mọi ngày là ngày thường.","https://phuquocexpress.com/gia-ve-tau-cao-toc-di-con-dao"),
        new Route("TD-CD","Cảng Trần Đề","Côn Đảo","Cảng cá Trần Đề","Cảng Bến Đầm · Côn Đảo",135,390000,312000,312000,590000,"Một lựa chọn ra Côn Đảo từ miền Tây. Cần kiểm tra giờ tàu theo thông báo vận hành.","https://phuquocexpress.com/gia-ve-tau-cao-toc-di-con-dao"),
        new Route("PT-PQY","Phan Thiết","Phú Quý","Cảng vận tải Phan Thiết","Cảng Phú Quý",135,370000,275000,295000,450000,"Giờ tàu thay đổi theo con nước; lịch RVT là lịch mẫu do quản trị nhập, không phải lịch hãng.","https://phuquocexpress.com/gia-ve-tau-cao-toc-di-phu-quy"),
        new Route("SK-LS","Cảng Sa Kỳ","Lý Sơn","Cảng Sa Kỳ","Cảng Bến Đình · Lý Sơn",60,182000,128000,147000,238000,"Giá mẫu đã gộp phí cảng theo nhóm; không thu thêm lần nữa. Thời lượng RVT là ước tính phục vụ đồ án.","https://phuquocexpress.com/gia-ve-tau-cao-toc-di-ly-son")
    );}
}
