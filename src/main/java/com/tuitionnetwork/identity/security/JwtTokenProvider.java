package com.tuitionnetwork.identity.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    private final String secretKey;

    public JwtTokenProvider(@Value("${app.security.jwt-secret:default-tuition-jwt-signing-secret-key-32-chars!}") String secretKey) {
        this.secretKey = secretKey;
    }

    /**
     * Generates a signed token containing user ID, email, and mapped GrantedAuthority role.
     */
    public String generateToken(SecurityUserPrincipal principal) {
        String instIdStr = principal.institutionId() != null ? principal.institutionId().toString() : "";
        String payload = principal.userId() + ":" + principal.email() + ":" + principal.role() + ":" + instIdStr;
        String encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        String signature = sign(encodedPayload);
        return encodedPayload + "." + signature;
    }

    /**
     * Parses and validates a token into SecurityUserPrincipal.
     */
    public Optional<SecurityUserPrincipal> validateAndParseToken(String token) {
        if (token == null || !token.contains(".")) {
            return Optional.empty();
        }
        String[] parts = token.split("\\.");
        if (parts.length != 2) {
            return Optional.empty();
        }
        String payload = parts[0];
        String expectedSignature = sign(payload);
        if (!expectedSignature.equals(parts[1])) {
            return Optional.empty();
        }

        try {
            String decoded = new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8);
            String[] userParts = decoded.split(":");
            if (userParts.length < 3) {
                return Optional.empty();
            }
            UUID userId = UUID.fromString(userParts[0]);
            String email = userParts[1];
            String role = userParts[2];
            UUID institutionId = null;
            if (userParts.length >= 4 && !userParts[3].isBlank()) {
                try {
                    institutionId = UUID.fromString(userParts[3]);
                } catch (IllegalArgumentException ignored) {}
            }
            return Optional.of(new SecurityUserPrincipal(userId, email, email, role, institutionId));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] signatureBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(signatureBytes);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign token", e);
        }
    }
}
