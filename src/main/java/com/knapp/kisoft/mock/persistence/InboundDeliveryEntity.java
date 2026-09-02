package com.knapp.kisoft.mock.persistence;

import jakarta.persistence.*;

@Entity
@Table(
        name = "inbound_delivery",
        uniqueConstraints = @UniqueConstraint(name = "uq_inbound_delivery_key", columnNames = {"client_number", "inbound_delivery_number"})
)
public class InboundDeliveryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_number", nullable = false, length = 64)
    private String clientNumber;

    @Column(name = "inbound_delivery_number", nullable = false, length = 64)
    private String inboundDeliveryNumber;

    @Column(name = "processing_status", nullable = false, length = 32)
    private String processingStatus;

    @Lob
    @Column(name = "payload_json", nullable = false)
    private String payloadJson;

    protected InboundDeliveryEntity() {}

    public InboundDeliveryEntity(String clientNumber, String inboundDeliveryNumber, String processingStatus, String payloadJson) {
        this.clientNumber = clientNumber;
        this.inboundDeliveryNumber = inboundDeliveryNumber;
        this.processingStatus = processingStatus;
        this.payloadJson = payloadJson;
    }

    public Long getId() { return id; }
    public String getClientNumber() { return clientNumber; }
    public String getInboundDeliveryNumber() { return inboundDeliveryNumber; }
    public String getProcessingStatus() { return processingStatus; }
    public String getPayloadJson() { return payloadJson; }

    public void setProcessingStatus(String processingStatus) { this.processingStatus = processingStatus; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
}

