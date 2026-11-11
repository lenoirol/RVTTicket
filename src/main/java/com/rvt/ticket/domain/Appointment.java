package com.rvt.ticket.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Lịch hẹn tư vấn vé. Bảng dbo.LichHenDatCho (migration V11). */
@Entity
@Table(name = "LichHenDatCho")
public class Appointment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "LichHenID") private Integer id;
    @Column(name = "MaLichHen", nullable = false, unique = true, length = 20) private String code;
    @Column(name = "TaiKhoanID", nullable = false) private Integer accountId;
    @Column(name = "LoaiVe", nullable = false, length = 12) private String ticketType;
    @Column(name = "HoTen", nullable = false, length = 100) private String fullName;
    @Column(name = "SoDienThoai", nullable = false, length = 15) private String phone;
    @Column(name = "NgayHen", nullable = false) private LocalDate date;
    @Column(name = "KhungGio", nullable = false, length = 5) private String slot;
    @Column(name = "GhiChu", length = 500) private String note;
    @Column(name = "MaDon", length = 30) private String reservationCode;
    @Column(name = "TrangThai", nullable = false, length = 12) private String status = "MOI";
    @Column(name = "NgayTao", nullable = false) private LocalDateTime createdAt;
    @Column(name = "CapNhatLuc", nullable = false) private LocalDateTime updatedAt;
    @Column(name = "XuLyBoi", length = 60) private String handledBy;

    public Integer getId() { return id; }
    public String getCode() { return code; } public void setCode(String v) { code = v; }
    public Integer getAccountId() { return accountId; } public void setAccountId(Integer v) { accountId = v; }
    public String getTicketType() { return ticketType; } public void setTicketType(String v) { ticketType = v; }
    public String getFullName() { return fullName; } public void setFullName(String v) { fullName = v; }
    public String getPhone() { return phone; } public void setPhone(String v) { phone = v; }
    public LocalDate getDate() { return date; } public void setDate(LocalDate v) { date = v; }
    public String getSlot() { return slot; } public void setSlot(String v) { slot = v; }
    public String getNote() { return note; } public void setNote(String v) { note = v; }
    public String getReservationCode() { return reservationCode; } public void setReservationCode(String v) { reservationCode = v; }
    public String getStatus() { return status; } public void setStatus(String v) { status = v; }
    public LocalDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(LocalDateTime v) { createdAt = v; }
    public LocalDateTime getUpdatedAt() { return updatedAt; } public void setUpdatedAt(LocalDateTime v) { updatedAt = v; }
    public String getHandledBy() { return handledBy; } public void setHandledBy(String v) { handledBy = v; }
}
