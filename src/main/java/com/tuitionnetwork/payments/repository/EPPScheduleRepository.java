package com.tuitionnetwork.payments.repository;

import com.tuitionnetwork.payments.domain.EPPSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EPPScheduleRepository extends JpaRepository<EPPSchedule, UUID> {

    Optional<EPPSchedule> findByPaymentId(UUID paymentId);
}
