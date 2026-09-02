package com.knapp.kisoft.mock.persistence;

import jakarta.persistence.*;

/**
 * Tracks current content of a tote compartment to enforce IB-02 EF-06 (no
 * topping-up) and EF-14 (wrong/mixed SKU). Each (clientNumber, loadUnitCode,
 * compartment) combination may hold at most one (article, packSize) entry.
 */
@Entity
@Table(
        name = "tote_compartment",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_tote_compartment_key",
                columnNames = {"client_number", "load_unit_code", "compartment"}
        )
)
public class ToteCompartmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_number", nullable = false, length = 64)
    private String clientNumber;

    @Column(name = "load_unit_code", nullable = false, length = 64)
    private String loadUnitCode;

    @Column(name = "compartment", nullable = false, length = 32)
    private String compartment;

    @Column(name = "article_number", nullable = false, length = 64)
    private String articleNumber;

    @Column(name = "pack_size", nullable = false, length = 32)
    private String packSize;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    protected ToteCompartmentEntity() {}

    public ToteCompartmentEntity(String clientNumber, String loadUnitCode, String compartment,
                                 String articleNumber, String packSize, int quantity) {
        this.clientNumber = clientNumber;
        this.loadUnitCode = loadUnitCode;
        this.compartment = compartment;
        this.articleNumber = articleNumber;
        this.packSize = packSize;
        this.quantity = quantity;
    }

    public Long getId() { return id; }
    public String getClientNumber() { return clientNumber; }
    public String getLoadUnitCode() { return loadUnitCode; }
    public String getCompartment() { return compartment; }
    public String getArticleNumber() { return articleNumber; }
    public String getPackSize() { return packSize; }
    public int getQuantity() { return quantity; }

    public void setQuantity(int quantity) { this.quantity = quantity; }
}
