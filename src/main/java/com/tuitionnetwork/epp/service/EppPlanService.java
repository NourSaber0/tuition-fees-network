package com.tuitionnetwork.epp.service;

import com.tuitionnetwork.epp.dto.CardValidationResponse;
import com.tuitionnetwork.epp.dto.CreateEppPlanRequest;
import com.tuitionnetwork.epp.dto.EppPlanDetailDto;
import com.tuitionnetwork.epp.dto.EppPlanListResponse;
import com.tuitionnetwork.epp.dto.EppQuoteRequest;
import com.tuitionnetwork.epp.dto.EppQuoteResponse;
import com.tuitionnetwork.epp.dto.EppScheduleInstallmentDto;
import com.tuitionnetwork.epp.dto.EppSummaryResponse;
import com.tuitionnetwork.epp.dto.UpdateEppPlanStatusRequest;

import java.util.List;
import java.util.UUID;

public interface EppPlanService {

    EppPlanListResponse listPlans(String search, String status, Integer tenor, int page, int pageSize);

    EppSummaryResponse getSummary();

    EppPlanDetailDto getPlan(UUID planId);

    List<EppScheduleInstallmentDto> getSchedule(UUID planId);

    EppQuoteResponse quote(EppQuoteRequest request);

    CardValidationResponse validateCard(String cardNumber);

    EppPlanDetailDto createPlan(CreateEppPlanRequest request);

    EppPlanDetailDto updateStatus(UUID planId, UpdateEppPlanStatusRequest request);
}
