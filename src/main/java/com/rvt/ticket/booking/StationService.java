package com.rvt.ticket.booking;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Bến xe có thật (địa chỉ, tọa độ từ OpenStreetMap) và tìm bến gần vị trí khách. */
@Service
public class StationService {
    private final JdbcTemplate db;
    private final TtlCache cache=new TtlCache(java.time.Duration.ofMinutes(10));
    public StationService(JdbcTemplate db){this.db=db;}

    @Transactional(rollbackFor = Exception.class)
    public void seed() throws java.io.IOException {
        for(String line:new ClassPathResource("data/vietnam-bus-stations.tsv").getContentAsString(StandardCharsets.UTF_8).split("\\R")){
            if(line.isBlank()||line.startsWith("#"))continue;
            String[] p=line.split("\\|",-1);
            var lat=new BigDecimal(p[3]);var lng=new BigDecimal(p[4]);String mapName=p[6].isBlank()?null:p[6];
            if(db.update("UPDATE dbo.BenXe SET ThanhPho=?,DiaChi=?,ViDo=?,KinhDo=?,DoChinhXac=?,TenTrenBanDo=? WHERE TenBen=?",p[1],p[2],lat,lng,p[5],mapName,p[0])==0)
                db.update("INSERT dbo.BenXe(TenBen,ThanhPho,DiaChi,ViDo,KinhDo,DoChinhXac,TenTrenBanDo) VALUES(?,?,?,?,?,?,?)",p[0],p[1],p[2],lat,lng,p[5],mapName);
        }
        cache.clear();
    }
    public List<Map<String,Object>> all(){return cache.get("all",()->db.queryForList("SELECT * FROM dbo.BenXe ORDER BY ThanhPho,TenBen"));}
    /** Bến theo tên đúng như trên chuyến (ThongTinChuyen.DiemDon / DiemTra). */
    public Optional<Map<String,Object>> byName(String name){
        if(name==null)return Optional.empty();
        String clean=name.replace(" (bến)","").strip();
        return all().stream().filter(s->s.get("TenBen").equals(clean)).findFirst();
    }
    /** Các bến gần nhất theo khoảng cách đường chim bay (công thức haversine). */
    public List<Map<String,Object>> nearest(double lat,double lng,int limit){
        if(!Double.isFinite(lat)||!Double.isFinite(lng)||lat<-90||lat>90||lng<-180||lng>180)throw new IllegalArgumentException("Tọa độ không hợp lệ.");
        if(limit<1||limit>20)throw new IllegalArgumentException("Số bến tối đa 20.");
        return all().stream().map(s->{var m=new LinkedHashMap<String,Object>(s);
                m.put("km",Math.round(distanceKm(lat,lng,((BigDecimal)s.get("ViDo")).doubleValue(),((BigDecimal)s.get("KinhDo")).doubleValue())*10)/10.0);return (Map<String,Object>)m;})
            .sorted(Comparator.comparingDouble(m->(Double)m.get("km"))).limit(limit).toList();
    }
    public static double distanceKm(double lat1,double lng1,double lat2,double lng2){
        double r=6371,dLat=Math.toRadians(lat2-lat1),dLng=Math.toRadians(lng2-lng1);
        double a=Math.sin(dLat/2)*Math.sin(dLat/2)+Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))*Math.sin(dLng/2)*Math.sin(dLng/2);
        return 2*r*Math.asin(Math.min(1,Math.sqrt(a)));
    }
    public static String mapUrl(Map<String,Object> s){
        return "https://www.google.com/maps/search/?api=1&query="+s.get("ViDo")+","+s.get("KinhDo");
    }
}
