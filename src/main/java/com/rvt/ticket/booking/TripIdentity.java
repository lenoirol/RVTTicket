package com.rvt.ticket.booking;

import java.util.Map;
import java.util.Objects;

/** A stable public code while retaining the original import key for historical links. */
public final class TripIdentity {
    private TripIdentity() {}
    public static String code(Map<String,Object> trip) {
        String code=Objects.toString(trip.get("MaChuyen"),"");
        return code.startsWith("RVT-DEMO-")||code.startsWith("RVT-SEA-")?
            "RVT-"+("TAU_THUYEN".equals(trip.get("LoaiVe"))?"T":"B")+trip.get("ChuyenDiID"):code;
    }
    public static String sql(String alias) {
        if(!alias.matches("[a-zA-Z][a-zA-Z0-9_]*"))throw new IllegalArgumentException("Invalid SQL alias");
        return "CASE WHEN LEFT("+alias+".MaChuyen,9)='RVT-DEMO-' OR LEFT("+alias+".MaChuyen,8)='RVT-SEA-' THEN 'RVT-'+CASE WHEN "+alias+".LoaiVe='TAU_THUYEN' THEN 'T' ELSE 'B' END+CONVERT(VARCHAR(20),"+alias+".ChuyenDiID) ELSE "+alias+".MaChuyen END";
    }
}
