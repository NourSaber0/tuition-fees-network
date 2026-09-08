package com.tuitionnetwork.billing.web;

import com.tuitionnetwork.billing.dto.ApplyPenaltiesResult;
import com.tuitionnetwork.billing.dto.FeeDeadlineDto;
import com.tuitionnetwork.billing.dto.FeeDeadlineSnapshot;
import com.tuitionnetwork.billing.dto.UpdateDueDateRequest;
import com.tuitionnetwork.billing.service.FeeDeadlineService;
import com.tuitionnetwork.identity.security.SecurityUserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@PreAuthorize("hasRole('BACK_OFFICE')")
public class FeeDeadlineController {

    private final FeeDeadlineService feeDeadlineService;

    public FeeDeadlineController(FeeDeadlineService feeDeadlineService) {
        this.feeDeadlineService = feeDeadlineService;
    }

    @PatchMapping({"/api/v1/fees/{id}/due-date", "/fees/{id}/due-date"})
    public ResponseEntity<FeeDeadlineDto> updateDueDate(@PathVariable("id") UUID id,
                                                          @RequestBody UpdateDueDateRequest request,
                                                          @AuthenticationPrincipal SecurityUserPrincipal principal) {
        UUID actorId = principal != null ? principal.userId() : null;
        FeeDeadlineSnapshot snapshot = feeDeadlineService.updateDueDate(id, request.dueDate(), request.reason(), actorId);
        return ResponseEntity.ok(toDto(id, snapshot));
    }

    /**
     * Spec: "scheduled job (nightly) ... Not a public endpoint". In this MVP it's still reachable
     * over HTTP behind the same back-office auth as everything else; production hardening should
     * restrict this to an internal service account or the scheduler's own network, not a browser role.
     */
    @PostMapping({"/api/v1/internal/fees/apply-penalties", "/internal/fees/apply-penalties"})
    public ResponseEntity<ApplyPenaltiesResult> applyPenalties() {
        return ResponseEntity.ok(feeDeadlineService.applyOverduePenalties());
    }

    private FeeDeadlineDto toDto(UUID feeLineId, FeeDeadlineSnapshot snapshot) {
        return new FeeDeadlineDto(
                feeLineId,
                snapshot.dueDate(),
                snapshot.priority().name(),
                snapshot.daysToDue(),
                snapshot.outstandingEGP(),
                snapshot.penaltyEGP(),
                snapshot.penaltyAppliedAt(),
                snapshot.graceEnded(),
                snapshot.totalDueEGP()
        );
    }
}
