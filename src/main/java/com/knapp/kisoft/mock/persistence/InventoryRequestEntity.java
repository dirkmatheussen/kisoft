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
        name = "inventory_request",
        uniqueConstraints = @UniqueConstraint(name = "uq_inventory_request_key", columnNames = {"client_number", "request_number"})
)
public class InventoryRequestEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "client_number", nullable = false, length = 64)
    private String clientNumber;
    @Column(name = "request_number", nullable = false, length = 64)
    private String requestNumber;
    @Column(name = "processing_status", nullable = false, length = 32)
    private String processingStatus;
    @Lob
    @Column(name = "payload_json", nullable = false)
    private String payloadJson;

    protected InventoryRequestEntity() {}

    public InventoryRequestEntity(String clientNumber, String requestNumber, String processingStatus, String payloadJson) {
        this.clientNumber = clientNumber;
        this.requestNumber = requestNumber;
        this.processingStatus = processingStatus;
        this.payloadJson = payloadJson;
    }

    public Long getId() { return id; }
    public String getClientNumber() { return clientNumber; }
    public String getRequestNumber() { return requestNumber; }
    public String getProcessingStatus() { return processingStatus; }
    public String getPayloadJson() { return payloadJson; }
    public void setProcessingStatus(String processingStatus) { this.processingStatus = processingStatus; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
}
