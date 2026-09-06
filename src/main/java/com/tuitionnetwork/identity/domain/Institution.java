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
@Table(name = "institution")
public class Institution {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "code", nullable = false, unique = true)
    private String code;

    @Column(name = "fee_absorption_policy")
    private String feeAbsorptionPolicy;

    public Institution() {
    }

    public Institution(String name, String code, String feeAbsorptionPolicy) {
        this.name = name;
        this.code = code;
        this.feeAbsorptionPolicy = feeAbsorptionPolicy;
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
