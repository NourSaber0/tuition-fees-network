package com.tuitionnetwork.identity.repository;

import com.tuitionnetwork.identity.domain.BankEmployee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BankEmployeeRepository extends JpaRepository<BankEmployee, UUID> {
    Optional<BankEmployee> findByEmail(String email);
    Optional<BankEmployee> findByEmployeeId(String employeeId);
}
