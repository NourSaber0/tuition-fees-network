package com.tuitionnetwork.mockbank.web;

import com.tuitionnetwork.mockbank.domain.MockBankSeedData;
import com.tuitionnetwork.mockbank.dto.TestDataResponse;
import com.tuitionnetwork.mockbank.store.MockBankStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class MockTestDataController {

    private final MockBankStore store;

    public MockTestDataController(MockBankStore store) {
        this.store = store;
    }

    @GetMapping("/api/v1/test-data")
    public ResponseEntity<TestDataResponse> getTestData() {
        return ResponseEntity.ok(new TestDataResponse(
                MockBankSeedData.TEST_CARDS,
                MockBankSeedData.PRESET_NATIONAL_IDS
        ));
    }

    @PostMapping("/api/v1/admin/reset")
    public ResponseEntity<Map<String, String>> resetMockBank() {
        store.reset();
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "message", "All mock banking data cleared"
        ));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
