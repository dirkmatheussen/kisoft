package com.knapp.kisoft.mock.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "knapp.mock")
public class KnappMockProperties {

    /**
     * Als true: authent removes requests zonder valid OAuth2 token (voor lokaal testen).
     * In productie altijd false.
     */
    private boolean bypassAuth = false;

    /**
     * Maximum number of records for pack units and storage orders. Default 1000.
     * Override via --knapp.mock.max-records=500 when starting the JAR.
     */
    private int maxRecords = 1000;

    /**
     * Base URL for outgoing reply webhooks (Option A). When set, the mock POSTs
     * InboundDeliveryReply to {reply-callback-url}/inboundDeliveryReply and
     * StorageOrderReply to {reply-callback-url}/storageOrderReply.
     */
    private String replyCallbackUrl;

    /**
     * IBM APIC client ID sent as X-IBM-Client-Id header on webhook POSTs. Optional.
     */
    private String webhookIbmClientId;

    /**
     * IBM APIC client secret sent as X-IBM-Client-Secret header on webhook POSTs. Optional.
     */
    private String webhookIbmClientSecret;

    public boolean isBypassAuth() {
        return bypassAuth;
    }

    public void setBypassAuth(boolean bypassAuth) {
        this.bypassAuth = bypassAuth;
    }

    public int getMaxRecords() {
        return maxRecords;
    }

    public void setMaxRecords(int maxRecords) {
        this.maxRecords = maxRecords;
    }

    public String getReplyCallbackUrl() {
        return replyCallbackUrl;
    }

    public void setReplyCallbackUrl(String replyCallbackUrl) {
        this.replyCallbackUrl = replyCallbackUrl;
    }

    public String getWebhookIbmClientId() {
        return webhookIbmClientId;
    }

    public void setWebhookIbmClientId(String webhookIbmClientId) {
        this.webhookIbmClientId = webhookIbmClientId;
    }

    public String getWebhookIbmClientSecret() {
        return webhookIbmClientSecret;
    }

    public void setWebhookIbmClientSecret(String webhookIbmClientSecret) {
        this.webhookIbmClientSecret = webhookIbmClientSecret;
    }
}
