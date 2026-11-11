package com.rvt.ticket.booking;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class StateMachineTests {
    @Test void reservationHappyPath() {
        assertTrue(ReservationState.CREATED.canMoveTo(ReservationState.CONFIRMED));
        assertTrue(ReservationState.CONFIRMED.canMoveTo(ReservationState.USED));
    }

    @Test void reservationRejectsIllegalMoves() {
        assertFalse(ReservationState.CANCELLED.canMoveTo(ReservationState.CONFIRMED));
        assertFalse(ReservationState.EXPIRED.canMoveTo(ReservationState.CONFIRMED));
        assertFalse(ReservationState.CREATED.canMoveTo(ReservationState.USED));
        assertThrows(IllegalArgumentException.class, () -> ReservationState.require("DA_HUY", ReservationState.CONFIRMED));
        assertDoesNotThrow(() -> ReservationState.require("GIU_CHO", ReservationState.CONFIRMED));
    }

    @Test void reservationCodesRoundTrip() {
        for (var s : ReservationState.values()) assertEquals(s, ReservationState.fromCode(s.code()));
        assertThrows(IllegalArgumentException.class, () -> ReservationState.fromCode("XYZ"));
    }

    @Test void appointmentLifecycle() {
        assertTrue(AppointmentState.NEW.canMoveTo(AppointmentState.CONFIRMED));
        assertTrue(AppointmentState.CONFIRMED.canMoveTo(AppointmentState.DONE));
        assertTrue(AppointmentState.NEW.canMoveTo(AppointmentState.CANCELLED));
        assertFalse(AppointmentState.DONE.canMoveTo(AppointmentState.CANCELLED));
        assertFalse(AppointmentState.CANCELLED.canMoveTo(AppointmentState.CONFIRMED));
        assertFalse(AppointmentState.NEW.canMoveTo(AppointmentState.DONE));
    }
}
