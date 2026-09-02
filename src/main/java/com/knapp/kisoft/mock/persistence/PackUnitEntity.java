package com.knapp.kisoft.mock.persistence;

import jakarta.persistence.*;

@Entity
@Table(
        name = "pack_unit",
        uniqueConstraints = @UniqueConstraint(name = "uq_pack_unit_key", columnNames = {"client_number", "article_number", "pack_size"})
)
public class PackUnitEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_number", nullable = false, length = 64)
    private String clientNumber;

    @Column(name = "article_number", nullable = false, length = 64)
    private String articleNumber;

    @Column(name = "pack_size", nullable = false, length = 32)
    private String packSize;

    @Lob
    @Column(name = "payload_json", nullable = false)
    private String payloadJson;

    protected PackUnitEntity() {}

    public PackUnitEntity(String clientNumber, String articleNumber, String packSize, String payloadJson) {
        this.clientNumber = clientNumber;
        this.articleNumber = articleNumber;
        this.packSize = packSize;
        this.payloadJson = payloadJson;
    }

    public Long getId() { return id; }
    public String getClientNumber() { return clientNumber; }
    public String getArticleNumber() { return articleNumber; }
    public String getPackSize() { return packSize; }
    public String getPayloadJson() { return payloadJson; }

    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
}

