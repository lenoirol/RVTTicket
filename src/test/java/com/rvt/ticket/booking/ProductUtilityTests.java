package com.rvt.ticket.booking;

import com.rvt.ticket.config.RequestThrottle;
import com.rvt.ticket.web.JourneyFilesController;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProductUtilityTests {
    @Test void manifestEscapesSpreadsheetFormulasAndQuotes(){
        assertEquals("\"'=SUM(1,2)\"",JourneyFilesController.csvCell("=SUM(1,2)"));
        assertEquals("\"'  +cmd\"",JourneyFilesController.csvCell("  +cmd"));
        assertEquals("\"Nguyễn \"\"An\"\"\"",JourneyFilesController.csvCell("Nguyễn \"An\""));
        assertEquals("\"hai dòng\"",JourneyFilesController.csvCell("hai\ndòng"));
        assertEquals("\"\"",JourneyFilesController.csvCell(null));
    }
    @Test void throttleSeparatesAddressesAndActions(){
        var throttle=new RequestThrottle(Clock.fixed(Instant.parse("2026-10-03T10:00:00Z"),ZoneOffset.UTC));
        for(int i=0;i<30;i++)assertTrue(throttle.allow("device-one","/login"));
        assertFalse(throttle.allow("device-one","/login"));assertTrue(throttle.allow("device-two","/login"));assertTrue(throttle.allow("device-one","/tra-cuu"));
    }
    @Test void throttleRecoversAfterWindow(){
        class MutableClock extends Clock{Instant instant=Instant.parse("2026-10-03T10:00:00Z");public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return instant;}}
        var clock=new MutableClock();var throttle=new RequestThrottle(clock);
        for(int i=0;i<15;i++)assertTrue(throttle.allow("device","/dang-ky"));assertFalse(throttle.allow("device","/dang-ky"));clock.instant=clock.instant.plusSeconds(301);assertTrue(throttle.allow("device","/dang-ky"));
    }
}
