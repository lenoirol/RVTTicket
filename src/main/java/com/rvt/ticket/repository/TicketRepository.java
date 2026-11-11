package com.rvt.ticket.repository;

import com.rvt.ticket.domain.Ticket;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketRepository extends JpaRepository<Ticket, Integer> {
    @Query(value = "SELECT v.* FROM DangKyVe v JOIN DonDatVeChiTiet d ON d.DangKyID=v.DangKyID WHERE d.DonDatVeID=:reservationId ORDER BY v.DangKyID", nativeQuery = true)
    List<Ticket> findByReservationId(@Param("reservationId") int reservationId);
}
