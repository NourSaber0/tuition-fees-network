package com.tuitionnetwork.billing.repository;

import com.tuitionnetwork.billing.domain.FeeLine;
import com.tuitionnetwork.billing.domain.FeeStatus;
import com.tuitionnetwork.billing.domain.FeeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FeeLineRepository extends JpaRepository<FeeLine, UUID> {

    List<FeeLine> findByStudentIdInAndStatusNot(List<UUID> studentIds, FeeStatus status);

    List<FeeLine> findByStudentIdAndStatusNot(UUID studentId, FeeStatus status);

    List<FeeLine> findByStudentId(UUID studentId);

    List<FeeLine> findByInstitutionId(UUID institutionId);

    List<FeeLine> findByInstitutionIdAndStudentId(UUID institutionId, UUID studentId);

    Optional<FeeLine> findByInstitutionIdAndStudentIdAndFeeTypeAndCollectionPeriod(
            UUID institutionId, UUID studentId, FeeType feeType, String collectionPeriod);

    Optional<FeeLine> findByRowIdempotencyKey(String rowIdempotencyKey);

    List<FeeLine> findByRemainingAmountGreaterThanAndStatusNot(BigDecimal amount, FeeStatus status);
}
