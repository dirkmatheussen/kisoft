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
     * Maximum number of pack-unit master-data records. Default 1000.
     * Override via --knapp.mock.max-records=500 when starting the JAR.
     */
    private int maxRecords = 1000;

    /**
     * Base URL for outgoing KiSoft One → HOST reply webhooks. Combined with
     * {@link #replyCallbackPathPrefix} and the message name, e.g.
     * {base}/oneapi/v1/_webhooks/inboundDeliveryReply on IBM APIC.
     */
    private String replyCallbackUrl;

    /**
     * Path segment between {@link #replyCallbackUrl} and the message name.
     * Default {@code oneapi/v1/_webhooks} for Volvo IBM APIC.
     */
    private String replyCallbackPathPrefix = "oneapi/v1/_webhooks";

    /**
     * Master switch for outgoing webhooks. When false, no callbacks are sent even if
     * {@link #replyCallbackUrl} is set.
     */
    private boolean replyCallbackEnabled = true;

    /**
     * IBM APIC client ID sent as X-IBM-Client-Id header on webhook POSTs. Optional.
     */
    private String webhookIbmClientId;

    /**
     * IBM APIC client secret sent as X-IBM-Client-Secret header on webhook POSTs. Optional.
     */
    private String webhookIbmClientSecret;

    /**
     * When true, IB-02 load-unit receipt also emits optional PostStorageOrderReply(STARTED/FINISHED) per load unit.
     */
    private boolean storageOrderReplyEnabled = false;

    /**
     * When true (default), {@code PostInboundDelivery} immediately books each line's
     * {@code expectedQuantity} into ASRS stock (same articleNumber/packSize keys used by
     * goods-out and inventoryRequestLine). Operator {@code loadUnit} then only tracks
     * physical receipt and does not double-book; {@code finish} corrects shortfalls.
     * Set false for strict IB-02 behaviour (stock only after load-unit confirmation).
     */
    private boolean inboundAutoStock = true;

    /** Microsoft Entra ID tenant for webhook OAuth (client credentials). */
    private String webhookOauthTenantId;

    /** Entra app (client) ID used to obtain the webhook Bearer token. */
    private String webhookOauthClientId;

    /** Entra app client secret for webhook OAuth. */
    private String webhookOauthClientSecret;

    /** OAuth scope, e.g. api://{app-id}/.default */
    private String webhookOauthScope;

    /**
     * When true (default), the homepage and Swagger UI require HTTP Basic Auth.
     * Disable with the {@code dev} or {@code test} profile for local work.
     */
    private boolean uiAuthEnabled = true;

    /** HTTP Basic username for the homepage and Swagger UI. */
    private String uiUsername = "knapp";

    /**
     * HTTP Basic password for the homepage and Swagger UI.
     * Set via {@code MOCK_UI_PASSWORD} or {@code knapp.mock.ui-password}.
     */
    private String uiPassword;

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

    public String getReplyCallbackPathPrefix() {
        return replyCallbackPathPrefix;
    }

    public void setReplyCallbackPathPrefix(String replyCallbackPathPrefix) {
        this.replyCallbackPathPrefix = replyCallbackPathPrefix;
    }

    public boolean isReplyCallbackEnabled() {
        return replyCallbackEnabled;
    }

    public void setReplyCallbackEnabled(boolean replyCallbackEnabled) {
        this.replyCallbackEnabled = replyCallbackEnabled;
    }

    /** True when outgoing KiSoft → HOST webhooks will be sent. */
    public boolean areCallbacksEnabled() {
        return replyCallbackEnabled && replyCallbackUrl != null && !replyCallbackUrl.isBlank();
    }

    /** Full IBM APIC / HOST callback URL for a KiSoft reply message (e.g. inboundDeliveryReply). */
    public String webhookTargetUrl(String messageName) {
        if (replyCallbackUrl == null || replyCallbackUrl.isBlank()) {
            return null;
        }
        String base = replyCallbackUrl.replaceAll("/$", "");
        String name = messageName.replaceAll("^/", "");
        String prefix = replyCallbackPathPrefix;
        if (prefix != null && !prefix.isBlank()) {
            prefix = prefix.replaceAll("^/|/$", "");
            return base + "/" + prefix + "/" + name;
        }
        return base + "/" + name;
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

    public boolean isStorageOrderReplyEnabled() {
        return storageOrderReplyEnabled;
    }

    public void setStorageOrderReplyEnabled(boolean storageOrderReplyEnabled) {
        this.storageOrderReplyEnabled = storageOrderReplyEnabled;
    }

    public boolean isInboundAutoStock() {
        return inboundAutoStock;
    }

    public void setInboundAutoStock(boolean inboundAutoStock) {
        this.inboundAutoStock = inboundAutoStock;
    }

    public String getWebhookOauthTenantId() {
        return webhookOauthTenantId;
    }

    public void setWebhookOauthTenantId(String webhookOauthTenantId) {
        this.webhookOauthTenantId = webhookOauthTenantId;
    }

    public String getWebhookOauthClientId() {
        return webhookOauthClientId;
    }

    public void setWebhookOauthClientId(String webhookOauthClientId) {
        this.webhookOauthClientId = webhookOauthClientId;
    }

    public String getWebhookOauthClientSecret() {
        return webhookOauthClientSecret;
    }

    public void setWebhookOauthClientSecret(String webhookOauthClientSecret) {
        this.webhookOauthClientSecret = webhookOauthClientSecret;
    }

    public String getWebhookOauthScope() {
        return webhookOauthScope;
    }

    public void setWebhookOauthScope(String webhookOauthScope) {
        this.webhookOauthScope = webhookOauthScope;
    }

    public boolean isUiAuthEnabled() {
        return uiAuthEnabled;
    }

    public void setUiAuthEnabled(boolean uiAuthEnabled) {
        this.uiAuthEnabled = uiAuthEnabled;
    }

    public String getUiUsername() {
        return uiUsername;
    }

    public void setUiUsername(String uiUsername) {
        this.uiUsername = uiUsername;
    }

    public String getUiPassword() {
        return uiPassword;
    }

    public void setUiPassword(String uiPassword) {
        this.uiPassword = uiPassword;
    }
}
