package com.knapp.kisoft.mock.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "goods_out_order",
        uniqueConstraints = @UniqueConstraint(name = "uq_goods_out_order_key", columnNames = {"client_number", "order_number", "sheet_number"})
)
public class GoodsOutOrderEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "client_number", nullable = false, length = 64)
    private String clientNumber;
    @Column(name = "order_number", nullable = false, length = 64)
    private String orderNumber;
    @Column(name = "sheet_number", nullable = false, length = 32)
    private String sheetNumber;
    @Column(name = "processing_status", nullable = false, length = 32)
    private String processingStatus;
    @Lob
    @Column(name = "payload_json", nullable = false)
    private String payloadJson;

    protected GoodsOutOrderEntity() {}

    public GoodsOutOrderEntity(String clientNumber, String orderNumber, String sheetNumber, String processingStatus, String payloadJson) {
        this.clientNumber = clientNumber;
        this.orderNumber = orderNumber;
        this.sheetNumber = sheetNumber;
        this.processingStatus = processingStatus;
        this.payloadJson = payloadJson;
    }

    public Long getId() { return id; }
    public String getClientNumber() { return clientNumber; }
    public String getOrderNumber() { return orderNumber; }
    public String getSheetNumber() { return sheetNumber; }
    public String getProcessingStatus() { return processingStatus; }
    public String getPayloadJson() { return payloadJson; }
    public void setProcessingStatus(String processingStatus) { this.processingStatus = processingStatus; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
}
