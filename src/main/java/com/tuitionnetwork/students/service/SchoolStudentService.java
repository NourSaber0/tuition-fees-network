package com.tuitionnetwork.students.service;

import com.tuitionnetwork.common.dto.PageResponse;
import com.tuitionnetwork.students.dto.EnrollStudentRequest;
import com.tuitionnetwork.students.dto.LinkGuardianRequest;
import com.tuitionnetwork.students.dto.StudentDetailDto;
import com.tuitionnetwork.students.dto.StudentFeesResponse;
import com.tuitionnetwork.students.dto.StudentGuardianDto;
import com.tuitionnetwork.students.dto.StudentPaymentsResponse;
import com.tuitionnetwork.students.dto.StudentSearchDto;
import com.tuitionnetwork.students.dto.StudentStatementResponse;
import com.tuitionnetwork.students.dto.StudentSummaryDto;
import com.tuitionnetwork.students.dto.UpdateStudentRequest;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface SchoolStudentService {

    PageResponse<StudentSummaryDto> getStudents(UUID institutionId, String search, String grade, int page, int pageSize);

    PageResponse<StudentSummaryDto> getDeactivatedStudents(UUID institutionId, String search, LocalDate deactivatedFrom, LocalDate deactivatedTo, int page, int pageSize);

    StudentDetailDto getStudentById(UUID institutionId, UUID studentId);

    StudentDetailDto enrollStudent(UUID institutionId, EnrollStudentRequest request, UUID actorId);

    StudentDetailDto updateStudent(UUID institutionId, UUID studentId, UpdateStudentRequest request, UUID actorId);

    StudentSummaryDto deactivateStudent(UUID institutionId, UUID studentId, String reason, UUID actorId);

    StudentSummaryDto reactivateStudent(UUID institutionId, UUID studentId, UUID actorId);

    StudentFeesResponse getStudentFees(UUID institutionId, UUID studentId);

    StudentPaymentsResponse getStudentPayments(UUID institutionId, UUID studentId, LocalDate dateFrom, LocalDate dateTo);

    List<StudentSearchDto> searchActiveStudents(UUID institutionId, String query);

    List<StudentGuardianDto> getStudentGuardians(UUID institutionId, UUID studentId);

    StudentGuardianDto linkGuardian(UUID institutionId, UUID studentId, LinkGuardianRequest request, UUID actorId);

    void unlinkGuardian(UUID institutionId, UUID studentId, UUID guardianId, UUID actorId);

    StudentStatementResponse getStudentStatement(UUID institutionId, UUID studentId);
}
