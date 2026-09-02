package com.knapp.kisoft.mock.persistence;

import jakarta.persistence.*;

/**
 * Aggregated ASRS stock per (clientNumber, articleNumber, packSize).
 * Used to enforce MA-01 E1 (no part delete while inventory exists).
 */
@Entity
@Table(
        name = "asrs_stock",
        uniqueConstraints = @UniqueConstraint(name = "uq_asrs_stock_key", columnNames = {"client_number", "article_number", "pack_size"})
)
public class AsrsStockEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_number", nullable = false, length = 64)
    private String clientNumber;

    @Column(name = "article_number", nullable = false, length = 64)
    private String articleNumber;

    @Column(name = "pack_size", nullable = false, length = 32)
    private String packSize;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    protected AsrsStockEntity() {}

    public AsrsStockEntity(String clientNumber, String articleNumber, String packSize, int quantity) {
        this.clientNumber = clientNumber;
        this.articleNumber = articleNumber;
        this.packSize = packSize;
        this.quantity = quantity;
    }

    public Long getId() { return id; }
    public String getClientNumber() { return clientNumber; }
    public String getArticleNumber() { return articleNumber; }
    public String getPackSize() { return packSize; }
    public int getQuantity() { return quantity; }

    public void setQuantity(int quantity) { this.quantity = quantity; }
}
