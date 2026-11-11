package com.rvt.ticket.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.annotations.Immutable;

/** Reservations: đơn đặt vé (bảng dbo.DonDatVe), chỉ đọc qua JPA; ghi bằng ReservationService/TicketService trong transaction. */
@Entity
@Immutable
@Table(name = "DonDatVe")
public class Reservation {
    @Id @Column(name = "DonDatVeID") private Integer id;
    @Column(name = "MaDon") private String code;
    @Column(name = "TaiKhoanID") private Integer accountId;
    @Column(name = "ChuyenDiID") private Integer tripId;
    @Column(name = "TongTien") private BigDecimal total;
    @Column(name = "TrangThai") private String status;
    @Column(name = "NgayTao") private LocalDateTime createdAt;
    @Column(name = "HanThanhToan") private LocalDateTime payBy;
    public Integer getId() { return id; }
    public String getCode() { return code; }
    public Integer getAccountId() { return accountId; }
    public Integer getTripId() { return tripId; }
    public BigDecimal getTotal() { return total; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getPayBy() { return payBy; }
}
