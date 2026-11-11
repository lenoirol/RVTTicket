package com.rvt.ticket.booking;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;

public class TripForm {
    @Min(1) private Integer vehicleId;
    public Integer getVehicleId(){return vehicleId;} public void setVehicleId(Integer v){vehicleId=v;}
    @NotBlank @Pattern(regexp="[A-Z0-9-]{3,30}")
    @Pattern(regexp="(?!(?:RVT-DEMO-|RVT-SEA-)).*",message="Tiền tố RVT-DEMO / RVT-SEA dành cho dữ liệu khởi tạo, không dùng cho chuyến mới.") private String code;
    @NotBlank @Size(max=100) private String operator;
    @NotBlank @Size(max=150) private String from;
    @NotBlank @Size(max=150) private String to;
    @NotBlank private String layout="BUS_SEAT";
    @NotNull @Future @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) private LocalDateTime departure;
    @NotNull @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) private LocalDateTime arrival;
    @NotNull @DecimalMin("1000") @DecimalMax("10000000") private BigDecimal price;
    @DecimalMin("1000") @DecimalMax("10000000") private BigDecimal childPrice;
    @DecimalMin("1000") @DecimalMax("10000000") private BigDecimal seniorPrice;
    public BigDecimal getChildPrice(){return childPrice;} public void setChildPrice(BigDecimal v){childPrice=v;}
    public BigDecimal getSeniorPrice(){return seniorPrice;} public void setSeniorPrice(BigDecimal v){seniorPrice=v;}
    @Min(1) @Max(15) private int rows=7;
    @Min(0) @Max(20) private int vipSeats=0;
    @NotNull @DecimalMin("0") @DecimalMax("1000000") private BigDecimal vipExtra=BigDecimal.ZERO;
    @Size(max=150) private String pickup="";
    @Size(max=150) private String dropoff="";
    @Size(max=500) private String amenities="";
    @Size(max=1000) private String description="";
    public String getCode(){return code;} public void setCode(String v){code=v;}
    public String getOperator(){return operator;} public void setOperator(String v){operator=v;}
    public String getFrom(){return from;} public void setFrom(String v){from=v;}
    public String getTo(){return to;} public void setTo(String v){to=v;}
    public String getLayout(){return layout;} public void setLayout(String v){layout=v;}
    public LocalDateTime getDeparture(){return departure;} public void setDeparture(LocalDateTime v){departure=v;}
    public LocalDateTime getArrival(){return arrival;} public void setArrival(LocalDateTime v){arrival=v;}
    public BigDecimal getPrice(){return price;} public void setPrice(BigDecimal v){price=v;}
    public int getRows(){return rows;} public void setRows(int v){rows=v;}
    public int getVipSeats(){return vipSeats;} public void setVipSeats(int v){vipSeats=v;}
    public BigDecimal getVipExtra(){return vipExtra;} public void setVipExtra(BigDecimal v){vipExtra=v;}
    public String getPickup(){return pickup;} public void setPickup(String v){pickup=v;}
    public String getDropoff(){return dropoff;} public void setDropoff(String v){dropoff=v;}
    public String getAmenities(){return amenities;} public void setAmenities(String v){amenities=v;}
    public String getDescription(){return description;} public void setDescription(String v){description=v;}
}
