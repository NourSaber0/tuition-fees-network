package com.tuitionnetwork.dashboard.web;

import com.tuitionnetwork.dashboard.dto.DashboardSummaryResponse;
import com.tuitionnetwork.dashboard.dto.DeadlineSummaryResponse;
import com.tuitionnetwork.dashboard.dto.InstitutionStatusResponse;
import com.tuitionnetwork.dashboard.dto.RecentTransactionsResponse;
import com.tuitionnetwork.dashboard.dto.SchoolQuickLinkDto;
import com.tuitionnetwork.dashboard.dto.WeeklyCollectionsResponse;
import com.tuitionnetwork.dashboard.service.DashboardService;
import com.tuitionnetwork.dashboard.service.SchoolDashboardService;
import com.tuitionnetwork.identity.security.SecurityUserPrincipal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/dashboard", "/dashboard"})
@PreAuthorize("hasAnyRole('BACK_OFFICE', 'SCHOOL_ADMIN', 'SCHOOL_FINANCE')")
public class DashboardController {

    private final DashboardService dashboardService;
    private final SchoolDashboardService schoolDashboardService;

    @Autowired
    public DashboardController(DashboardService dashboardService, SchoolDashboardService schoolDashboardService) {
        this.dashboardService = dashboardService;
        this.schoolDashboardService = schoolDashboardService;
    }

    @GetMapping("/summary")
    public ResponseEntity<?> getSummary(@AuthenticationPrincipal SecurityUserPrincipal principal) {
        if (principal != null && principal.isSchoolUser()) {
            return ResponseEntity.ok(schoolDashboardService.getSchoolSummary(principal.institutionId()));
        }
        return ResponseEntity.ok(dashboardService.getSummary());
    }

    @GetMapping({"/recent-payments", "/recent-transactions"})
    public ResponseEntity<?> getRecentPayments(
            @RequestParam(value = "limit", defaultValue = "6") int limit,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {
        if (principal != null && principal.isSchoolUser()) {
            return ResponseEntity.ok(schoolDashboardService.getRecentPayments(principal.institutionId(), limit));
        }
        return ResponseEntity.ok(dashboardService.getRecentTransactions(limit));
    }

    @GetMapping("/quick-links")
    public ResponseEntity<List<SchoolQuickLinkDto>> getQuickLinks(
            @AuthenticationPrincipal SecurityUserPrincipal principal) {
        UUID schoolId = principal != null ? principal.institutionId() : null;
        UUID userId = principal != null ? principal.userId() : null;
        return ResponseEntity.ok(schoolDashboardService.getQuickLinks(schoolId, userId));
    }

    @GetMapping("/collections/weekly")
    public ResponseEntity<WeeklyCollectionsResponse> getWeeklyCollections(
            @RequestParam(value = "weekOf", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekOf,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {
        if (principal != null && principal.isSchoolUser()) {
            return ResponseEntity.ok(schoolDashboardService.getSchoolWeeklyCollections(principal.institutionId(), weekOf));
        }
        return ResponseEntity.ok(dashboardService.getWeeklyCollections(weekOf));
    }

    @GetMapping("/institution-status")
    @PreAuthorize("hasRole('BACK_OFFICE')")
    public ResponseEntity<InstitutionStatusResponse> getInstitutionStatus() {
        return ResponseEntity.ok(dashboardService.getInstitutionStatus());
    }

    @GetMapping("/deadline-summary")
    @PreAuthorize("hasRole('BACK_OFFICE')")
    public ResponseEntity<DeadlineSummaryResponse> getDeadlineSummary() {
        return ResponseEntity.ok(dashboardService.getDeadlineSummary());
    }
}
