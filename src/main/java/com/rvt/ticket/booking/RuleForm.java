package com.rvt.ticket.booking;

import java.math.BigDecimal;
import jakarta.validation.constraints.*;

public record RuleForm(
    @NotBlank @Pattern(regexp="XE_KHACH|TAU_THUYEN") String type,
    @NotNull @DecimalMin("0") @DecimalMax("40") BigDecimal allowance,
    @NotNull @DecimalMin("0") @DecimalMax("40") BigDecimal maximum,
    @NotNull @DecimalMin("0") @DecimalMax("1000000") BigDecimal rate,
    @Min(1) @Max(168) int cancelHours) {}
