package com.rvt.ticket.booking;

import com.rvt.ticket.domain.Appointment;
import com.rvt.ticket.repository.AppointmentRepository;
import com.rvt.ticket.repository.ReservationRepository;
import com.rvt.ticket.repository.UserAccountRepository;
import com.rvt.ticket.strategy.TicketStrategyRegistry;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Appointment Booking Workflow (Workflow 4): khách/đại lý đặt lịch hẹn tư vấn vé, nhân viên điều hành xác nhận.
 * Quy tắc ngày giờ và sức chứa mỗi khung giờ do TicketStrategy của từng loại vé quyết định.
 */
@Service
public class AppointmentService {
    public static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");
    private static final Set<String> ACTIVE = Set.of(AppointmentState.NEW.code(), AppointmentState.CONFIRMED.code());

    private final AppointmentRepository appointments;
    private final UserAccountRepository users;
    private final ReservationRepository reservations;
    private final TicketStrategyRegistry strategies;
    private final AppointmentSlotLock lock;
    private final Clock clock;

    @Autowired
    public AppointmentService(AppointmentRepository appointments, UserAccountRepository users, ReservationRepository reservations,
                              TicketStrategyRegistry strategies, AppointmentSlotLock lock) {
        this(appointments, users, reservations, strategies, lock, Clock.system(VN));
    }
    AppointmentService(AppointmentRepository appointments, UserAccountRepository users, ReservationRepository reservations,
                       TicketStrategyRegistry strategies, AppointmentSlotLock lock, Clock clock) {
        this.appointments = appointments; this.users = users; this.reservations = reservations;
        this.strategies = strategies; this.lock = lock; this.clock = clock;
    }

    public record SlotView(String time, int remaining, boolean bookable) {}

    /** Danh sách khung giờ của một loại vé trong một ngày kèm số lượt còn trống. */
    @Transactional(readOnly = true)
    public List<SlotView> slots(String type, LocalDate date) {
        var strategy = strategies.of(type);
        var now = LocalDateTime.now(clock);
        var list = new ArrayList<SlotView>();
        for (LocalTime t : strategy.appointmentSlots()) {
            String slot = t.format(HHMM);
            long used = appointments.countByTicketTypeAndDateAndSlotAndStatusNot(type, date, slot, AppointmentState.CANCELLED.code());
            int remaining = (int) Math.max(0, strategy.appointmentCapacityPerSlot() - used);
            boolean ok;
            try { strategy.validateAppointment(date, t, now); ok = remaining > 0; } catch (IllegalArgumentException e) { ok = false; }
            list.add(new SlotView(slot, remaining, ok));
        }
        return list;
    }

