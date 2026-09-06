package com.tuitionnetwork.billing.service;

import com.tuitionnetwork.billing.dto.StudentFeeLineDto;

import java.util.List;
import java.util.UUID;

public interface BillingFeeQueryService {

    List<StudentFeeLineDto> findOpenFeesByStudentIds(List<UUID> studentIds);

    List<StudentFeeLineDto> findOpenFeesByStudentId(UUID studentId);

    List<StudentFeeLineDto> findOpenFeesByGuardianId(UUID guardianId);

    List<StudentFeeLineDto> findFeesByInstitution(UUID institutionId);

    List<StudentFeeLineDto> findFeesByInstitutionAndStudent(UUID institutionId, UUID studentId);
}
