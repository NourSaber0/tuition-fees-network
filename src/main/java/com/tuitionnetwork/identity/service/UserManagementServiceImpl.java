package com.tuitionnetwork.identity.service;

import com.tuitionnetwork.audit.domain.AuditLog;
import com.tuitionnetwork.audit.repository.AuditLogRepository;
import com.tuitionnetwork.common.dto.PageResponse;
import com.tuitionnetwork.identity.domain.BankEmployee;
import com.tuitionnetwork.identity.domain.BankRole;
import com.tuitionnetwork.identity.dto.auth.ForgotPasswordRequest;
import com.tuitionnetwork.identity.dto.auth.MessageResponse;
import com.tuitionnetwork.identity.dto.users.BankUserSummaryDto;
import com.tuitionnetwork.identity.dto.users.CreateBankUserRequest;
import com.tuitionnetwork.identity.dto.users.UpdateBankUserRequest;
import com.tuitionnetwork.identity.dto.users.UserStatusResponse;
import com.tuitionnetwork.identity.repository.BankEmployeeRepository;
import com.tuitionnetwork.identity.security.AuthException;
import com.tuitionnetwork.identity.security.SecurityUserPrincipal;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserManagementServiceImpl implements UserManagementService {

    private final BankEmployeeRepository bankEmployeeRepository;
    private final AuditLogRepository auditLogRepository;
    private final BankAuthService bankAuthService;

    public UserManagementServiceImpl(BankEmployeeRepository bankEmployeeRepository,
                                      AuditLogRepository auditLogRepository,
                                      BankAuthService bankAuthService) {
        this.bankEmployeeRepository = bankEmployeeRepository;
        this.auditLogRepository = auditLogRepository;
        this.bankAuthService = bankAuthService;
    }

    @Override
    public PageResponse<BankUserSummaryDto> listUsers(String search, String role, String status, int page, int pageSize) {
        String needle = search != null ? search.trim().toLowerCase() : null;
        Optional<BankRole> roleFilter = role != null && !role.isBlank() ? BankRole.fromString(role) : Optional.empty();

        List<BankEmployee> matching = bankEmployeeRepository.findAll().stream()
                .filter(e -> needle == null || needle.isBlank()
                        || containsIgnoreCase(e.getName(), needle)
                        || containsIgnoreCase(e.getEmployeeId(), needle)
                        || containsIgnoreCase(e.getEmail(), needle)
                        || containsIgnoreCase(e.getRole(), needle))
                .filter(e -> roleFilter.isEmpty() || roleFilter.get().getRoleId().equalsIgnoreCase(e.getRole()))
                .filter(e -> status == null || status.isBlank() || status.equalsIgnoreCase(e.getStatus()))
                .sorted(Comparator.comparing(BankEmployee::getName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();

        int safePage = Math.max(page, 0);
        int safeSize = Math.max(pageSize, 1);
        int fromIndex = Math.min(safePage * safeSize, matching.size());
        int toIndex = Math.min(fromIndex + safeSize, matching.size());

        var pageImpl = new PageImpl<>(matching.subList(fromIndex, toIndex), PageRequest.of(safePage, safeSize), matching.size());
        return PageResponse.from(pageImpl, this::toSummary);
    }

    private boolean containsIgnoreCase(String value, String needle) {
        return value != null && value.toLowerCase().contains(needle);
    }

    @Override
    public Map<String, Long> getSummary() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (BankRole role : BankRole.values()) {
            counts.put(role.getDisplayName(), 0L);
        }
        for (BankEmployee employee : bankEmployeeRepository.findAll()) {
            if (!"Active".equalsIgnoreCase(employee.getStatus())) {
                continue;
            }
            BankRole role = BankRole.fromString(employee.getRole()).orElse(null);
            if (role != null) {
                counts.merge(role.getDisplayName(), 1L, Long::sum);
            }
        }
        return counts;
    }

    @Override
    public BankUserSummaryDto getUser(UUID id) {
        return toSummary(findOrThrow(id));
    }

    @Override
    @Transactional
    public BankUserSummaryDto createUser(CreateBankUserRequest request, SecurityUserPrincipal actor) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "name_required", "Name is required");
        }
        if (request.email() == null || !request.email().contains("@")) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "invalid_email", "A valid email address is required");
        }
        String normalizedEmail = request.email().trim().toLowerCase();
        if (bankEmployeeRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new AuthException(HttpStatus.CONFLICT, "email_exists", "A user with this email already exists");
        }

        BankRole role = request.role() != null
                ? BankRole.fromString(request.role()).orElseThrow(() ->
                        new AuthException(HttpStatus.BAD_REQUEST, "invalid_role", "Unknown role: " + request.role()))
                : BankRole.BANK_OPERATIONS;

        String username = request.username() != null && !request.username().isBlank()
                ? request.username().trim()
                : generateUsername(request.name());
        if (bankEmployeeRepository.findByEmployeeId(username).isPresent()) {
            throw new AuthException(HttpStatus.CONFLICT, "username_taken", "This username is already in use");
        }

        String temporaryPassword = "TEMP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        BankEmployee employee = new BankEmployee(
                request.name().trim(),
                normalizedEmail,
                username,
                temporaryPassword,
                request.department(),
                role.getRoleId()
        );
        employee.setMustChangePassword(true);
        employee = bankEmployeeRepository.save(employee);

        writeAudit(actor, "USER_CREATED", "Created bank user " + employee.getEmail() + " with role " + role.getRoleId());

        return toSummary(employee);
    }

    @Override
    @Transactional
    public BankUserSummaryDto updateUser(UUID id, UpdateBankUserRequest request, SecurityUserPrincipal actor) {
        BankEmployee employee = findOrThrow(id);

        if (request.email() != null && !request.email().isBlank()) {
            String normalizedEmail = request.email().trim().toLowerCase();
            if (!normalizedEmail.equalsIgnoreCase(employee.getEmail())) {
                bankEmployeeRepository.findByEmail(normalizedEmail).ifPresent(existing -> {
                    throw new AuthException(HttpStatus.CONFLICT, "email_exists", "A user with this email already exists");
                });
            }
            employee.setEmail(normalizedEmail);
        }
        if (request.username() != null && !request.username().isBlank()) {
            String username = request.username().trim();
            if (!username.equalsIgnoreCase(employee.getEmployeeId())) {
                bankEmployeeRepository.findByEmployeeId(username).ifPresent(existing -> {
                    throw new AuthException(HttpStatus.CONFLICT, "username_taken", "This username is already in use");
                });
            }
            employee.setEmployeeId(username);
        }
        if (request.name() != null && !request.name().isBlank()) {
            employee.setName(request.name().trim());
        }
        if (request.department() != null) {
            employee.setDepartment(request.department());
        }
        if (request.role() != null && !request.role().isBlank()) {
            BankRole role = BankRole.fromString(request.role())
                    .orElseThrow(() -> new AuthException(HttpStatus.BAD_REQUEST, "invalid_role", "Unknown role: " + request.role()));
            employee.setRole(role.getRoleId());
        }

        employee = bankEmployeeRepository.save(employee);
        writeAudit(actor, "USER_UPDATED", "Updated bank user " + employee.getEmail());

        return toSummary(employee);
    }

    @Override
    @Transactional
    public UserStatusResponse deactivateUser(UUID id, SecurityUserPrincipal actor) {
        BankEmployee employee = findOrThrow(id);
        employee.setStatus("Inactive");
        bankEmployeeRepository.save(employee);
        writeAudit(actor, "USER_DEACTIVATED", "Deactivated bank user " + employee.getEmail());
        return new UserStatusResponse("Inactive");
    }

    @Override
    @Transactional
    public UserStatusResponse activateUser(UUID id, SecurityUserPrincipal actor) {
        BankEmployee employee = findOrThrow(id);
        employee.setStatus("Active");
        bankEmployeeRepository.save(employee);
        writeAudit(actor, "USER_ACTIVATED", "Activated bank user " + employee.getEmail());
        return new UserStatusResponse("Active");
    }

    @Override
    @Transactional
    public MessageResponse triggerPasswordReset(UUID id, SecurityUserPrincipal actor) {
        BankEmployee employee = findOrThrow(id);
        bankAuthService.forgotPassword(new ForgotPasswordRequest(employee.getEmail()));
        writeAudit(actor, "USER_PASSWORD_RESET_TRIGGERED", "Triggered password reset for " + employee.getEmail());
        return new MessageResponse("Password reset email sent");
    }

    private BankEmployee findOrThrow(UUID id) {
        return bankEmployeeRepository.findById(id)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "user_not_found", "Bank user not found"));
    }

    private void writeAudit(SecurityUserPrincipal actor, String action, String targetResource) {
        if (auditLogRepository == null) {
            return;
        }
        UUID actorId = actor != null ? actor.userId() : null;
        auditLogRepository.save(new AuditLog(actorId, "BANK_EMPLOYEE", action, targetResource));
    }

    private String generateUsername(String name) {
        String base = name.trim().toLowerCase().replaceAll("[^a-z0-9]+", ".");
        String candidate = base;
        int suffix = 1;
        while (bankEmployeeRepository.findByEmployeeId(candidate).isPresent()) {
            candidate = base + suffix++;
        }
        return candidate;
    }

    private BankUserSummaryDto toSummary(BankEmployee employee) {
        BankRole role = BankRole.fromString(employee.getRole()).orElse(BankRole.BANK_OPERATIONS);
        return new BankUserSummaryDto(
                employee.getId(),
                employee.getName(),
                employee.getEmployeeId(),
                employee.getEmail(),
                role.getDisplayName(),
                employee.getStatus(),
                employee.getLastLoginAt(),
                employee.getDepartment(),
                employee.getCreatedAt()
        );
    }
}
