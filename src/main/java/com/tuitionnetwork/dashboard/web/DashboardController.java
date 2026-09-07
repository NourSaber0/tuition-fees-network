package com.tuitionnetwork.dashboard.web;

import com.tuitionnetwork.dashboard.dto.DashboardSummaryResponse;
import com.tuitionnetwork.dashboard.dto.InstitutionStatusResponse;
import com.tuitionnetwork.dashboard.dto.RecentTransactionsResponse;
import com.tuitionnetwork.dashboard.dto.WeeklyCollectionsResponse;
import com.tuitionnetwork.dashboard.service.DashboardService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/dashboard")
@PreAuthorize("hasRole('BACK_OFFICE')")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    public ResponseEntity<DashboardSummaryResponse> getSummary() {
        return ResponseEntity.ok(dashboardService.getSummary());
    }

    @GetMapping("/collections/weekly")
    public ResponseEntity<WeeklyCollectionsResponse> getWeeklyCollections(
            @RequestParam(value = "weekOf", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekOf) {
        return ResponseEntity.ok(dashboardService.getWeeklyCollections(weekOf));
    }

    @GetMapping("/institution-status")
    public ResponseEntity<InstitutionStatusResponse> getInstitutionStatus() {
        return ResponseEntity.ok(dashboardService.getInstitutionStatus());
    }

    @GetMapping("/recent-transactions")
    public ResponseEntity<RecentTransactionsResponse> getRecentTransactions(
            @RequestParam(value = "limit", defaultValue = "6") int limit) {
        return ResponseEntity.ok(dashboardService.getRecentTransactions(limit));
    }
}
