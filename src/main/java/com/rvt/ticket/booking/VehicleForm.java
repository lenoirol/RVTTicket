package com.rvt.ticket.booking;
import jakarta.validation.constraints.*;
public record VehicleForm(
    @NotBlank @Pattern(regexp="[A-Z0-9-]{3,30}") String code,
    @NotBlank @Size(max=100) String name,
    @NotBlank @Size(max=40) String registration,
    @NotBlank @Size(max=100) String operator,
    @Pattern(regexp="XE_KHACH|TAU_THUYEN") @NotNull String type,
    boolean active,
    @NotBlank @Size(max=40000) String layout
) {}
