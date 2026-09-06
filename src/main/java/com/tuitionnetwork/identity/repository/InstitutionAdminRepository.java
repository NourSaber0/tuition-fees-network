package com.tuitionnetwork.identity.repository;

import com.tuitionnetwork.identity.domain.InstitutionAdmin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InstitutionAdminRepository extends JpaRepository<InstitutionAdmin, UUID> {
    Optional<InstitutionAdmin> findByEmail(String email);
    List<InstitutionAdmin> findByInstitutionId(UUID institutionId);
}
