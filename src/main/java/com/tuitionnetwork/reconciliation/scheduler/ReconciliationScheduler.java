package com.tuitionnetwork.reconciliation.scheduler;

import com.tuitionnetwork.reconciliation.dto.TriggerRunRequest;
import com.tuitionnetwork.reconciliation.service.ReconciliationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Automated 6-hour batch cycle for 3-way reconciliation across CIB bank statements,
 * Tuition Network ledger, and educational institutions' SIS submissions.
 * Conforms to Back-Office Reconciliation specification (US-54, US-55).
 */
@Component
@ConditionalOnProperty(name = "app.reconciliation.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class ReconciliationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationScheduler.class);

    private final ReconciliationService reconciliationService;

    @Autowired
    public ReconciliationScheduler(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    /**
     * Executes automated 3-way reconciliation cycle every 6 hours (00:00, 06:00, 12:00, 18:00).
     */
    @Scheduled(cron = "${app.reconciliation.cron:0 0 */6 * * *}")
    public void runAutomatedReconciliation() {
        log.info("Starting automated 6-hour 3-way reconciliation batch cycle...");
        try {
            TriggerRunRequest request = new TriggerRunRequest(LocalDate.now(), null);
            var result = reconciliationService.triggerRun(request);
            log.info("Automated reconciliation cycle completed successfully. Run ID: {}, Status: {}, Matched: {}, Exceptions: {}",
                    result.id(), result.status(), result.matchedCount(), result.exceptionCount());
        } catch (Exception ex) {
            log.error("Automated reconciliation batch cycle failed: {}", ex.getMessage(), ex);
        }
    }
}
