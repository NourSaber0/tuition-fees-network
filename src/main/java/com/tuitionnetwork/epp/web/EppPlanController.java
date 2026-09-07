package com.tuitionnetwork.epp.web;

import com.tuitionnetwork.epp.dto.CardValidationRequest;
import com.tuitionnetwork.epp.dto.CardValidationResponse;
import com.tuitionnetwork.epp.dto.CreateEppPlanRequest;
import com.tuitionnetwork.epp.dto.EppPlanDetailDto;
import com.tuitionnetwork.epp.dto.EppPlanListResponse;
import com.tuitionnetwork.epp.dto.EppQuoteRequest;
import com.tuitionnetwork.epp.dto.EppQuoteResponse;
import com.tuitionnetwork.epp.dto.EppScheduleInstallmentDto;
import com.tuitionnetwork.epp.dto.EppSummaryResponse;
import com.tuitionnetwork.epp.dto.UpdateEppPlanStatusRequest;
import com.tuitionnetwork.epp.service.EppPlanService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/epp")
@PreAuthorize("hasRole('BACK_OFFICE')")
public class EppPlanController {

    private final EppPlanService eppPlanService;

    public EppPlanController(EppPlanService eppPlanService) {
        this.eppPlanService = eppPlanService;
    }

    @GetMapping("/plans")
    public ResponseEntity<EppPlanListResponse> listPlans(
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "tenor", required = false) Integer tenor,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "25") int pageSize) {
        return ResponseEntity.ok(eppPlanService.listPlans(search, status, tenor, page, pageSize));
    }

    @GetMapping("/summary")
    public ResponseEntity<EppSummaryResponse> getSummary() {
        return ResponseEntity.ok(eppPlanService.getSummary());
    }

    @GetMapping("/plans/{id}")
    public ResponseEntity<EppPlanDetailDto> getPlan(@PathVariable UUID id) {
        return ResponseEntity.ok(eppPlanService.getPlan(id));
    }

    @GetMapping("/plans/{id}/schedule")
    public ResponseEntity<List<EppScheduleInstallmentDto>> getSchedule(@PathVariable UUID id) {
        return ResponseEntity.ok(eppPlanService.getSchedule(id));
    }

    @PostMapping("/quote")
    public ResponseEntity<EppQuoteResponse> quote(@RequestBody EppQuoteRequest request) {
        return ResponseEntity.ok(eppPlanService.quote(request));
    }

    @PostMapping("/cards/validate")
    public ResponseEntity<CardValidationResponse> validateCard(@RequestBody CardValidationRequest request) {
        return ResponseEntity.ok(eppPlanService.validateCard(request.cardNumber()));
    }

    @PostMapping("/plans")
    public ResponseEntity<EppPlanDetailDto> createPlan(@RequestBody CreateEppPlanRequest request) {
        EppPlanDetailDto created = eppPlanService.createPlan(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/plans/{id}")
    public ResponseEntity<EppPlanDetailDto> updateStatus(@PathVariable UUID id,
                                                           @RequestBody UpdateEppPlanStatusRequest request) {
        return ResponseEntity.ok(eppPlanService.updateStatus(id, request));
    }
}
