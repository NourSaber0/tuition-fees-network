package com.tuitionnetwork.identity.repository;

import com.tuitionnetwork.identity.domain.AccountStatus;
import com.tuitionnetwork.identity.domain.Institution;
import com.tuitionnetwork.identity.domain.InstitutionType;
import com.tuitionnetwork.identity.domain.RegistrationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InstitutionRepository extends JpaRepository<Institution, UUID> {

    Optional<Institution> findByCode(String code);

    Optional<Institution> findByRegistrationNumber(String registrationNumber);

    long countByInstitutionType(InstitutionType institutionType);

    List<Institution> findByNameContainingIgnoreCase(String name);

    /**
     * Paged institution search for the back-office list view (US-05 .. US-07).
     * Every filter is optional — a null argument disables that predicate.
     * {@code search} matches name, code, city or registration number (case-insensitive).
     */
    @Query("""
            SELECT i FROM Institution i
            WHERE (:search IS NULL
                   OR LOWER(i.name) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(i.code) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(COALESCE(i.city, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(COALESCE(i.registrationNumber, '')) LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:type IS NULL OR i.institutionType = :type)
              AND (:registrationStatus IS NULL OR i.registrationStatus = :registrationStatus)
              AND (:accountStatus IS NULL OR i.accountStatus = :accountStatus)
            """)
    Page<Institution> search(@Param("search") String search,
                             @Param("type") InstitutionType type,
                             @Param("registrationStatus") RegistrationStatus registrationStatus,
                             @Param("accountStatus") AccountStatus accountStatus,
                             Pageable pageable);
}
