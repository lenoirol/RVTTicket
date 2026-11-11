package com.rvt.ticket.repository;

import com.rvt.ticket.domain.Appointment;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppointmentRepository extends JpaRepository<Appointment, Integer> {
    Optional<Appointment> findByCode(String code);
    List<Appointment> findByAccountIdOrderByDateDescSlotDesc(Integer accountId);
    List<Appointment> findByStatusInOrderByDateAscSlotAsc(Collection<String> statuses);
    List<Appointment> findAllByOrderByDateDescSlotDesc();
    long countByTicketTypeAndDateAndSlotAndStatusNot(String ticketType, LocalDate date, String slot, String status);
    long countByAccountIdAndStatusIn(Integer accountId, Collection<String> statuses);
    boolean existsByAccountIdAndTicketTypeAndDateAndSlotAndStatusNot(Integer accountId, String ticketType, LocalDate date, String slot, String status);
}
