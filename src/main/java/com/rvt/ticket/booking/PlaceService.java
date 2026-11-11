package com.rvt.ticket.booking;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlaceService {
    private final JdbcTemplate db;
    public PlaceService(JdbcTemplate db){this.db=db;}
    public static String normalized(String value) {
        return Normalizer.normalize(value.strip(),Normalizer.Form.NFD).replaceAll("\\p{M}","").replace('đ','d').replace('Đ','D').toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+"," ").strip();
    }
    @Transactional(rollbackFor = Exception.class)
    public void seed() throws java.io.IOException {
        for(String line:new ClassPathResource("data/vietnam-places.tsv").getContentAsString(StandardCharsets.UTF_8).split("\\R")){
            if(line.isBlank()||line.startsWith("#"))continue;
            String[] p=line.split("\\|",-1);
            if(db.queryForObject("SELECT COUNT(*) FROM dbo.TinhThanh WHERE MaTinh=?",Integer.class,p[0])==0)
                db.update("INSERT dbo.TinhThanh VALUES(?,?,?)",p[0],p[1],p[2]);
            int n=0;
            for(String name:p[3].split(";")){
                String id=p[0]+"-"+(++n);
                if(db.queryForObject("SELECT COUNT(*) FROM dbo.DiaDiem WHERE TenDiem=?",Integer.class,name)==0)
                    db.update("INSERT dbo.DiaDiem VALUES(?,?,?)",id,p[0],name);
            }
        }
        db.update("UPDATE c SET MaDiemDi=d.MaDiem FROM dbo.ChuyenDi c JOIN dbo.DiaDiem d ON d.TenDiem=c.NoiDi WHERE c.MaDiemDi IS NULL");
        db.update("UPDATE c SET MaDiemDen=d.MaDiem FROM dbo.ChuyenDi c JOIN dbo.DiaDiem d ON d.TenDiem=c.NoiDen WHERE c.MaDiemDen IS NULL");
    }
    public List<Map<String,Object>> provinces(){return db.queryForList("SELECT * FROM dbo.TinhThanh ORDER BY TenTinh");}
    public List<Map<String,Object>> destinations(){return db.queryForList("SELECT d.*,p.TenTinh,p.TenCu FROM dbo.DiaDiem d JOIN dbo.TinhThanh p ON p.MaTinh=d.MaTinh ORDER BY p.TenTinh,d.TenDiem");}
    public List<Map<String,Object>> groups(){
        var destinations=destinations();return provinces().stream().map(p->{
            var group=new LinkedHashMap<String,Object>(p);
            group.put("places",destinations.stream().filter(d->d.get("MaTinh").equals(p.get("MaTinh"))).toList());return (Map<String,Object>)group;
        }).toList();
    }
    @Transactional(rollbackFor = Exception.class)
    public void add(String province,String name,String actor){
        if(province==null||name==null||name.isBlank()||name.length()>150||db.queryForObject("SELECT COUNT(*) FROM dbo.TinhThanh WHERE MaTinh=?",Integer.class,province)==0)
            throw new IllegalArgumentException("Chọn tỉnh/thành và nhập tên điểm đến (tối đa 150 ký tự).");
        db.update("INSERT dbo.DiaDiem VALUES(?,?,?)","custom-"+UUID.randomUUID().toString().replace("-",""),province,name.strip());
        db.update("INSERT dbo.NhatKyThaoTac(NguoiThucHien,HanhDong,DoiTuong) VALUES(?,'THEM_DIA_DIEM',?)",actor,name.strip().substring(0,Math.min(name.strip().length(),100)));
    }
    public List<String> searchNames(String selected) {
        if(selected==null||selected.isBlank()) return List.of();
        if(selected.startsWith("province:")){
            String id=selected.substring(9);
            if(db.queryForObject("SELECT COUNT(*) FROM dbo.TinhThanh WHERE MaTinh=?",Integer.class,id)==0)
                throw new IllegalArgumentException("Tỉnh/thành không có trong danh mục.");
            var names=db.queryForList("SELECT TenDiem FROM dbo.DiaDiem WHERE MaTinh=?",String.class,id);
            names.add(db.queryForObject("SELECT TenTinh FROM dbo.TinhThanh WHERE MaTinh=?",String.class,id));return names;
        }
        String value=normalized(selected);
        if(Set.of("sai gon","ho chi minh","tp hcm","tphcm").contains(value))return List.of("TP. Hồ Chí Minh");
        for(var d:destinations()) if(normalized((String)d.get("TenDiem")).equals(value)||d.get("MaDiem").equals(selected))
            return List.of((String)d.get("TenDiem"));
        for(var p:provinces()){
            if(normalized((String)p.get("TenTinh")).equals(value)||normalized((String)p.get("MaTinh")).equals(value)){
                var names=db.queryForList("SELECT TenDiem FROM dbo.DiaDiem WHERE MaTinh=?",String.class,p.get("MaTinh"));
                names.add((String)p.get("TenTinh"));return names;
            }
        }
        for(var d:destinations()) if(normalized((String)d.get("TenDiem")).equals(value)||d.get("MaDiem").equals(selected))
            return List.of((String)d.get("TenDiem"));
        // Former province names remain searchable, but are not labelled current provinces.
        for(var p:provinces()) for(String alias:((String)p.get("TenCu")).split(";"))if(!alias.isBlank()&&normalized(alias).equals(value))
            return db.queryForList("SELECT TenDiem FROM dbo.DiaDiem WHERE MaTinh=?",String.class,p.get("MaTinh"));
        return List.of(selected.strip());
    }
    public String destinationId(String name){var rows=db.queryForList("SELECT MaDiem FROM dbo.DiaDiem WHERE TenDiem=?",String.class,name);return rows.isEmpty()?null:rows.getFirst();}
}
