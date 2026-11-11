package com.rvt.ticket.booking;

import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TripBatchService {
    private final TicketService tickets;
    private final FleetService fleet;
    public TripBatchService(TicketService tickets,FleetService fleet){this.tickets=tickets;this.fleet=fleet;}
    public List<TripForm> preview(TripForm source,int days) {
        if(days<1||days>31)throw new IllegalArgumentException("Một lần tạo từ 1 đến 31 ngày liên tiếp.");
        if(source.getVehicleId()==null)throw new IllegalArgumentException("Chọn phương tiện đã có sơ đồ.");
        if(source.getCode()==null||!source.getCode().matches("[A-Z0-9-]{3,30}")||(days>1&&source.getCode().length()>21))
            throw new IllegalArgumentException("Mã chuyến có 3–30 ký tự in hoa/số/gạch nối; tạo nhiều ngày dùng tiền tố tối đa 21 ký tự.");
        if(source.getDeparture()==null||source.getArrival()==null||!source.getArrival().isAfter(source.getDeparture()))throw new IllegalArgumentException("Nhập giờ đến sau khởi hành.");
        var vehicle=fleet.vehicle(source.getVehicleId(),false);
        if(!Boolean.TRUE.equals(vehicle.get("HoatDong"))||fleet.seats(source.getVehicleId()).isEmpty())throw new IllegalArgumentException("Phương tiện chưa sẵn sàng hoặc chưa có sơ đồ.");
        var forms=new ArrayList<TripForm>();
        for(int i=0;i<days;i++) {
            var f=copy(source);f.setDeparture(source.getDeparture().plusDays(i));f.setArrival(source.getArrival().plusDays(i));
            f.setCode(days==1?source.getCode():source.getCode()+"-"+f.getDeparture().format(DateTimeFormatter.BASIC_ISO_DATE));forms.add(f);
        }return forms;
    }
    @Transactional(rollbackFor = Exception.class) public List<Integer> create(TripForm source,int days,String actor) {
        var forms=preview(source,days);var ids=new ArrayList<Integer>();
        for(var f:forms){int id=tickets.createTrip(f,actor);tickets.changeSale(id,"DONG_BAN",actor);ids.add(id);}
        return ids;
    }
    public TripForm duplicate(int id) {
        var t=tickets.trip(id);var f=new TripForm();
        f.setCode("COPY-"+UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT));
        f.setVehicleId((Integer)t.get("PhuongTienID"));f.setFrom((String)t.get("NoiDi"));f.setTo((String)t.get("NoiDen"));f.setOperator((String)t.get("DonViVanChuyen"));
        f.setPrice((java.math.BigDecimal)t.get("GiaCoBan"));f.setPickup((String)t.get("DiemDon"));f.setDropoff((String)t.get("DiemTra"));f.setAmenities((String)t.get("TienIch"));f.setDescription((String)t.get("MoTa"));
        f.setChildPrice((java.math.BigDecimal)t.get("GiaTreEm"));f.setSeniorPrice((java.math.BigDecimal)t.get("GiaCaoTuoi"));
        var original=((Timestamp)t.get("KhoiHanh")).toLocalDateTime().plusHours(7);
        var now=tickets.databaseNow().plusHours(7);
        var departure=now.toLocalDate().plusDays(1).atTime(original.toLocalTime());
        f.setDeparture(departure);f.setArrival(departure.plus(Duration.between(original,((Timestamp)t.get("DuKienDen")).toLocalDateTime().plusHours(7))));
        return f;
    }
    private TripForm copy(TripForm s) {
        var f=new TripForm();f.setVehicleId(s.getVehicleId());f.setCode(s.getCode());f.setOperator(s.getOperator());f.setFrom(s.getFrom());f.setTo(s.getTo());
        f.setLayout(s.getLayout());f.setRows(s.getRows());f.setVipSeats(s.getVipSeats());f.setVipExtra(s.getVipExtra());f.setPrice(s.getPrice());f.setChildPrice(s.getChildPrice());f.setSeniorPrice(s.getSeniorPrice());f.setPickup(s.getPickup());f.setDropoff(s.getDropoff());f.setAmenities(s.getAmenities());f.setDescription(s.getDescription());return f;
    }
}
