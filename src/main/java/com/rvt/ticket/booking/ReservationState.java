package com.rvt.ticket.booking;

import java.util.EnumSet;
import java.util.Set;

/**
 * Máy trạng thái vòng đời đơn/vé: CREATED (giữ chỗ) → CONFIRMED (đã thanh toán) → USED (đã lên xe/tàu),
 * hoặc CANCELLED / EXPIRED. Mã lưu trong SQL Server giữ nguyên tiếng Việt để tương thích dữ liệu cũ.
 */
public enum ReservationState {
    CREATED("GIU_CHO"), CONFIRMED("DA_THANH_TOAN"), USED("DA_SU_DUNG"), CANCELLED("DA_HUY"), EXPIRED("HET_HAN");

    private final String code;
    ReservationState(String code) { this.code = code; }
    public String code() { return code; }

    public static ReservationState fromCode(String code) {
        for (var s : values()) if (s.code.equals(code)) return s;
        throw new IllegalArgumentException("Trạng thái đơn không hợp lệ: " + code);
    }

    public Set<ReservationState> next() {
        return switch (this) {
            case CREATED -> EnumSet.of(CONFIRMED, CANCELLED, EXPIRED);
            case CONFIRMED -> EnumSet.of(USED, CANCELLED);
            case USED, CANCELLED, EXPIRED -> EnumSet.noneOf(ReservationState.class);
        };
    }
    public boolean canMoveTo(ReservationState target) { return next().contains(target); }

    /** Ném lỗi nếu chuyển trạng thái trái quy tắc nghiệp vụ. */
    public static void require(Object currentCode, ReservationState target) {
        var current = fromCode((String) currentCode);
        if (!current.canMoveTo(target))
            throw new IllegalArgumentException("Không thể chuyển đơn từ " + current + " sang " + target + ".");
    }
}
