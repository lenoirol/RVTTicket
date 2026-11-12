package com.rvt.ticket.booking;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;

public final class FarePolicy {
    private FarePolicy() {}
    public static String group(LocalDate birth,LocalDate travel){
        if(birth==null||birth.isAfter(travel)||Period.between(birth,travel).getYears()>120)
            throw new IllegalArgumentException("Nhập ngày sinh hợp lệ; tuổi tính tại ngày khởi hành (0–120 tuổi).");
        int age=Period.between(birth,travel).getYears();
        return age<12?"TRE_EM":age>=60?"CAO_TUOI":"NGUOI_LON";
    }
    public static BigDecimal discounted(BigDecimal adult){return adult.multiply(new BigDecimal("0.8")).divide(BigDecimal.valueOf(1000),0,RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(1000)).max(BigDecimal.valueOf(1000));}
    public static boolean refundAllowed(java.time.LocalDateTime departure,java.time.LocalDateTime now){return !departure.isBefore(now.plusHours(24));}
    /** Phí hủy = số tiền đã trả × tỷ lệ của mốc, làm tròn tới 1.000 đồng và không vượt số đã trả. */
    public static BigDecimal cancellationFee(BigDecimal paid,BigDecimal rate){
        if(paid==null||paid.signum()<=0)return BigDecimal.ZERO;
        return paid.multiply(rate).divide(BigDecimal.valueOf(1000),0,RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(1000)).min(paid).max(BigDecimal.ZERO);
    }
    public static BigDecimal passengerFare(String group,BigDecimal adult,BigDecimal child,BigDecimal senior,boolean vip){
        if(vip||"NGUOI_LON".equals(group))return adult;
        return "TRE_EM".equals(group)?(child==null?discounted(adult):child):(senior==null?discounted(adult):senior);
    }
    public static BigDecimal luggageFee(String type, BigDecimal kg) {
        boolean bus = "XE_KHACH".equals(type);
        BigDecimal max = BigDecimal.valueOf(bus ? 30 : 40);
        if (kg == null || kg.signum() < 0 || kg.compareTo(max) > 0)
            throw new IllegalArgumentException("Hành lý tối đa " + max + " kg mỗi hành khách.");
        BigDecimal excess = kg.subtract(BigDecimal.valueOf(bus ? 20 : 25)).max(BigDecimal.ZERO);
        return excess.multiply(BigDecimal.valueOf(bus ? 10000 : 15000));
    }
    public static BigDecimal luggageFee(BigDecimal kg,BigDecimal allowance,BigDecimal maximum,BigDecimal rate) {
        if(kg==null || kg.signum()<0 || kg.compareTo(maximum)>0)
            throw new IllegalArgumentException("Hành lý tối đa "+maximum.stripTrailingZeros().toPlainString()+" kg mỗi hành khách.");
        return kg.subtract(allowance).max(BigDecimal.ZERO).multiply(rate);
    }
    public static void validateDocument(String type, String document) {
        validateDocument(type,"HO_CHIEU",document);
    }
    public static void validateDocument(String ticketType,String documentType,String document){
        if(!"TAU_THUYEN".equals(ticketType))return;
        if("CCCD".equals(documentType)){
            if(document==null||!document.matches("[0-9]{12}"))throw new IllegalArgumentException("Số căn cước / CCCD phải gồm đúng 12 chữ số.");
        }else if("HO_CHIEU".equals(documentType)){
            if(document==null||!document.matches("[A-Za-z0-9]{6,30}"))throw new IllegalArgumentException("Số hộ chiếu cần 6–30 ký tự chữ hoặc số, không có khoảng trắng.");
        }else if("KHAI_SINH".equals(documentType)){
            if(document==null||!document.matches("[\\p{L}\\p{N}./-]{1,30}"))throw new IllegalArgumentException("Nhập số giấy khai sinh hợp lệ, tối đa 30 ký tự.");
        }else throw new IllegalArgumentException("Chọn loại giấy tờ hợp lệ.");
    }
}
