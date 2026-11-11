package com.rvt.ticket.strategy;

import static org.junit.jupiter.api.Assertions.*;

import com.rvt.ticket.booking.BookingForm;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class TicketStrategyTests {
    private final BusTicketStrategy bus = new BusTicketStrategy();
    private final BoatShipTicketStrategy boat = new BoatShipTicketStrategy();
    private final TicketStrategyRegistry registry = new TicketStrategyRegistry(List.of(bus, boat));

    private static BookingForm.Passenger passenger(LocalDate birth) {
        var p = new BookingForm.Passenger();
        p.setBirthDate(birth);
        p.setName("Test");
        p.setSeat("A1");
        return p;
    }

    @Test void registryPicksStrategyByType() {
        assertSame(bus, registry.of("XE_KHACH"));
        assertSame(boat, registry.of("TAU_THUYEN"));
        assertThrows(IllegalArgumentException.class, () -> registry.of("MAY_BAY"));
        assertEquals(2, registry.all().size());
    }

    @Test void luggageLimitsDifferByType() {
        assertEquals(30, bus.absoluteLuggageLimit().intValue());
        assertEquals(40, boat.absoluteLuggageLimit().intValue());
    }

    @Test void busHasSingleAdultFareGroup() {
        assertEquals("NGUOI_LON", bus.fareGroup(passenger(LocalDate.of(2020, 1, 1)), LocalDate.of(2026, 12, 1)));
    }

    @Test void boatRejectsChildWithoutAdult() {
        var travel = LocalDate.of(2026, 12, 1);
        var child = passenger(LocalDate.of(2020, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> boat.validateOrder(List.of(child), travel, LocalDate.of(2026, 11, 10)));
        var adult = passenger(LocalDate.of(1990, 1, 1));
        assertDoesNotThrow(() -> boat.validateOrder(List.of(child, adult), travel, LocalDate.of(2026, 11, 10)));
    }

    @Test void appointmentRulesDifferByType() {
        var now = LocalDateTime.of(2026, 11, 10, 9, 0); // Thứ Ba
        var sameDay = LocalDate.of(2026, 11, 10);
        assertDoesNotThrow(() -> bus.validateAppointment(sameDay, LocalTime.of(14, 0), now));
        assertThrows(IllegalArgumentException.class, () -> boat.validateAppointment(sameDay, LocalTime.of(14, 0), now));
        var sunday = LocalDate.of(2026, 11, 15);
        assertThrows(IllegalArgumentException.class, () -> bus.validateAppointment(sunday, LocalTime.of(9, 0), now));
        assertThrows(IllegalArgumentException.class,
                () -> boat.validateAppointment(LocalDate.of(2026, 12, 30), LocalTime.of(9, 0), now));
    }
}
