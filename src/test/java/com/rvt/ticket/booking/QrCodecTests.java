package com.rvt.ticket.booking;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QrCodecTests {
    private final QrService qr=new QrService(null,null);
    @Test void exportedImagesRoundTripAcrossDifferentQrPatterns(){
        var random=new Random(20261003);
        for(int i=0;i<80;i++){byte[] bytes=new byte[32];random.nextBytes(bytes);String token=HexFormat.of().formatHex(bytes).toUpperCase(Locale.ROOT);assertEquals(token,qr.decode(qr.image(token)));}
        for(String token:List.of("0".repeat(64),"F".repeat(64),"1234567890ABCDEF".repeat(4)))assertEquals(token,qr.decode(qr.image(token)));
    }
    @Test void invalidOrForeignPayloadsAreRejected(){
        assertThrows(IllegalArgumentException.class,()->qr.token("https://example.com"));
        assertThrows(IllegalArgumentException.class,()->qr.decode(new byte[0]));
        assertThrows(IllegalArgumentException.class,()->qr.decode(new byte[4*1024*1024+1]));
        assertThrows(IllegalArgumentException.class,()->qr.decode(new byte[]{1,2,3}));
    }
}
