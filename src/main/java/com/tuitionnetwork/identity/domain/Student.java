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

    @Column(name = "student_ref")
    private String studentRef;

    @Column(name = "grade")
    private String grade;

    @Column(name = "section")
    private String section;

    @Column(name = "status", nullable = false)
    private String status = "Active";

    @Column(name = "deactivated_date")
    private LocalDate deactivatedDate;

    @Column(name = "deactivation_reason")
    private String deactivationReason;

    @Column(name = "parent_name")
    private String parentName;

    @Column(name = "parent_phone")
    private String parentPhone;

    @Column(name = "parent_email")
    private String parentEmail;

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
        this.status = "Active";
    }

    public Student(UUID guardianId, UUID institutionId, String nationalIdHash, String nationalIdEncrypted,
                   String fullName, LocalDate dateOfBirth, String studentRef, String grade, String section,
                   String parentName, String parentPhone, String parentEmail) {
        this.guardianId = guardianId;
        this.institutionId = institutionId;
        this.nationalIdHash = nationalIdHash;
        this.nationalIdEncrypted = nationalIdEncrypted;
        this.fullName = fullName;
        this.dateOfBirth = dateOfBirth;
        this.studentRef = studentRef;
        this.grade = grade;
        this.section = section;
        this.parentName = parentName;
        this.parentPhone = parentPhone;
        this.parentEmail = parentEmail;
        this.status = "Active";
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

    public String getStudentRef() {
        return studentRef;
    }

    public void setStudentRef(String studentRef) {
        this.studentRef = studentRef;
    }

    public String getGrade() {
        return grade;
    }

    public void setGrade(String grade) {
        this.grade = grade;
    }

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section;
    }

    public String getStatus() {
        return status != null ? status : "Active";
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDate getDeactivatedDate() {
        return deactivatedDate;
    }

    public void setDeactivatedDate(LocalDate deactivatedDate) {
        this.deactivatedDate = deactivatedDate;
    }

    public String getDeactivationReason() {
        return deactivationReason;
    }

    public void setDeactivationReason(String deactivationReason) {
        this.deactivationReason = deactivationReason;
    }

    public String getParentName() {
        return parentName;
    }

    public void setParentName(String parentName) {
        this.parentName = parentName;
    }

    public String getParentPhone() {
        return parentPhone;
    }

    public void setParentPhone(String parentPhone) {
        this.parentPhone = parentPhone;
    }

    public String getParentEmail() {
        return parentEmail;
    }

    public void setParentEmail(String parentEmail) {
        this.parentEmail = parentEmail;
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
