package com.rvt.ticket.booking;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PassengerFareTests {
    @Test void birthdayBoundaries(){LocalDate day=LocalDate.of(2026,10,3);assertEquals("TRE_EM",FarePolicy.group(day.minusYears(12).plusDays(1),day));assertEquals("NGUOI_LON",FarePolicy.group(day.minusYears(12),day));assertEquals("NGUOI_LON",FarePolicy.group(day.minusYears(60).plusDays(1),day));assertEquals("CAO_TUOI",FarePolicy.group(day.minusYears(60),day));}
    @Test void rejectInvalidBirthdates(){LocalDate day=LocalDate.of(2026,10,3);assertThrows(IllegalArgumentException.class,()->FarePolicy.group(null,day));assertThrows(IllegalArgumentException.class,()->FarePolicy.group(day.plusDays(1),day));assertThrows(IllegalArgumentException.class,()->FarePolicy.group(day.minusYears(121),day));}
    @Test void fallbackRoundedAndVipFullPrice(){BigDecimal adult=new BigDecimal("315000");assertEquals(0,new BigDecimal("252000").compareTo(FarePolicy.passengerFare("TRE_EM",adult,null,null,false)));assertEquals(adult,FarePolicy.passengerFare("TRE_EM",adult,new BigDecimal("225000"),null,true));}
    @Test void cancellationIncludesExactly24Hours(){var now=LocalDate.of(2026,10,3).atTime(8,30);assertTrue(FarePolicy.refundAllowed(now.plusHours(24),now));assertFalse(FarePolicy.refundAllowed(now.plusHours(24).minusNanos(1),now));assertTrue(FarePolicy.refundAllowed(now.plusHours(24).plusNanos(1),now));}
}
