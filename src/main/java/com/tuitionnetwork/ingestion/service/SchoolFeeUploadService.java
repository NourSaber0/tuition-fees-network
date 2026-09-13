package com.tuitionnetwork.ingestion.service;

import com.tuitionnetwork.ingestion.dto.FeeUploadDetailDto;
import com.tuitionnetwork.ingestion.dto.FeeUploadResponseDto;
import com.tuitionnetwork.ingestion.dto.FeeUploadRowDto;
import com.tuitionnetwork.ingestion.dto.FeeUploadSummaryDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface SchoolFeeUploadService {

    String getCsvTemplate();

    FeeUploadResponseDto processUpload(UUID schoolId, MultipartFile file, UUID actorId);

    FeeUploadDetailDto getUploadDetail(UUID schoolId, String uploadId);

    List<FeeUploadRowDto> getUploadRows(UUID schoolId, String uploadId, String status);

    List<FeeUploadRowDto> getUploadErrors(UUID schoolId, String uploadId);

    String exportUploadErrorsCsv(UUID schoolId, String uploadId);

    FeeUploadDetailDto resubmitUpload(UUID schoolId, String uploadId, MultipartFile file, UUID actorId);

    List<FeeUploadSummaryDto> getUploadHistory(UUID schoolId);
}
