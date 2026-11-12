package com.rvt.ticket.strategy;

import com.rvt.ticket.booking.BookingForm;
import com.rvt.ticket.booking.FarePolicy;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Vé xe khách: tuyến nội tỉnh/liên tỉnh, một giá người lớn, hành lý tối đa 30 kg, không cần giấy tờ. */
@Component
public class BusTicketStrategy implements TicketStrategy {
    private static final List<LocalTime> SLOTS = List.of(LocalTime.of(8, 0), LocalTime.of(9, 0), LocalTime.of(10, 0),
            LocalTime.of(14, 0), LocalTime.of(15, 0), LocalTime.of(16, 0));

    @Override public String type() { return "XE_KHACH"; }
    @Override public String displayName() { return "Vé xe khách"; }
    @Override public BigDecimal absoluteLuggageLimit() { return BigDecimal.valueOf(30); }
    @Override public int boardingLeadMinutes() { return 30; }

    @Override public void validateOrder(List<BookingForm.Passenger> passengers, LocalDate travelDate, LocalDate todayVn) { }
    @Override public void validateDocument(BookingForm.Passenger p) { }
    @Override public String fareGroup(BookingForm.Passenger p, LocalDate travelDate) { return "NGUOI_LON"; }
    @Override public void validateIdentity(BookingForm.Passenger p, String fareGroup) { }
    @Override public String storedDocumentType(BookingForm.Passenger p) { return null; }

    @Override public void insertDetails(JdbcTemplate db, int ticketId, BookingForm.Passenger p, Map<String, Object> profile) {
        db.update("INSERT dbo.VeXeKhach(DangKyID,DiemDon,DiemTra,KhoiLuongHanhLy) VALUES(?,?,?,?)",
                ticketId, profile.get("DiemDon"), profile.get("DiemTra"), p.getLuggage());
    }

    @Override public BigDecimal luggageFee(BigDecimal kg, BigDecimal allowance, BigDecimal maximum, BigDecimal rate) {
        return FarePolicy.luggageFee(kg, allowance, maximum, rate);
    }

    /** Phí hủy theo mốc thời gian trước giờ khởi hành. */
    private static final List<CancellationTier> CANCELLATION = List.of(new CancellationTier(24, new BigDecimal("0.10")), new CancellationTier(4, new BigDecimal("0.25")));
    @Override public List<CancellationTier> cancellationTiers() { return CANCELLATION; }
    @Override public int exchangeMinHours() { return 6; }

    @Override public List<LocalTime> appointmentSlots() { return SLOTS; }
    @Override public int appointmentCapacityPerSlot() { return 4; }

    /** Xe khách: đặt lịch cùng ngày được (cách ít nhất 2 giờ), tối đa 14 ngày tới, không làm việc Chủ nhật. */
    @Override public void validateAppointment(LocalDate date, LocalTime slot, LocalDateTime nowVn) {
        if (date.getDayOfWeek() == DayOfWeek.SUNDAY)
            throw new IllegalArgumentException("Văn phòng tư vấn vé xe khách không làm việc Chủ nhật.");
        if (!LocalDateTime.of(date, slot).isAfter(nowVn.plusHours(2)))
            throw new IllegalArgumentException("Lịch hẹn vé xe khách phải cách thời điểm hiện tại ít nhất 2 giờ.");
        if (date.isAfter(nowVn.toLocalDate().plusDays(14)))
            throw new IllegalArgumentException("Lịch hẹn vé xe khách chỉ nhận trong 14 ngày tới.");
    }
}
