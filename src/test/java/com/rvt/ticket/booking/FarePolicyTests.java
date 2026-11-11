package com.rvt.ticket.booking;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FarePolicyTests {
    @Test void citizenCardIsExactlyTwelveDigits(){
        assertDoesNotThrow(()->FarePolicy.validateDocument("TAU_THUYEN","CCCD","012345678901"));
        for(String invalid:new String[]{"01234567890","0123456789012","B12345678901","012 45678901",""})
            assertThrows(IllegalArgumentException.class,()->FarePolicy.validateDocument("TAU_THUYEN","CCCD",invalid));
        assertThrows(IllegalArgumentException.class,()->FarePolicy.validateDocument("TAU_THUYEN","CCCD",null));
    }
    @Test void passportAndBirthCertificateKeepTheirOwnFormats(){
        assertDoesNotThrow(()->FarePolicy.validateDocument("TAU_THUYEN","HO_CHIEU","B1234567"));
        assertDoesNotThrow(()->FarePolicy.validateDocument("TAU_THUYEN","KHAI_SINH","123/2020"));
        assertThrows(IllegalArgumentException.class,()->FarePolicy.validateDocument("TAU_THUYEN","INVALID","012345678901"));
    }
    @Test void busAllowanceAndExcess() {
        assertEquals(0,FarePolicy.luggageFee("XE_KHACH",new BigDecimal("20")).signum());
        assertEquals(new BigDecimal("55000.0"),FarePolicy.luggageFee("XE_KHACH",new BigDecimal("25.5")));
        assertThrows(IllegalArgumentException.class,()->FarePolicy.luggageFee("XE_KHACH",new BigDecimal("30.1")));
    }
    @Test void boatHasSeparateRules() {
        assertEquals(0,FarePolicy.luggageFee("TAU_THUYEN",new BigDecimal("25")).signum());
        assertEquals(new BigDecimal("225000"),FarePolicy.luggageFee("TAU_THUYEN",new BigDecimal("40")));
        assertThrows(IllegalArgumentException.class,()->FarePolicy.validateDocument("TAU_THUYEN","12 3456"));
        assertDoesNotThrow(()->FarePolicy.validateDocument("TAU_THUYEN","B1234567"));
        assertDoesNotThrow(()->FarePolicy.validateDocument("XE_KHACH",null));
    }
}
