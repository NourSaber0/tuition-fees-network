package com.tuitionnetwork.identity.repository;

import com.tuitionnetwork.identity.domain.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StudentRepository extends JpaRepository<Student, UUID> {
    Optional<Student> findByNationalIdHash(String nationalIdHash);
    List<Student> findByGuardianId(UUID guardianId);
    List<Student> findByInstitutionId(UUID institutionId);
}
