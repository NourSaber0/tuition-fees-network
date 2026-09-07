package com.tuitionnetwork.settings.repository;

import com.tuitionnetwork.settings.domain.FeeTypeSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FeeTypeSettingRepository extends JpaRepository<FeeTypeSetting, UUID> {

    Optional<FeeTypeSetting> findByCodeIgnoreCase(String code);

    List<FeeTypeSetting> findAllByOrderByNameAsc();
}
