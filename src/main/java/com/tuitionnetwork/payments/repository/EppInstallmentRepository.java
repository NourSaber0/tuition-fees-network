package com.tuitionnetwork.payments.repository;

import com.tuitionnetwork.payments.domain.EPPSchedule;
import com.tuitionnetwork.payments.domain.EppInstallment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EppInstallmentRepository extends JpaRepository<EppInstallment, UUID> {
    List<EppInstallment> findByEppPlan(EPPSchedule eppPlan);
}
