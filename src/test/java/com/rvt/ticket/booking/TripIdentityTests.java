package com.rvt.ticket.booking;

import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TripIdentityTests {
    @Test void importedTripCodesHaveStableAliasesAndOtherCodesAreUntouched(){
        assertEquals("RVT-B7",TripIdentity.code(Map.of("MaChuyen","RVT-DEMO-1-22","LoaiVe","XE_KHACH","ChuyenDiID",7)));
        assertEquals("RVT-T15",TripIdentity.code(Map.of("MaChuyen","RVT-SEA-RG-PQ-1-0","LoaiVe","TAU_THUYEN","ChuyenDiID",15)));
        assertEquals("XK001",TripIdentity.code(Map.of("MaChuyen","XK001","LoaiVe","XE_KHACH","ChuyenDiID",1)));
    }
    @Test void sqlAliasIsOnlyAnIdentifier(){
        assertThrows(IllegalArgumentException.class,()->TripIdentity.sql("c;DROP"));assertThrows(IllegalArgumentException.class,()->TripReadiness.configured("c;DROP"));
    }
    @Test void manualFormsCannotImpersonateImportedReferenceCodeNamespaces(){
        try(var factory=jakarta.validation.Validation.buildDefaultValidatorFactory()){
            var validator=factory.getValidator();var form=new TripForm();
            for(String code:java.util.List.of("RVT-DEMO-7-8","RVT-SEA-VT-CD-8")){
                form.setCode(code);assertTrue(validator.validate(form).stream().anyMatch(v->v.getPropertyPath().toString().equals("code")));
            }
            form.setCode("RVT-VT-CD-001");assertTrue(validator.validate(form).stream().noneMatch(v->v.getPropertyPath().toString().equals("code")));
        }
    }
}
