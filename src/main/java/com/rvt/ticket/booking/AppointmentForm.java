package com.rvt.ticket.booking;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/** DTO nhận dữ liệu từ form đặt lịch hẹn tư vấn. */
public record AppointmentForm(
        @NotBlank @Pattern(regexp = "XE_KHACH|TAU_THUYEN") String type,
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Pattern(regexp = "[0-9]{10,15}", message = "Số điện thoại phải có 10–15 chữ số") String phone,
        @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        @NotBlank @Pattern(regexp = "[0-2][0-9]:[0-5][0-9]") String slot,
        @Size(max = 500) String note,
        @Size(max = 30) String reservationCode) {}
