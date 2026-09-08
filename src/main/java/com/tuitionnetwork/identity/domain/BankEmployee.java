package com.tuitionnetwork.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "bank_employee")
public class BankEmployee {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "employee_id", nullable = false, unique = true)
    private String employeeId;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "department", nullable = false)
    private String department; // e.g., Operations, Support

    @Column(name = "role")
    private String role = "bank-operations";

    @Column(name = "status")
    private String status = "Active";

    @Column(name = "phone")
    private String phone = "+20 10 0000 4821";

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "must_change_password")
    private boolean mustChangePassword = false;

    @Column(name = "failed_login_attempts")
    private int failedLoginAttempts = 0;

    @Column(name = "account_locked")
    private boolean accountLocked = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public BankEmployee() {
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    public BankEmployee(String name, String email, String employeeId, String passwordHash, String department) {
        this.name = name;
        this.email = email;
        this.employeeId = employeeId;
        this.passwordHash = passwordHash;
        this.department = department;
        this.role = resolveDefaultRole(department);
        this.status = "Active";
        this.phone = "+20 10 0000 4821";
        this.mustChangePassword = false;
        this.failedLoginAttempts = 0;
        this.accountLocked = false;
    }

    public BankEmployee(String name, String email, String employeeId, String passwordHash, String department, String role) {
        this(name, email, employeeId, passwordHash, department);
        this.role = role != null ? role : resolveDefaultRole(department);
    }

    private static String resolveDefaultRole(String dept) {
        if (dept == null) return "bank-operations";
        String lower = dept.toLowerCase();
        if (lower.contains("admin") || lower.contains("it")) return "bank-admin";
        if (lower.contains("finance")) return "bank-finance";
        if (lower.contains("recon")) return "bank-reconciliation";
        return "bank-operations";
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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(LocalDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public void setMustChangePassword(boolean mustChangePassword) {
        this.mustChangePassword = mustChangePassword;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public void setFailedLoginAttempts(int failedLoginAttempts) {
        this.failedLoginAttempts = failedLoginAttempts;
    }

    public boolean isAccountLocked() {
        return accountLocked;
    }

    public void setAccountLocked(boolean accountLocked) {
        this.accountLocked = accountLocked;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BankEmployee that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
