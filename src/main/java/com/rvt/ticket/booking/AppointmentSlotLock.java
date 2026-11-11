package com.rvt.ticket.booking;

import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Khóa theo khung giờ trong transaction (sp_getapplock, LockOwner=Transaction) để hai khách không cùng
 * lấy lượt hẹn cuối cùng. Chỉ có tác dụng trên SQL Server; môi trường khác (H2 kiểm thử) bỏ qua.
 */
@Component
public class AppointmentSlotLock {
    private final JdbcTemplate db;
    private final boolean sqlServer;

    public AppointmentSlotLock(JdbcTemplate db, DataSource dataSource) {
        this.db = db;
        boolean detected;
        try (var c = dataSource.getConnection()) {
            detected = c.getMetaData().getDatabaseProductName().toLowerCase().contains("sql server");
        } catch (Exception e) { detected = false; }
        this.sqlServer = detected;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void acquire(String resource) {
        if (!sqlServer) return;
        Integer result = db.queryForObject("""
            DECLARE @r INT; EXEC @r = sp_getapplock @Resource=?, @LockMode='Exclusive', @LockOwner='Transaction', @LockTimeout=5000;
            SELECT @r
            """, Integer.class, resource);
        if (result == null || result < 0) throw new IllegalStateException("Hệ thống đang bận xử lý lịch hẹn. Vui lòng thử lại.");
    }
}
