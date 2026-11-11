package com.rvt.ticket.repository;

import com.rvt.ticket.domain.Reservation;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository extends JpaRepository<Reservation, Integer> {
    Optional<Reservation> findByCode(String code);
    boolean existsByCodeAndAccountId(String code, Integer accountId);
}
