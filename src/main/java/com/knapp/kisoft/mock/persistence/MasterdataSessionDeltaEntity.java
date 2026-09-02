package com.knapp.kisoft.mock.persistence;

import jakarta.persistence.*;

@Entity
@Table(
        name = "masterdata_session_delta",
        uniqueConstraints = @UniqueConstraint(name = "uq_delta_domain_client_key", columnNames = {"domain", "client_number", "key_value"})
)
public class MasterdataSessionDeltaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "domain", nullable = false, length = 32)
    private String domain;

    @Column(name = "client_number", nullable = false, length = 64)
    private String clientNumber;

    @Column(name = "key_value", nullable = false, length = 256)
    private String keyValue;

    protected MasterdataSessionDeltaEntity() {}

    public MasterdataSessionDeltaEntity(String domain, String clientNumber, String keyValue) {
        this.domain = domain;
        this.clientNumber = clientNumber;
        this.keyValue = keyValue;
    }

    public Long getId() { return id; }
    public String getDomain() { return domain; }
    public String getClientNumber() { return clientNumber; }
    public String getKeyValue() { return keyValue; }
}

