package com.tuitionnetwork.reconciliation;

import com.tuitionnetwork.reconciliation.dto.ReconciliationRunDto;
import com.tuitionnetwork.reconciliation.dto.TriggerRunRequest;
import com.tuitionnetwork.reconciliation.scheduler.ReconciliationScheduler;
import com.tuitionnetwork.reconciliation.service.ReconciliationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReconciliationSchedulerTest {

    private ReconciliationService reconciliationService;
    private ReconciliationScheduler scheduler;

    @BeforeEach
    void setUp() {
        reconciliationService = mock(ReconciliationService.class);
        scheduler = new ReconciliationScheduler(reconciliationService);
    }

    @Test
    void runAutomatedReconciliation_triggersRunSuccessfully() {
        ReconciliationRunDto mockDto = new ReconciliationRunDto(
                UUID.randomUUID(),
                "All Registered Institutions",
                "Composite",
                LocalDate.now(),
                120,
                480000L,
                480000L,
                480000L,
                "Matched",
                LocalDateTime.now(),
                120,
                120,
                0
        );

        when(reconciliationService.triggerRun(any(TriggerRunRequest.class))).thenReturn(mockDto);

        scheduler.runAutomatedReconciliation();

        ArgumentCaptor<TriggerRunRequest> captor = ArgumentCaptor.forClass(TriggerRunRequest.class);
        verify(reconciliationService).triggerRun(captor.capture());

        TriggerRunRequest captured = captor.getValue();
        assertNotNull(captured);
        assertEquals(LocalDate.now(), captured.date());
    }

    @Test
    void runAutomatedReconciliation_handlesServiceFailureGracefully() {
        doThrow(new RuntimeException("Simulated database failure during automated cycle"))
                .when(reconciliationService).triggerRun(any());

        // Must not throw an unhandled exception
        scheduler.runAutomatedReconciliation();

        verify(reconciliationService).triggerRun(any());
    }
}
