package com.rvt.ticket.strategy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Service Context: chọn Strategy theo mã loại vé (XE_KHACH, TAU_THUYEN). */
@Component
public class TicketStrategyRegistry {
    private final Map<String, TicketStrategy> byType = new LinkedHashMap<>();

    public TicketStrategyRegistry(List<TicketStrategy> strategies) {
        for (var s : strategies) byType.put(s.type(), s);
    }
    public TicketStrategy of(String type) {
        var s = byType.get(type);
        if (s == null) throw new IllegalArgumentException("Loại vé không hợp lệ.");
        return s;
    }
    public List<TicketStrategy> all() { return List.copyOf(byType.values()); }
}
