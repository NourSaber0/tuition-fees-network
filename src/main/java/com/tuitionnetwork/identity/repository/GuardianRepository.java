package com.tuitionnetwork.identity.repository;

import com.tuitionnetwork.identity.domain.Guardian;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface GuardianRepository extends JpaRepository<Guardian, UUID> {
    Optional<Guardian> findByNationalIdHash(String nationalIdHash);
    Optional<Guardian> findByEmail(String email);
}
