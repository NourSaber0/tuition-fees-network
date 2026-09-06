package com.tuitionnetwork.billing.service;

import com.tuitionnetwork.billing.domain.FeeLine;
import com.tuitionnetwork.billing.domain.FeeStatus;
import com.tuitionnetwork.billing.domain.FeeType;
import com.tuitionnetwork.billing.dto.StudentFeeLineDto;
import com.tuitionnetwork.billing.repository.FeeLineRepository;
import com.tuitionnetwork.identity.domain.Student;
import com.tuitionnetwork.identity.repository.StudentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class BillingFeeQueryServiceImpl implements BillingFeeQueryService {

    private final FeeLineRepository feeLineRepository;
    private final StudentRepository studentRepository;

    @Autowired
    public BillingFeeQueryServiceImpl(FeeLineRepository feeLineRepository,
                                     @Autowired(required = false) StudentRepository studentRepository) {
        this.feeLineRepository = feeLineRepository;
        this.studentRepository = studentRepository;
    }

    public BillingFeeQueryServiceImpl(FeeLineRepository feeLineRepository) {
        this(feeLineRepository, null);
    }

    @Override
    public List<StudentFeeLineDto> findOpenFeesByStudentIds(List<UUID> studentIds) {
        if (studentIds == null || studentIds.isEmpty()) {
            return List.of();
        }

        List<FeeLine> feeLines = feeLineRepository.findByStudentIdInAndStatusNot(studentIds, FeeStatus.PAID);
        return feeLines.stream()
                .filter(f -> f.getStatus() != FeeStatus.CANCELLED)
                .map(this::mapToDto)
                .toList();
    }

    @Override
    public List<StudentFeeLineDto> findOpenFeesByStudentId(UUID studentId) {
        if (studentId == null) {
            return List.of();
        }
        return findOpenFeesByStudentIds(List.of(studentId));
    }

    @Override
    public List<StudentFeeLineDto> findOpenFeesByGuardianId(UUID guardianId) {
        if (guardianId == null) {
            return List.of();
        }
        if (studentRepository != null) {
            List<Student> students = studentRepository.findByGuardianId(guardianId);
            if (students == null || students.isEmpty()) {
                return List.of();
            }
            List<UUID> studentIds = students.stream().map(Student::getId).toList();
            return findOpenFeesByStudentIds(studentIds);
        }
        return List.of();
    }

    @Override
    public List<StudentFeeLineDto> findFeesByInstitution(UUID institutionId) {
        if (institutionId == null) {
            return List.of();
        }
        return feeLineRepository.findByInstitutionId(institutionId).stream()
                .map(this::mapToDto)
                .toList();
    }

    @Override
    public List<StudentFeeLineDto> findFeesByInstitutionAndStudent(UUID institutionId, UUID studentId) {
        if (institutionId == null || studentId == null) {
            return List.of();
        }
        return feeLineRepository.findByInstitutionIdAndStudentId(institutionId, studentId).stream()
                .map(this::mapToDto)
                .toList();
    }

    private StudentFeeLineDto mapToDto(FeeLine feeLine) {
        return new StudentFeeLineDto(
                feeLine.getId(),
                feeLine.getStudentId(),
                feeLine.getInstitutionId(),
                feeLine.getFeeType() != null ? feeLine.getFeeType().getDisplayName() : "Tuition",
                feeLine.getCollectionPeriod(),
                feeLine.getTotalAmount(),
                feeLine.getPaidAmount() != null ? feeLine.getPaidAmount() : BigDecimal.ZERO,
                feeLine.getRemainingAmount(),
                feeLine.getCurrency(),
                feeLine.getStatus().name(),
                feeLine.getDueDate()
        );
    }
}
