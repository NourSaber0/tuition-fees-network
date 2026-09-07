package com.tuitionnetwork.identity.config;

import com.tuitionnetwork.identity.domain.BankEmployee;
import com.tuitionnetwork.identity.repository.BankEmployeeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!prod")
public class BankEmployeeDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(BankEmployeeDataInitializer.class);

    private final BankEmployeeRepository bankEmployeeRepository;

    public BankEmployeeDataInitializer(BankEmployeeRepository bankEmployeeRepository) {
        this.bankEmployeeRepository = bankEmployeeRepository;
    }

    @Override
    public void run(String... args) {
        if (bankEmployeeRepository.count() == 0) {
            BankEmployee admin = new BankEmployee(
                    "Mohamed Ali",
                    "mohamed.ali@cibeg.com",
                    "USR-001",
                    "CIB@2026",
                    "Operations",
                    "bank-admin"
            );
            admin.setPhone("+20 10 0000 4821");
            admin.setStatus("Active");
            bankEmployeeRepository.save(admin);
            log.info("Seeded initial Bank Admin user: {} ({})", admin.getName(), admin.getEmail());
        }
    }
}
