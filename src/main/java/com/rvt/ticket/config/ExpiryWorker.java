package com.rvt.ticket.config;

import com.rvt.ticket.booking.TicketService;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component @EnableScheduling @Profile("!test")
public class ExpiryWorker {
    private final JdbcTemplate db;
    private final TicketService service;
    public ExpiryWorker(JdbcTemplate db,TicketService service) {this.db=db;this.service=service;}
    @Scheduled(initialDelay=60000,fixedDelay=60000)
    public void expire() {
        for(Integer id:db.queryForList("SELECT DISTINCT ChuyenDiID FROM dbo.DangKyVe WHERE TrangThai='GIU_CHO' AND HanThanhToan<=SYSUTCDATETIME()",Integer.class)) service.expireTrip(id);
        service.refreshLifecycle();
    }
}
