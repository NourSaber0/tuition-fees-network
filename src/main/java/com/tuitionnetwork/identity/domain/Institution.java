package com.tuitionnetwork.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "institution")
public class Institution {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    /** Network short-code (e.g. SCH-001). Unique across the network. */
    @Column(name = "code", nullable = false, unique = true)
    private String code;

    @Column(name = "fee_absorption_policy")
    private String feeAbsorptionPolicy;

    // ── Institution-management fields (Phase 3) ──────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "institution_type", nullable = false)
    private InstitutionType institutionType = InstitutionType.SCHOOL;

    /** Free-text sub-type: International / National / STEM / Public / Private … */
    @Column(name = "sub_type")
    private String subType;

    @Column(name = "city")
    private String city;

    @Column(name = "principal_name")
    private String principalName;

    @Column(name = "phone")
    private String phone;

    @Column(name = "email")
    private String email;

    /** Ministry registration number, e.g. MOEDU-SCH-2026-0831. Unique. */
    @Column(name = "registration_number", unique = true)
    private String registrationNumber;

    @Column(name = "student_count", nullable = false)
    private int studentCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "registration_status", nullable = false)
    private RegistrationStatus registrationStatus = RegistrationStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false)
    private AccountStatus accountStatus = AccountStatus.INACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "integration_status", nullable = false)
    private IntegrationStatus integrationStatus = IntegrationStatus.NOT_INTEGRATED;

    @Column(name = "registered_at", nullable = false)
    private LocalDate registeredAt;

    /** Reason captured when an application is rejected (US-10). */
    @Column(name = "rejection_reason", length = 1024)
    private String rejectionReason;

    @Version
    @Column(name = "version")
    private Long version;

    public Institution() {
    }

    public Institution(String name, String code, String feeAbsorptionPolicy) {
        this.name = name;
        this.code = code;
        this.feeAbsorptionPolicy = feeAbsorptionPolicy;
    }

    @PrePersist
    void onPersist() {
        if (registeredAt == null) {
            registeredAt = LocalDate.now();
        }
        if (registrationStatus == null) {
            registrationStatus = RegistrationStatus.PENDING;
        }
        if (accountStatus == null) {
            accountStatus = AccountStatus.INACTIVE;
        }
        if (integrationStatus == null) {
            integrationStatus = IntegrationStatus.NOT_INTEGRATED;
        }
        if (institutionType == null) {
            institutionType = InstitutionType.SCHOOL;
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getFeeAbsorptionPolicy() {
        return feeAbsorptionPolicy;
    }

    public void setFeeAbsorptionPolicy(String feeAbsorptionPolicy) {
        this.feeAbsorptionPolicy = feeAbsorptionPolicy;
    }

    public InstitutionType getInstitutionType() {
        return institutionType;
    }

    public void setInstitutionType(InstitutionType institutionType) {
        this.institutionType = institutionType;
    }

    public String getSubType() {
        return subType;
    }

    public void setSubType(String subType) {
        this.subType = subType;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getPrincipalName() {
        return principalName;
    }

    public void setPrincipalName(String principalName) {
        this.principalName = principalName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public void setRegistrationNumber(String registrationNumber) {
        this.registrationNumber = registrationNumber;
    }

    public int getStudentCount() {
        return studentCount;
    }

    public void setStudentCount(int studentCount) {
        this.studentCount = studentCount;
    }

    public RegistrationStatus getRegistrationStatus() {
        return registrationStatus;
    }

    public void setRegistrationStatus(RegistrationStatus registrationStatus) {
        this.registrationStatus = registrationStatus;
    }

    public AccountStatus getAccountStatus() {
        return accountStatus;
    }

    public void setAccountStatus(AccountStatus accountStatus) {
        this.accountStatus = accountStatus;
    }

    public IntegrationStatus getIntegrationStatus() {
        return integrationStatus;
    }

    public void setIntegrationStatus(IntegrationStatus integrationStatus) {
        this.integrationStatus = integrationStatus;
    }

    public LocalDate getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(LocalDate registeredAt) {
        this.registeredAt = registeredAt;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Institution that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