    @Transactional(rollbackFor = Exception.class)
    public Appointment book(String username, AppointmentForm form) {
        var account = users.findByUsername(username).orElseThrow(() -> new IllegalArgumentException("Đăng nhập tài khoản khách hàng trước khi đặt lịch hẹn."));
        if (Boolean.TRUE.equals(account.getLocked())) throw new IllegalArgumentException("Tài khoản đã bị khóa. Vui lòng liên hệ quản trị.");
        var strategy = strategies.of(form.type());
        LocalTime slotTime;
        try { slotTime = LocalTime.parse(form.slot(), HHMM); } catch (RuntimeException e) { throw new IllegalArgumentException("Khung giờ không hợp lệ."); }
        if (!strategy.appointmentSlots().contains(slotTime)) throw new IllegalArgumentException("Khung giờ không nằm trong lịch làm việc của văn phòng tư vấn.");
        strategy.validateAppointment(form.date(), slotTime, LocalDateTime.now(clock));

        boolean agent = "AGENT".equals(account.getRole());
        int limit = agent ? 10 : 3;
        if (appointments.countByAccountIdAndStatusIn(account.getId(), ACTIVE) >= limit)
            throw new IllegalArgumentException(agent ? "Đại lý có tối đa 10 lịch hẹn đang chờ xử lý." : "Mỗi tài khoản có tối đa 3 lịch hẹn đang chờ xử lý. Hãy hủy hoặc đợi lịch cũ hoàn tất.");

        String reservationCode = form.reservationCode() == null || form.reservationCode().isBlank() ? null : form.reservationCode().strip().toUpperCase();
        if (reservationCode != null && !reservations.existsByCodeAndAccountId(reservationCode, account.getId()))
            throw new IllegalArgumentException("Mã đơn không tồn tại hoặc không thuộc tài khoản của bạn.");

        lock.acquire("appointment:" + form.type() + ":" + form.date() + ":" + form.slot());
        String cancelled = AppointmentState.CANCELLED.code();
        if (appointments.existsByAccountIdAndTicketTypeAndDateAndSlotAndStatusNot(account.getId(), form.type(), form.date(), form.slot(), cancelled))
            throw new IllegalArgumentException("Bạn đã có lịch hẹn cùng loại vé trong khung giờ này.");
        if (appointments.countByTicketTypeAndDateAndSlotAndStatusNot(form.type(), form.date(), form.slot(), cancelled) >= strategy.appointmentCapacityPerSlot())
            throw new IllegalArgumentException("Khung giờ này đã hết lượt hẹn. Vui lòng chọn khung giờ khác.");

        var now = LocalDateTime.now(clock.withZone(ZoneOffset.UTC));
        var a = new Appointment();
        a.setCode("LH" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase());
        a.setAccountId(account.getId());
        a.setTicketType(form.type());
        a.setFullName(form.name().strip());
        a.setPhone(form.phone());
        a.setDate(form.date());
        a.setSlot(form.slot());
        a.setNote(form.note() == null || form.note().isBlank() ? null : form.note().strip());
        a.setReservationCode(reservationCode);
        a.setStatus(AppointmentState.NEW.code());
        a.setCreatedAt(now);
        a.setUpdatedAt(now);
        return appointments.save(a);
    }

    @Transactional(readOnly = true)
    public List<Appointment> mine(String username) {
        var account = users.findByUsername(username).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản."));
        return appointments.findByAccountIdOrderByDateDescSlotDesc(account.getId());
    }

    /** Khách hủy lịch của chính mình, chậm nhất 2 giờ trước giờ hẹn. */
    @Transactional(rollbackFor = Exception.class)
    public void cancelByOwner(String username, String code) {
        var account = users.findByUsername(username).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản."));
        var a = appointments.findByCode(code).filter(x -> x.getAccountId().equals(account.getId()))
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy lịch hẹn của bạn."));
        var from = AppointmentState.fromCode(a.getStatus());
        if (!from.canMoveTo(AppointmentState.CANCELLED)) throw new IllegalArgumentException("Lịch hẹn này không còn có thể hủy.");
        var at = LocalDateTime.of(a.getDate(), LocalTime.parse(a.getSlot(), HHMM));
        if (!at.isAfter(LocalDateTime.now(clock).plusHours(2))) throw new IllegalArgumentException("Chỉ hủy lịch hẹn trước giờ hẹn ít nhất 2 giờ.");
        a.setStatus(AppointmentState.CANCELLED.code());
        a.setUpdatedAt(LocalDateTime.now(clock.withZone(ZoneOffset.UTC)));
        appointments.save(a);
    }

    @Transactional(readOnly = true)
    public List<Appointment> forStaff(String status) {
        if (status == null || status.isBlank()) return appointments.findAllByOrderByDateDescSlotDesc();
        AppointmentState.fromCode(status);
        return appointments.findByStatusInOrderByDateAscSlotAsc(List.of(status));
    }

    /** Nhân viên điều hành/quản trị đổi trạng thái theo máy trạng thái: NEW → CONFIRMED → DONE, hoặc hủy. */
    @Transactional(rollbackFor = Exception.class)
    public void staffMove(String code, AppointmentState target, String actor) {
        var a = appointments.findByCode(code).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy lịch hẹn."));
        var from = AppointmentState.fromCode(a.getStatus());
        if (!from.canMoveTo(target)) throw new IllegalArgumentException("Không thể chuyển lịch hẹn từ " + from + " sang " + target + ".");
        a.setStatus(target.code());
        a.setHandledBy(actor);
        a.setUpdatedAt(LocalDateTime.now(clock.withZone(ZoneOffset.UTC)));
        appointments.save(a);
    }
}
