package com.rvt.ticket.booking;

import java.util.EnumSet;
import java.util.Set;

/** Máy trạng thái lịch hẹn tư vấn: NEW → CONFIRMED → DONE; NEW/CONFIRMED → CANCELLED. */
public enum AppointmentState {
    NEW("MOI"), CONFIRMED("DA_XAC_NHAN"), DONE("HOAN_THANH"), CANCELLED("DA_HUY");

    private final String code;
    AppointmentState(String code) { this.code = code; }
    public String code() { return code; }

    public static AppointmentState fromCode(String code) {
        for (var s : values()) if (s.code.equals(code)) return s;
        throw new IllegalArgumentException("Trạng thái lịch hẹn không hợp lệ.");
    }
    public Set<AppointmentState> next() {
        return switch (this) {
            case NEW -> EnumSet.of(CONFIRMED, CANCELLED);
            case CONFIRMED -> EnumSet.of(DONE, CANCELLED);
            case DONE, CANCELLED -> EnumSet.noneOf(AppointmentState.class);
        };
    }
    public boolean canMoveTo(AppointmentState target) { return next().contains(target); }
}
