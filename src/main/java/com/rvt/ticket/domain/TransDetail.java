package com.rvt.ticket.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.annotations.Immutable;

/** TransDetails: chi tiết giao dịch thanh toán/hoàn tiền (bảng dbo.ThanhToan), chỉ đọc qua JPA. */
@Entity
@Immutable
@Table(name = "ThanhToan")
public class TransDetail {
    @Id @Column(name = "ThanhToanID") private Integer id;
    @Column(name = "DangKyID") private Integer ticketId;
    @Column(name = "MaGiaoDich") private String transactionCode;
    @Column(name = "SoTien") private BigDecimal amount;
    @Column(name = "PhuongThuc") private String method;
    @Column(name = "TrangThai") private String status;
    @Column(name = "ThoiGian") private LocalDateTime paidAt;
    @Column(name = "HoanLuc") private LocalDateTime refundedAt;
    public Integer getId() { return id; }
    public Integer getTicketId() { return ticketId; }
    public String getTransactionCode() { return transactionCode; }
    public BigDecimal getAmount() { return amount; }
    public String getMethod() { return method; }
    public String getStatus() { return status; }
    public LocalDateTime getPaidAt() { return paidAt; }
    public LocalDateTime getRefundedAt() { return refundedAt; }
}
