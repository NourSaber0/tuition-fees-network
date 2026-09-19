package com.tuitionnetwork;

import com.tuitionnetwork.epp.infrastructure.MockBankEppClient;
import com.tuitionnetwork.identity.infrastructure.MockBankCustomerClient;
import com.tuitionnetwork.identity.infrastructure.MockBankMoiClient;
import com.tuitionnetwork.payments.infrastructure.MockBankBackOfficeClient;
import com.tuitionnetwork.payments.spi.GatewayResponse;
import com.tuitionnetwork.payments.domain.PaymentStatus;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;

@TestConfiguration
public class MockBankTestConfig {

    @Bean
    @Primary
    public MockBankBackOfficeClient mockBankBackOfficeClient() {
        MockBankBackOfficeClient mock = Mockito.mock(MockBankBackOfficeClient.class);
        GatewayResponse success = new GatewayResponse(PaymentStatus.CAPTURED, "AUTH-123", "TXN-123");
        
        Mockito.when(mock.processBackOfficePayment(any(), any(), any())).thenReturn(success);
        Mockito.when(mock.processCardPayment(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(success);
        Mockito.when(mock.processPosPayment(any(), any(), any(), any())).thenReturn(success);
        
        return mock;
    }

    @Bean
    @Primary
    public MockBankEppClient mockBankEppClient() {
        MockBankEppClient mock = Mockito.mock(MockBankEppClient.class);
        
        Mockito.when(mock.getQuotes(any())).thenAnswer(invocation -> {
            java.math.BigDecimal principal = invocation.getArgument(0);
            java.math.BigDecimal twelveRepay = principal.multiply(new java.math.BigDecimal("1.15"));
            java.math.BigDecimal twelveMonth = twelveRepay.divide(new java.math.BigDecimal("12"), 2, java.math.RoundingMode.HALF_UP);
            java.math.BigDecimal threeMonth = principal.divide(new java.math.BigDecimal("3"), 2, java.math.RoundingMode.HALF_UP);
            
            return List.of(
                    new MockBankEppClient.EppQuoteDto(3, threeMonth.toString(), "0.00", principal.toString()),
                    new MockBankEppClient.EppQuoteDto(12, twelveMonth.toString(), twelveRepay.subtract(principal).toString(), twelveRepay.toString())
            );
        });
        
        Mockito.when(mock.createPlan(any(), anyInt())).thenReturn(
                new MockBankEppClient.EppPlanResponse("PLAN-123", "PAY-123", 12, "1150.00", "13800.00", List.of())
        );
        
        return mock;
    }

    @Bean
    @Primary
    public MockBankCustomerClient mockBankCustomerClient() {
        MockBankCustomerClient mock = Mockito.mock(MockBankCustomerClient.class);
        
        Mockito.when(mock.getCustomerByNationalId(any())).thenReturn(
                new MockBankCustomerClient.CustomerResponse(
                        "CUST-123", "Mona Samir", "29805150101023",
                        List.of(new MockBankCustomerClient.AccountDto("ACC-123", "100012345678", "SAVINGS", "EGP", 50000.0, "ACTIVE")),
                        List.of(new MockBankCustomerClient.CardDto("CARD-123", "4111********1111", "VISA", "CREDIT", "Mona Samir", "12/28", "ACTIVE", 20000.0, 15000.0, "ACC-123"))
                )
        );
        
        return mock;
    }

    @Bean
    @Primary
    public MockBankMoiClient mockBankMoiClient() {
        MockBankMoiClient mock = Mockito.mock(MockBankMoiClient.class);
        Mockito.when(mock.validateNationalId(any())).thenReturn(true);
        return mock;
    }
}
