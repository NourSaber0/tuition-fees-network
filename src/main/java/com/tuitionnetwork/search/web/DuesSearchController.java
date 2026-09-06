package com.tuitionnetwork.search.web;

import com.tuitionnetwork.search.dto.GuardianDuesResponse;
import com.tuitionnetwork.search.service.SearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/guardian/dues")
public class DuesSearchController {

    private final SearchService searchService;

    public DuesSearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('BACK_OFFICE', 'GUARDIAN')")
    public ResponseEntity<GuardianDuesResponse> searchGuardianDues(
            @RequestHeader(value = "X-Guardian-National-Id", required = false) String nationalIdHeader,
            @RequestParam(value = "parentNationalId", required = false) String nationalIdParam) {

        String nationalId = (nationalIdHeader != null && !nationalIdHeader.isBlank())
                ? nationalIdHeader
                : nationalIdParam;

        if (nationalId == null || nationalId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        GuardianDuesResponse response = searchService.searchDuesByNationalId(nationalId);
        return ResponseEntity.ok(response);
    }
}
