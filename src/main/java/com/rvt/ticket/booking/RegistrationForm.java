package com.rvt.ticket.booking;

import jakarta.validation.constraints.*;

public record RegistrationForm(
    @NotBlank @Pattern(regexp="[a-z0-9_]{4,40}",message="Tên đăng nhập có 4–40 ký tự thường, số hoặc dấu gạch dưới") String username,
    @NotBlank @Size(min=10,max=64,message="Mật khẩu có 10–64 ký tự") String password,
    @NotBlank @Size(max=100) String name,
    @NotBlank @Pattern(regexp="[0-9]{10,15}") String phone) {}
