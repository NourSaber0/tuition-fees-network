package com.tuitionnetwork.payments.repository;

import com.tuitionnetwork.payments.domain.PaymentAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface PaymentAllocationRepository extends JpaRepository<PaymentAllocation, UUID> {
    List<PaymentAllocation> findByFeeLineId(UUID feeLineId);

    List<PaymentAllocation> findByPaymentId(UUID paymentId);

    @Query("select pa from PaymentAllocation pa where pa.feeLine.institutionId = :institutionId order by pa.payment.createdAt desc")
    List<PaymentAllocation> findByInstitutionId(@Param("institutionId") UUID institutionId);

    @Query("select count(pa) > 0 from PaymentAllocation pa where pa.payment.id = :paymentId and pa.feeLine.institutionId = :institutionId")
    boolean existsByPaymentIdAndInstitutionId(@Param("paymentId") UUID paymentId, @Param("institutionId") UUID institutionId);
}
