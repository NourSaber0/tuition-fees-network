package com.tuitionnetwork.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "guardian")
public class Guardian {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "national_id_hash", nullable = false, length = 64)
    private String nationalIdHash; // HMAC (For Search)

    @Column(name = "national_id_encrypted", nullable = false, length = 512)
    private String nationalIdEncrypted; // AES-256-GCM (For Rest)

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "phone", nullable = false)
    private String phone;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "cib_account_linked", nullable = false)
    private boolean cibAccountLinked = false;

    public Guardian() {
    }

    public Guardian(String nationalIdHash, String nationalIdEncrypted, String name,
                    String email, String phone, String passwordHash, boolean cibAccountLinked) {
        this.nationalIdHash = nationalIdHash;
        this.nationalIdEncrypted = nationalIdEncrypted;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.passwordHash = passwordHash;
        this.cibAccountLinked = cibAccountLinked;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
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

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public boolean isCibAccountLinked() {
        return cibAccountLinked;
    }

    public void setCibAccountLinked(boolean cibAccountLinked) {
        this.cibAccountLinked = cibAccountLinked;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Guardian guardian)) return false;
        return Objects.equals(id, guardian.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
