package com.rvt.ticket.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import org.hibernate.annotations.Immutable;

/** Tickets: vé của từng hành khách (bảng dbo.DangKyVe), chỉ đọc qua JPA. */
@Entity
@Immutable
@Table(name = "DangKyVe")
public class Ticket {
    @Id @Column(name = "DangKyID") private Integer id;
    @Column(name = "DangKyCode") private String code;
    @Column(name = "ChuyenDiID") private Integer tripId;
    @Column(name = "LoaiVe") private String ticketType;
    @Column(name = "TenHanhKhach") private String passengerName;
    @Column(name = "MaCho") private String seat;
    @Column(name = "GiaVe") private BigDecimal price;
    @Column(name = "TrangThai") private String status;
    public Integer getId() { return id; }
    public String getCode() { return code; }
    public Integer getTripId() { return tripId; }
    public String getTicketType() { return ticketType; }
    public String getPassengerName() { return passengerName; }
    public String getSeat() { return seat; }
    public BigDecimal getPrice() { return price; }
    public String getStatus() { return status; }
}
