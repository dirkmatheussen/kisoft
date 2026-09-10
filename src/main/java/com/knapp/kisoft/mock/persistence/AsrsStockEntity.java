package com.knapp.kisoft.mock.persistence;

import com.knapp.kisoft.mock.service.ReservationCodes;
import jakarta.persistence.*;

/**
 * Aggregated ASRS stock per (clientNumber, articleNumber, packSize).
 * Used to enforce MA-01 E1 (no part delete while inventory exists) and mock inventory reads.
 */
@Entity
@Table(
        name = "asrs_stock",
        uniqueConstraints = @UniqueConstraint(name = "uq_asrs_stock_key", columnNames = {"client_number", "article_number", "pack_size", "reservation_code"})
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

    @Column(name = "stock_type", length = 64)
    private String stockType;

    @Column(name = "lot_number", length = 128)
    private String lotNumber;

    @Column(name = "date_mark", length = 64)
    private String dateMark;

    @Column(name = "serial_number", length = 128)
    private String serialNumber;

    @Column(name = "reservation_code", nullable = false, length = 64)
    private String reservationCode = "";

    /** JSON array of lock reason strings, e.g. {@code ["LOCKED_FOR_VISION_CHECK"]}. */
    @Lob
    @Column(name = "stock_lock_reasons")
    private String stockLockReasonsJson;

    protected AsrsStockEntity() {}

    public AsrsStockEntity(String clientNumber, String articleNumber, String packSize,
                           String reservationCode, int quantity) {
        this.clientNumber = clientNumber;
        this.articleNumber = articleNumber;
        this.packSize = packSize;
        this.reservationCode = ReservationCodes.normalize(reservationCode);
        this.quantity = quantity;
    }

    public Long getId() { return id; }
    public String getClientNumber() { return clientNumber; }
    public String getArticleNumber() { return articleNumber; }
    public String getPackSize() { return packSize; }
    public int getQuantity() { return quantity; }
    public String getStockType() { return stockType; }
    public String getLotNumber() { return lotNumber; }
    public String getDateMark() { return dateMark; }
    public String getSerialNumber() { return serialNumber; }
    public String getReservationCode() { return reservationCode; }
    public String getStockLockReasonsJson() { return stockLockReasonsJson; }

    public void setQuantity(int quantity) { this.quantity = quantity; }
    public void setStockType(String stockType) { this.stockType = stockType; }
    public void setLotNumber(String lotNumber) { this.lotNumber = lotNumber; }
    public void setDateMark(String dateMark) { this.dateMark = dateMark; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }
    public void setReservationCode(String reservationCode) {
        this.reservationCode = ReservationCodes.normalize(reservationCode);
    }
    public void setStockLockReasonsJson(String stockLockReasonsJson) { this.stockLockReasonsJson = stockLockReasonsJson; }
}
