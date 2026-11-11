package com.rvt.ticket.web;

import com.rvt.ticket.booking.TicketService;
import com.rvt.ticket.repository.ReservationRepository;
import com.rvt.ticket.repository.TransDetailRepository;
import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** REST API: trả chi tiết giao dịch (TransDetails) của một đơn vé dưới dạng JSON. Chỉ chủ đơn được xem. */
@RestController
public class TransactionApiController {
    private final TicketService tickets;
    private final ReservationRepository reservations;
    private final TransDetailRepository transactions;
    public TransactionApiController(TicketService tickets, ReservationRepository reservations, TransDetailRepository transactions) {
        this.tickets = tickets; this.reservations = reservations; this.transactions = transactions;
    }

    @GetMapping("/api/don-ve/{code}/giao-dich")
    ResponseEntity<?> transactions(@PathVariable String code, Principal principal) {
        String normalized = code.strip().toUpperCase();
        try { tickets.ownerPhone(normalized, principal.getName()); }
        catch (IllegalArgumentException e) { return ResponseEntity.status(403).body(Map.of("error", "Bạn không có quyền xem giao dịch của đơn này.")); }
        var reservation = reservations.findByCode(normalized);
        if (reservation.isEmpty()) return ResponseEntity.status(404).body(Map.of("error", "Không tìm thấy đơn."));
        List<Map<String, Object>> items = transactions.findByReservationId(reservation.get().getId()).stream().map(t -> {
            var row = new LinkedHashMap<String, Object>();
            row.put("maGiaoDich", t.getTransactionCode()); row.put("soTien", t.getAmount()); row.put("phuongThuc", t.getMethod());
            row.put("trangThai", t.getStatus()); row.put("thoiGian", t.getPaidAt()); row.put("hoanLuc", t.getRefundedAt());
            return (Map<String, Object>) row;
        }).toList();
        return ResponseEntity.ok(Map.of("maDon", normalized, "trangThaiDon", reservation.get().getStatus(), "giaoDich", items));
    }
}
