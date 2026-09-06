package com.tuitionnetwork.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "student")
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "guardian_id")
    private UUID guardianId;

    @Column(name = "institution_id")
    private UUID institutionId;

    @Column(name = "national_id_hash", nullable = false, length = 64)
    private String nationalIdHash; // HMAC (For Search)

    @Column(name = "national_id_encrypted", nullable = false, length = 512)
    private String nationalIdEncrypted; // AES-256-GCM (For Rest)

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    public Student() {
    }

    public Student(String nationalIdHash, String nationalIdEncrypted, String fullName, LocalDate dateOfBirth) {
        this(null, null, nationalIdHash, nationalIdEncrypted, fullName, dateOfBirth);
    }

    public Student(UUID guardianId, UUID institutionId, String nationalIdHash, String nationalIdEncrypted, String fullName, LocalDate dateOfBirth) {
        this.guardianId = guardianId;
        this.institutionId = institutionId;
        this.nationalIdHash = nationalIdHash;
        this.nationalIdEncrypted = nationalIdEncrypted;
        this.fullName = fullName;
        this.dateOfBirth = dateOfBirth;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getGuardianId() {
        return guardianId;
    }

    public void setGuardianId(UUID guardianId) {
        this.guardianId = guardianId;
    }

    public UUID getInstitutionId() {
        return institutionId;
    }

    public void setInstitutionId(UUID institutionId) {
        this.institutionId = institutionId;
    }

    public String getNationalIdHash() {
        return nationalIdHash;
    }

    public void setNationalIdHash(String nationalIdHash) {
        this.nationalIdHash = nationalIdHash;
    }

    public String getNationalIdEncrypted() {
        return nationalIdEncrypted;
    }

    public void setNationalIdEncrypted(String nationalIdEncrypted) {
        this.nationalIdEncrypted = nationalIdEncrypted;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Student student)) return false;
        return Objects.equals(id, student.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
