package com.tuitionnetwork.mockbank.web;

import com.tuitionnetwork.mockbank.domain.MockBankSeedData;
import com.tuitionnetwork.mockbank.domain.NationalIdParser;
import com.tuitionnetwork.mockbank.dto.MockBankException;
import com.tuitionnetwork.mockbank.dto.MoiValidateRequest;
import com.tuitionnetwork.mockbank.dto.MoiVerificationResponse;
import com.tuitionnetwork.mockbank.store.MockBankStore;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/moi")
public class MoiValidationController {

    private final MockBankStore store;

    public MoiValidationController(MockBankStore store) {
        this.store = store;
    }

    @PostMapping("/validate")
    public ResponseEntity<MoiVerificationResponse> validateNationalId(@RequestBody(required = false) MoiValidateRequest request) {
        if (request == null || request.nationalId() == null || request.nationalId().isBlank()) {
            throw new MockBankException(HttpStatus.BAD_REQUEST, "INVALID_NATIONAL_ID", "14-digit Egyptian national ID is required");
        }

        String nid = request.nationalId().trim();
        Optional<NationalIdParser.ParsedNationalId> parsedOpt = NationalIdParser.parse(nid);
        if (parsedOpt.isEmpty()) {
            throw new MockBankException(HttpStatus.BAD_REQUEST, "INVALID_NATIONAL_ID", "Invalid national ID format, date or check digit");
        }

        NationalIdParser.ParsedNationalId parsed = parsedOpt.get();
        List<String> reasons = new ArrayList<>();
        String recordStatus = "ACTIVE";
        String fullNameEn = "Egyptian Citizen";
        LocalDate birthDate = parsed.birthDate();
        int age = parsed.age();
        String gender = parsed.gender();
        String governorate = parsed.governorate();

        // Check special digit 10-13 overrides
        String serialDigits = nid.substring(9, 13);
        if ("0000".equals(serialDigits)) {
            recordStatus = "NOT_FOUND";
            reasons.add("NO_MOI_RECORD");
        } else if ("8888".equals(serialDigits)) {
            recordStatus = "DECEASED";
            reasons.add("HOLDER_DECEASED");
        } else if ("9999".equals(serialDigits)) {
            recordStatus = "BLOCKED";
            reasons.add("RECORD_BLOCKED_BY_AUTHORITY");
        } else if (MockBankSeedData.PRESET_NATIONAL_IDS.containsKey(nid)) {
            MockBankSeedData.TestNationalIdRecord preset = MockBankSeedData.PRESET_NATIONAL_IDS.get(nid);
            recordStatus = preset.status();
            fullNameEn = preset.fullNameEn();
            birthDate = preset.birthDate();
            gender = preset.gender();
            governorate = preset.governorate();
            reasons.addAll(preset.defaultReasons());
        }

        // Age check
        if (age < 21 && !reasons.contains("UNDER_MINIMUM_AGE")) {
            reasons.add("UNDER_MINIMUM_AGE");
        }

        // Name match check
        boolean nameMatched = true;
        double matchScore = 1.0;
        if (request.fullName() != null && !request.fullName().isBlank() && !"Egyptian Citizen".equals(fullNameEn)) {
            String cleanInput = request.fullName().trim().toLowerCase();
            String cleanPreset = fullNameEn.trim().toLowerCase();
            String[] inputTokens = cleanInput.split("\\s+");
            String[] presetTokens = cleanPreset.split("\\s+");
            int matches = 0;
            for (String it : inputTokens) {
                for (String pt : presetTokens) {
                    if (it.equals(pt) || pt.contains(it) || it.contains(pt)) {
                        matches++;
                        break;
                    }
                }
            }
            if (matches == 0) {
                nameMatched = false;
                matchScore = 0.0;
                reasons.add("NAME_MISMATCH");
            }
        }

        boolean valid = reasons.isEmpty() && "ACTIVE".equals(recordStatus);
        String verificationId = "ver_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        MoiVerificationResponse response = new MoiVerificationResponse(
                verificationId,
                valid,
                nid,
                recordStatus,
                new MoiVerificationResponse.HolderInfo(
                        fullNameEn,
                        birthDate,
                        age,
                        gender,
                        governorate,
                        parsed.checksumValid()
                ),
                new MoiVerificationResponse.NameMatchInfo(nameMatched, matchScore),
                new MoiVerificationResponse.EligibilityInfo(age >= 21, 21, age >= 21),
                reasons
        );

        store.saveVerification(response);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/verifications/{id}")
    public ResponseEntity<MoiVerificationResponse> getVerification(@PathVariable("id") String id) {
        return store.getVerification(id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new MockBankException(HttpStatus.NOT_FOUND, "VERIFICATION_NOT_FOUND", "Verification record not found: " + id));
    }

    @GetMapping("/verifications")
    public ResponseEntity<List<MoiVerificationResponse>> listVerifications() {
        return ResponseEntity.ok(store.listVerifications());
    }
}
