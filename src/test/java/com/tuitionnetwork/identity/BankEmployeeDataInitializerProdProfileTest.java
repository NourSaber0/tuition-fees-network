package com.tuitionnetwork.identity;

import com.example.demo.DemoApplication;
import com.tuitionnetwork.identity.config.BankEmployeeDataInitializer;
import com.tuitionnetwork.identity.repository.BankEmployeeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest(classes = DemoApplication.class)
@ActiveProfiles("prod")
class BankEmployeeDataInitializerProdProfileTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private BankEmployeeRepository bankEmployeeRepository;

    @Test
    void testInitializer_isNotLoadedInProdProfile() {
        // Assert that BankEmployeeDataInitializer bean does NOT exist in production profile
        boolean hasInitializerBean = applicationContext.containsBean("bankEmployeeDataInitializer");
        assertFalse(hasInitializerBean, "BankEmployeeDataInitializer must be disabled in prod profile to prevent default credential seeding");
    }
}
