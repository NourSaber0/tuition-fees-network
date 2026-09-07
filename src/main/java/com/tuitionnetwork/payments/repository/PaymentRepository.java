package com.tuitionnetwork.payments.repository;

import com.tuitionnetwork.payments.domain.Payment;
import com.tuitionnetwork.payments.domain.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID>, JpaSpecificationExecutor<Payment> {

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    Page<Payment> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByStatus(PaymentStatus status);

    long countByStatusIn(Collection<PaymentStatus> statuses);

    long countByCreatedAtBetween(LocalDateTime from, LocalDateTime to);

    long countByStatusAndCreatedAtBetween(PaymentStatus status, LocalDateTime from, LocalDateTime to);

    long countByStatusInAndCreatedAtBetween(Collection<PaymentStatus> statuses, LocalDateTime from, LocalDateTime to);

    @Query("select coalesce(sum(p.totalAmount), 0) from Payment p " +
            "where p.status = :status and p.createdAt between :from and :to")
    BigDecimal sumAmountByStatusAndCreatedAtBetween(@Param("status") PaymentStatus status,
                                                      @Param("from") LocalDateTime from,
                                                      @Param("to") LocalDateTime to);
}
