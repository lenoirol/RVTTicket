package com.rvt.ticket.repository;

import com.rvt.ticket.domain.TransDetail;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransDetailRepository extends JpaRepository<TransDetail, Integer> {
    @Query(value = "SELECT p.* FROM ThanhToan p JOIN DonDatVeChiTiet d ON d.DangKyID=p.DangKyID WHERE d.DonDatVeID=:reservationId ORDER BY p.ThanhToanID", nativeQuery = true)
    List<TransDetail> findByReservationId(@Param("reservationId") int reservationId);
}
