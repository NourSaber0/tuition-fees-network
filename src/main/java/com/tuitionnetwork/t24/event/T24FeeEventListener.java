package com.tuitionnetwork.t24.event;

import com.tuitionnetwork.identity.domain.Student;
import com.tuitionnetwork.identity.repository.StudentRepository;
import com.tuitionnetwork.ingestion.event.FeeCreatedEvent;
import com.tuitionnetwork.t24.dto.T24BillingDto.RequestBillingRequest;
import com.tuitionnetwork.t24.service.T24CustomerBillingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
public class T24FeeEventListener {

    private static final Logger log = LoggerFactory.getLogger(T24FeeEventListener.class);
    private final T24CustomerBillingService billingService;
    private final StudentRepository studentRepository;

    public T24FeeEventListener(T24CustomerBillingService billingService, StudentRepository studentRepository) {
        this.billingService = billingService;
        this.studentRepository = studentRepository;
    }

    @ApplicationModuleListener
    public void onFeeCreated(FeeCreatedEvent event) {
        if (event == null || event.feeLine() == null) return;
        log.info("T24 Adapter notified of FeeCreatedEvent for feeLineId={}", event.feeLine().getId());
        try {
            String studentName = "Unknown Student";
            String studentId = event.feeLine().getStudentId().toString();
            if (studentRepository != null) {
                Student student = studentRepository.findById(event.feeLine().getStudentId()).orElse(null);
                if (student != null) {
                    studentName = student.getFullName();
                    studentId = student.getId().toString();
                }
            }
            
            RequestBillingRequest request = new RequestBillingRequest(
                    event.feeLine().getInstitutionId().toString(),
                    studentId,
                    studentName,
                    event.feeLine().getFeeType().name(),
                    event.feeLine().getTotalAmount(),
                    event.feeLine().getCurrency(),
                    event.feeLine().getCollectionPeriod(),
                    event.feeLine().getDueDate()
            );
            
            billingService.registerFeeInT24(request, null);
            log.info("Successfully registered fee {} in T24", event.feeLine().getId());
        } catch (Exception e) {
            log.warn("Non-blocking error propagating fee {} to T24 customer billing: {}",
                    event.feeLine().getId(), e.getMessage());
        }
    }
}
