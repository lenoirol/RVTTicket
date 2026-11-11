package com.rvt.ticket.strategy;

import com.rvt.ticket.booking.BookingForm;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Strategy Pattern cho tầng nghiệp vụ: mỗi loại vé (xe khách, tàu/thuyền) có quy trình,
 * quy tắc validation, chính sách phí và quy tắc lịch hẹn riêng. Service chỉ làm việc qua
 * interface này nên thêm loại vé mới không phải sửa logic cũ (Open/Closed Principle).
 */
public interface TicketStrategy {
    /** Mã loại vé lưu trong cột LoaiVe: XE_KHACH hoặc TAU_THUYEN. */
    String type();
    String displayName();

    /** Giới hạn hành lý tuyệt đối mỗi hành khách (kg), không cho quản trị đặt vượt. */
    BigDecimal absoluteLuggageLimit();
    /** Số phút nên có mặt trước giờ khởi hành. */
    int boardingLeadMinutes();

    /** Kiểm tra toàn đơn (tuổi, người đi cùng...) trước khi xử lý từng hành khách. */
    void validateOrder(List<BookingForm.Passenger> passengers, LocalDate travelDate, LocalDate todayVn);
    /** Kiểm tra giấy tờ của từng hành khách. */
    void validateDocument(BookingForm.Passenger passenger);
    /** Nhóm giá: NGUOI_LON, TRE_EM hoặc CAO_TUOI. */
    String fareGroup(BookingForm.Passenger passenger, LocalDate travelDate);
    /** Kiểm tra danh tính sau khi biết nhóm giá. */
    void validateIdentity(BookingForm.Passenger passenger, String fareGroup);
    /** Loại giấy tờ lưu trên vé; null nếu loại vé này không lưu. */
    String storedDocumentType(BookingForm.Passenger passenger);
    /** Ghi bản ghi chi tiết riêng của loại vé (VeXeKhach hoặc VeTauThuyen). */
    void insertDetails(JdbcTemplate db, int ticketId, BookingForm.Passenger passenger, Map<String, Object> tripProfile);

    /** Phí hành lý theo định mức từ bảng QuyTacVe. */
    BigDecimal luggageFee(BigDecimal kg, BigDecimal allowance, BigDecimal maximum, BigDecimal rate);

    // ---- Lịch hẹn tư vấn (Workflow 4) ----
    /** Các khung giờ tư vấn trong ngày. */
    List<LocalTime> appointmentSlots();
    /** Số lượt hẹn tối đa mỗi khung giờ cho loại vé này. */
    int appointmentCapacityPerSlot();
    /** Kiểm tra ngày/giờ hẹn theo quy tắc riêng của loại vé. */
    void validateAppointment(LocalDate date, LocalTime slot, LocalDateTime nowVn);
}
