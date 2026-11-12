package com.rvt.ticket.strategy;

import java.math.BigDecimal;

/** Một mốc hủy vé: hủy khi còn ít nhất {@code minHoursBefore} giờ trước khởi hành thì thu phí {@code feeRate} trên số tiền đã trả. */
public record CancellationTier(int minHoursBefore, BigDecimal feeRate) {
    public int feePercent() { return feeRate.movePointRight(2).intValue(); }
}
