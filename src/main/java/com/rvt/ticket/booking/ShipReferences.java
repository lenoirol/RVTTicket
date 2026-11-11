package com.rvt.ticket.booking;

import java.util.*;

/** Public reference data, never an assertion of an operator-confirmed assignment. */
public final class ShipReferences {
    private ShipReferences(){}
    public record Ship(String name,String operator,String source){}
    public static Optional<Ship> route(String from,String to){
        Set<String> points=new HashSet<>(Arrays.asList(place(from),place(to)));
        String name=(points.equals(Set.of("Rạch Giá","Phú Quốc"))||points.equals(Set.of("Hà Tiên","Phú Quốc")))?"Phú Quốc Express 7":
            points.equals(Set.of("Trần Đề","Côn Đảo"))?"Côn Đảo Express 36":
            points.equals(Set.of("Vũng Tàu","Côn Đảo"))?"Thăng Long":
            points.equals(Set.of("Phan Thiết","Phú Quý"))?"Trưng Trắc":
            points.equals(Set.of("Sa Kỳ","Lý Sơn"))?"Phú Quốc Express 27":null;
        return name==null?Optional.empty():Optional.of(new Ship(name,"Phú Quốc Express","https://phuquocexpress.com/doi-tau-phu-quoc-express"));
    }
    private static String place(String value){return value==null?"":value.strip().replaceFirst("^Cảng ","");}
}
