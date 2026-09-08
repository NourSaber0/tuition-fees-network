package com.tuitionnetwork.settings.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;

/**
 * A configurable fee category schools may submit (Settings › Fee Types).
 * Broader than the fixed {@code billing.FeeType} enum used by the ledger.
 */
@Entity
@Table(name = "fee_type_setting")
public class FeeTypeSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "code", nullable = false, unique = true, length = 32)
    private String code;

    @Column(name = "taxable", nullable = false)
    private boolean taxable;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public FeeTypeSetting() {
    }

    public FeeTypeSetting(String name, String code, boolean taxable, boolean active) {
        this.name = name;
        this.code = code;
        this.taxable = taxable;
        this.active = active;
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

    public boolean isTaxable() {
        return taxable;
    }

    public void setTaxable(boolean taxable) {
        this.taxable = taxable;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FeeTypeSetting that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
