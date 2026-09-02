package com.knapp.kisoft.mock.persistence;

import jakarta.persistence.*;

/**
 * Tracks per-line received quantity for an inbound delivery during goods-in.
 * Used by IB-02 lifecycle to enforce open quantity (EF-04, EF-05) and to
 * decide when a delivery becomes FINISHED.
 */
@Entity
@Table(
        name = "inbound_delivery_progress",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_inbound_delivery_progress_key",
                columnNames = {"client_number", "inbound_delivery_number", "line_reference"}
        )
)
public class InboundDeliveryProgressEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_number", nullable = false, length = 64)
    private String clientNumber;

    @Column(name = "inbound_delivery_number", nullable = false, length = 64)
    private String inboundDeliveryNumber;

    @Column(name = "line_reference", nullable = false, length = 64)
    private String lineReference;

    @Column(name = "article_number", nullable = false, length = 64)
    private String articleNumber;

    @Column(name = "pack_size", nullable = false, length = 32)
    private String packSize;

    @Column(name = "expected_quantity", nullable = false)
    private int expectedQuantity;

    @Column(name = "received_quantity", nullable = false)
    private int receivedQuantity;

    protected InboundDeliveryProgressEntity() {}

    public InboundDeliveryProgressEntity(String clientNumber, String inboundDeliveryNumber, String lineReference,
                                         String articleNumber, String packSize,
                                         int expectedQuantity, int receivedQuantity) {
        this.clientNumber = clientNumber;
        this.inboundDeliveryNumber = inboundDeliveryNumber;
        this.lineReference = lineReference;
        this.articleNumber = articleNumber;
        this.packSize = packSize;
        this.expectedQuantity = expectedQuantity;
        this.receivedQuantity = receivedQuantity;
    }

    public Long getId() { return id; }
    public String getClientNumber() { return clientNumber; }
    public String getInboundDeliveryNumber() { return inboundDeliveryNumber; }
    public String getLineReference() { return lineReference; }
    public String getArticleNumber() { return articleNumber; }
    public String getPackSize() { return packSize; }
    public int getExpectedQuantity() { return expectedQuantity; }
    public int getReceivedQuantity() { return receivedQuantity; }

    public void setReceivedQuantity(int receivedQuantity) { this.receivedQuantity = receivedQuantity; }

    public int getOpenQuantity() { return expectedQuantity - receivedQuantity; }
}
