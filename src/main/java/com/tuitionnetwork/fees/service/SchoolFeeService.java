package com.tuitionnetwork.fees.service;

import com.tuitionnetwork.common.dto.PageResponse;
import com.tuitionnetwork.fees.dto.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface SchoolFeeService {

    PageResponse<FeeItemSummaryDto> getFees(UUID institutionId,
                                            String search,
                                            String category,
                                            UUID studentId,
                                            String grade,
                                            LocalDate dueDateFrom,
                                            LocalDate dueDateTo,
                                            String status,
                                            int page,
                                            int pageSize);

    FeeDetailDto getFeeById(UUID institutionId, UUID feeId);

    FeeDetailDto createFee(UUID institutionId, CreateFeeRequest request, UUID actorId);

    FeeDetailDto updateFee(UUID institutionId, UUID feeId, UpdateFeeRequest request, UUID actorId);

    void cancelFee(UUID institutionId, UUID feeId, CancelFeeRequest request, UUID actorId);

    FeeDetailDto applyManualPenalty(UUID institutionId, UUID feeId, UUID actorId);

    FeePenaltyInfoDto getFeePenaltyInfo(UUID institutionId, UUID feeId);

    FeeStatsDto getFeeStats(UUID institutionId);

    List<FeeCategoryDto> getFeeCategories();
}
