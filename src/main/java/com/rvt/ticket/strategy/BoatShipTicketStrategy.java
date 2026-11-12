package com.rvt.ticket.strategy;

import com.rvt.ticket.booking.BookingForm;
import com.rvt.ticket.booking.FarePolicy;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Period;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Vé tàu/thuyền: bắt buộc giấy tờ và quốc tịch, giá riêng cho trẻ em và người cao tuổi,
 * trẻ dưới 12 tuổi phải đi cùng người từ 18 tuổi, hành lý tối đa 40 kg, có mặt tại cảng trước 45 phút.
 */
@Component
public class BoatShipTicketStrategy implements TicketStrategy {
    private static final List<LocalTime> SLOTS = List.of(LocalTime.of(8, 30), LocalTime.of(9, 30), LocalTime.of(10, 30),
            LocalTime.of(14, 30), LocalTime.of(15, 30));

    @Override public String type() { return "TAU_THUYEN"; }
    @Override public String displayName() { return "Vé tàu / thuyền"; }
    @Override public BigDecimal absoluteLuggageLimit() { return BigDecimal.valueOf(40); }
    @Override public int boardingLeadMinutes() { return 45; }

    @Override public void validateOrder(List<BookingForm.Passenger> passengers, LocalDate travelDate, LocalDate todayVn) {
        var groups = passengers.stream().map(p -> FarePolicy.group(p.getBirthDate(), travelDate)).toList();
        if (passengers.stream().anyMatch(p -> p.getBirthDate().isAfter(todayVn)))
            throw new IllegalArgumentException("Ngày sinh không được ở tương lai.");
        boolean child = groups.contains("TRE_EM");
        boolean adult = passengers.stream().anyMatch(p -> Period.between(p.getBirthDate(), travelDate).getYears() >= 18);
        if (child && !adult)
            throw new IllegalArgumentException("Trẻ dưới 12 tuổi cần một người từ 18 tuổi trở lên đi cùng trong đơn.");
    }

    @Override public void validateDocument(BookingForm.Passenger p) {
        FarePolicy.validateDocument(type(), p.getDocumentType(), p.getDocument());
    }

    @Override public String fareGroup(BookingForm.Passenger p, LocalDate travelDate) {
        return FarePolicy.group(p.getBirthDate(), travelDate);
    }

    @Override public void validateIdentity(BookingForm.Passenger p, String fareGroup) {
        if (!Set.of("CCCD", "HO_CHIEU", "KHAI_SINH").contains(p.getDocumentType()) || p.getNationality() == null
                || p.getNationality().isBlank() || p.getNationality().length() > 50)
            throw new IllegalArgumentException("Kiểm tra loại giấy tờ và quốc tịch của hành khách.");
        if ("KHAI_SINH".equals(p.getDocumentType()) && !"TRE_EM".equals(fareGroup))
            throw new IllegalArgumentException("Hành khách từ 12 tuổi dùng CCCD hoặc hộ chiếu.");
    }

    @Override public String storedDocumentType(BookingForm.Passenger p) { return p.getDocumentType(); }

    @Override public void insertDetails(JdbcTemplate db, int ticketId, BookingForm.Passenger p, Map<String, Object> profile) {
        db.update("INSERT dbo.VeTauThuyen(DangKyID,SoGiayTo,QuocTich,KhoiLuongHanhLy) VALUES(?,?,?,?)",
                ticketId, p.getDocument(), p.getNationality().strip(), p.getLuggage());
    }

    @Override public BigDecimal luggageFee(BigDecimal kg, BigDecimal allowance, BigDecimal maximum, BigDecimal rate) {
        return FarePolicy.luggageFee(kg, allowance, maximum, rate);
    }

    /** Phí hủy theo mốc thời gian trước giờ khởi hành. */
    private static final List<CancellationTier> CANCELLATION = List.of(new CancellationTier(48, new BigDecimal("0.10")), new CancellationTier(24, new BigDecimal("0.20")), new CancellationTier(12, new BigDecimal("0.40")));
    @Override public List<CancellationTier> cancellationTiers() { return CANCELLATION; }
    @Override public int exchangeMinHours() { return 24; }

    @Override public List<LocalTime> appointmentSlots() { return SLOTS; }
    @Override public int appointmentCapacityPerSlot() { return 2; }

    /** Tàu/thuyền: cần đặt trước ít nhất 1 ngày (xét thời tiết, bến cảng), tối đa 30 ngày tới, không làm việc Chủ nhật. */
    @Override public void validateAppointment(LocalDate date, LocalTime slot, LocalDateTime nowVn) {
        if (date.getDayOfWeek() == DayOfWeek.SUNDAY)
            throw new IllegalArgumentException("Văn phòng tư vấn vé tàu / thuyền không làm việc Chủ nhật.");
        if (!date.isAfter(nowVn.toLocalDate()))
            throw new IllegalArgumentException("Lịch hẹn vé tàu / thuyền phải đặt trước ít nhất 1 ngày để kiểm tra điều kiện cảng và thời tiết.");
        if (date.isAfter(nowVn.toLocalDate().plusDays(30)))
            throw new IllegalArgumentException("Lịch hẹn vé tàu / thuyền chỉ nhận trong 30 ngày tới.");
    }
}
