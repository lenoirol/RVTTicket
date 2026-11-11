package com.rvt.ticket.booking;

import java.util.*;

/** Danh mục điểm đến du lịch có ảnh (Wikimedia Commons) dùng cho trang chủ, trang Điểm đến và trang Nguồn ảnh.
 *  {@code to} là giá trị ô "Điểm đến" khi tìm chuyến: tên địa điểm hoặc "province:&lt;mã tỉnh&gt;". */
public final class DestinationGuide {
    private DestinationGuide() {}
    public record Destination(String slug,String name,String region,String blurb,String type,String to,String note,
                              String author,String license,String licenseUrl,String source) {
        public String image() { return "/images/places/"+slug+".jpg"; }
        public String typeLabel() { return "TAU_THUYEN".equals(type)?"Tàu cao tốc":"Xe khách"; }
    }
    public static final List<Destination> ALL = List.of(
        new Destination("ha-noi","Hà Nội","Miền Bắc","Hồ Gươm, phố cổ và ba bến xe lớn Mỹ Đình, Giáp Bát, Nước Ngầm đi khắp miền Bắc.","XE_KHACH","Hà Nội",null,"Jakub Hałun","CC BY 4.0","https://creativecommons.org/licenses/by/4.0","https://commons.wikimedia.org/wiki/File:Huc_Bridge_in_Hanoi,_Vietnam,_20240204_1301_5726.jpg"),
        new Destination("sa-pa","Sa Pa","Miền Bắc","Ruộng bậc thang, bản Cát Cát và đỉnh Fansipan. Xe giường nằm từ Hà Nội khoảng 7 giờ.","XE_KHACH","Sa Pa",null,"Christophe95","CC BY-SA 4.0","https://creativecommons.org/licenses/by-sa/4.0","https://commons.wikimedia.org/wiki/File:Rice_terraces_in_Sa_Pa_08.jpg"),
        new Destination("ha-giang","Hà Giang","Miền Bắc","Đèo Mã Pí Lèng và cao nguyên đá Đồng Văn. Phần lớn khách đi xe đêm từ Hà Nội.","XE_KHACH","Hà Giang",null,"Jaybeelarsay","CC BY-SA 3.0","https://creativecommons.org/licenses/by-sa/3.0","https://commons.wikimedia.org/wiki/File:Le_col_de_Ma_Pi_Leng_(Dong_Van-Meo_Vac).jpg"),
        new Destination("ninh-binh","Ninh Bình","Miền Bắc","Tràng An, Tam Cốc: núi đá vôi soi bóng nước, đi thuyền nan. Cách Hà Nội chừng 2 giờ xe.","XE_KHACH","Ninh Bình",null,"Jakub Hałun","CC BY 4.0","https://creativecommons.org/licenses/by/4.0","https://commons.wikimedia.org/wiki/File:Trang_An_Landscape_Complex,_Ninh_Binh_Province,_Vietnam,_20240202_1433_5284.jpg"),
        new Destination("ha-long","Hạ Long","Miền Bắc","Vịnh Hạ Long, đảo Ti Tốp và du thuyền ngủ đêm. Có xe limousine từ Hà Nội.","XE_KHACH","Hạ Long",null,"Shyamal","CC BY-SA 4.0","https://creativecommons.org/licenses/by-sa/4.0","https://commons.wikimedia.org/wiki/File:Ha_Long_Bay_24.jpg"),
        new Destination("phong-nha","Phong Nha","Miền Trung","Động Phong Nha, động Thiên Đường và sông Son. Đi xe tới Đồng Hới, thêm khoảng 45 km.","XE_KHACH","Đồng Hới","Xe đến Đồng Hới","Andre Hospers","CC BY 4.0","https://creativecommons.org/licenses/by/4.0","https://commons.wikimedia.org/wiki/File:Dong_Hoi_Phong_Nha_grotten.jpg"),
        new Destination("hue","Huế","Miền Trung","Đại Nội, lăng tẩm các vua Nguyễn và sông Hương.","XE_KHACH","Huế",null,"Supanut Arunoprayote","CC BY 4.0","https://creativecommons.org/licenses/by/4.0","https://commons.wikimedia.org/wiki/File:Gate_in_Imperial_City,_Hu%E1%BA%BF_(III).jpg"),
        new Destination("da-nang","Đà Nẵng","Miền Trung","Biển Mỹ Khê, bán đảo Sơn Trà và đèo Hải Vân.","XE_KHACH","Đà Nẵng",null,"Kuroczynski","CC BY-SA 4.0","https://creativecommons.org/licenses/by-sa/4.0","https://commons.wikimedia.org/wiki/File:Da_Nang_china_Beach_2020_IMG_4009.jpg"),
        new Destination("hoi-an","Hội An","Miền Trung","Phố cổ tường vàng, đèn lồng về đêm. Đi xe tới Đà Nẵng rồi thêm khoảng 30 km.","XE_KHACH","province:da-nang","Xe đến Đà Nẵng","Steffen Schmitz","CC BY-SA 4.0","https://creativecommons.org/licenses/by-sa/4.0","https://commons.wikimedia.org/wiki/File:H%E1%BB%99i_An,_Ancient_Town,_2020-01_CN-06.jpg"),
        new Destination("ly-son","Lý Sơn","Miền Trung","Đảo núi lửa, vách đá ven biển và cánh đồng tỏi. Tàu cao tốc từ cảng Sa Kỳ.","TAU_THUYEN","Lý Sơn",null,"Kim Yong Soo/Doosan","CC BY 3.0","https://creativecommons.org/licenses/by/3.0","https://commons.wikimedia.org/wiki/File:Ly_Son_Island_13_-_panoramio.jpg"),
        new Destination("quy-nhon","Quy Nhơn","Miền Trung","Biển Kỳ Co, Eo Gió và những làng chài thuyền thúng.","XE_KHACH","Quy Nhơn",null,"Pulencio","CC BY 2.0","https://creativecommons.org/licenses/by/2.0","https://commons.wikimedia.org/wiki/File:Nhon_Ly,_Quy_Nhon,_Vietnam_(53001404016).jpg"),
        new Destination("nha-trang","Nha Trang","Miền Trung","Vịnh biển nhiều đảo, nước trong, lặn ngắm san hô ở Hòn Mun.","XE_KHACH","Nha Trang",null,"Minh Duc Ly","Public domain","","https://commons.wikimedia.org/wiki/File:H%C3%B2n_N%E1%BB%99i,_Nha_Trang_Bay,_Vietnam.jpg"),
        new Destination("da-lat","Đà Lạt","Miền Trung","Thành phố cao nguyên: ga xe lửa cổ, hồ Xuân Hương, đồi thông.","XE_KHACH","Đà Lạt",null,"P. Hughes","CC BY 4.0","https://creativecommons.org/licenses/by/4.0","https://commons.wikimedia.org/wiki/File:Da_Lat_-_Train_Station.jpg"),
        new Destination("mui-ne","Mũi Né","Miền Nam","Đồi cát, biển nhiều gió cho lướt ván diều, gần Phan Thiết.","XE_KHACH","Phan Thiết","Xe đến Phan Thiết","Vyacheslav Argenberg","CC BY 4.0","https://creativecommons.org/licenses/by/4.0","https://commons.wikimedia.org/wiki/File:Vietnam,_Mui_Ne_sand_dunes,_trees_on_the_sand.jpg"),
        new Destination("vung-tau","Vũng Tàu","Miền Nam","Bãi Trước, Bãi Sau và ngọn hải đăng, gần TP. Hồ Chí Minh nhất.","XE_KHACH","Vũng Tàu",null,"Tuong Lam Photos","CC BY-SA 4.0","https://creativecommons.org/licenses/by-sa/4.0","https://commons.wikimedia.org/wiki/File:Vung_Tau,_Viet_Nam_2021.jpg"),
        new Destination("can-tho","Cần Thơ","Miền Nam","Chợ nổi Cái Răng họp từ sáng sớm, miệt vườn sông nước miền Tây.","XE_KHACH","Cần Thơ",null,"Jean-Marc Astesana","CC BY-SA 2.0","https://creativecommons.org/licenses/by-sa/2.0","https://commons.wikimedia.org/wiki/File:Cai_Rang_floating_market_(15886375226).jpg"),
        new Destination("con-dao","Côn Đảo","Miền Nam","Cung đường ven biển, rừng quốc gia và khu di tích nhà tù. Tàu từ Vũng Tàu và Trần Đề.","TAU_THUYEN","Côn Đảo",null,"Lam Tr Th","CC BY-SA 3.0","https://creativecommons.org/licenses/by-sa/3.0","https://commons.wikimedia.org/wiki/File:C%C3%B4n_%C4%90%E1%BA%A3o,_B%C3%A0_R%E1%BB%8Ba_-_V%C5%A9ng_T%C3%A0u,_Vietnam_-_panoramio_(10).jpg"),
        new Destination("phu-quoc","Phú Quốc","Miền Nam","Bãi Sao cát trắng, nước xanh. Tàu cao tốc từ Rạch Giá và Hà Tiên.","TAU_THUYEN","Phú Quốc",null,"Trantuonglam","CC BY-SA 4.0","https://creativecommons.org/licenses/by-sa/4.0","https://commons.wikimedia.org/wiki/File:Bai-sao-phu-quoc-tuonglamphotos.jpg")
    );
    private static final List<String> FEATURED = List.of("sa-pa", "ha-giang", "ninh-binh", "ha-long", "hoi-an", "da-lat", "nha-trang", "phu-quoc", "can-tho", "con-dao");
    public static List<Destination> featured() { return FEATURED.stream().map(s->ALL.stream().filter(d->d.slug().equals(s)).findFirst().orElseThrow()).toList(); }
    /** Nhóm theo vùng, giữ thứ tự Bắc → Trung → Nam. */
    public static Map<String,List<Destination>> byRegion() {
        var out=new LinkedHashMap<String,List<Destination>>();
        for(var d:ALL) out.computeIfAbsent(d.region(),k->new ArrayList<>()).add(d);
        return out;
    }
}
