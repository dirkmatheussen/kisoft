package com.knapp.kisoft.mock.service;

import com.knapp.kisoft.mock.config.KnappMockProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Obtains and caches a Microsoft Entra ID access token (client credentials) for outgoing
 * webhook POSTs through IBM APIC. Refreshes proactively before expiry and after {@link #invalidate()}.
 */
@Service
public class WebhookOAuthTokenService {

    private static final Logger log = LoggerFactory.getLogger(WebhookOAuthTokenService.class);
    private static final int EXPIRY_BUFFER_SECONDS = 60;

    private final KnappMockProperties properties;
    private final RestTemplate restTemplate;

    private volatile String cachedToken;
    private volatile Instant expiresAt = Instant.EPOCH;

    public WebhookOAuthTokenService(KnappMockProperties properties, RestTemplate restTemplate) {
        this.properties = properties;
        this.restTemplate = restTemplate;
    }

    public boolean isConfigured() {
        return !blank(properties.getWebhookOauthTenantId())
                && !blank(properties.getWebhookOauthClientId())
                && !blank(properties.getWebhookOauthClientSecret())
                && !blank(properties.getWebhookOauthScope());
    }

    /** Clears the cached token so the next {@link #getAccessToken()} call fetches a new one. */
    public void invalidate() {
        synchronized (this) {
            cachedToken = null;
            expiresAt = Instant.EPOCH;
        }
    }

    /** Returns a valid Bearer access token, refreshing when expired or invalidated. */
    public Optional<String> getAccessToken() {
        if (!isConfigured()) {
            return Optional.empty();
        }
        Instant now = Instant.now();
        if (cachedToken != null && now.isBefore(expiresAt)) {
            return Optional.of(cachedToken);
        }
        synchronized (this) {
            now = Instant.now();
            if (cachedToken != null && now.isBefore(expiresAt)) {
                return Optional.of(cachedToken);
            }
            boolean renewing = cachedToken != null;
            if (renewing) {
                log.info("Webhook OAuth access token expired — renewing");
            }
            return fetchToken(renewing);
        }
    }

    @SuppressWarnings("unchecked")
    private Optional<String> fetchToken(boolean renewing) {
        String tenantId = properties.getWebhookOauthTenantId();
        String tokenUrl = "https://login.microsoftonline.com/" + tenantId + "/oauth2/v2.0/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", properties.getWebhookOauthClientId());
        body.add("client_secret", properties.getWebhookOauthClientSecret());
        body.add("grant_type", "client_credentials");
        body.add("scope", properties.getWebhookOauthScope());

        try {
            Map<String, Object> response = restTemplate.postForObject(
                    tokenUrl, new HttpEntity<>(body, headers), Map.class);
            if (response == null || response.get("access_token") == null) {
                log.warn("Webhook OAuth token response missing access_token");
                clearCache();
                return Optional.empty();
            }
            String token = response.get("access_token").toString();
            int expiresIn = response.get("expires_in") instanceof Number n ? n.intValue() : 3600;
            cachedToken = token;
            expiresAt = Instant.now().plusSeconds(Math.max(0, expiresIn - EXPIRY_BUFFER_SECONDS));
            if (renewing) {
                log.info("Renewed webhook OAuth access token (expires in {}s, refresh before {}s)",
                        expiresIn, EXPIRY_BUFFER_SECONDS);
            } else {
                log.info("Acquired webhook OAuth access token (expires in {}s)", expiresIn);
            }
            return Optional.of(token);
        } catch (Exception e) {
            clearCache();
            log.warn("Failed to {} webhook OAuth token: {}",
                    renewing ? "renew" : "obtain", e.getMessage());
            return Optional.empty();
        }
    }

    private void clearCache() {
        cachedToken = null;
        expiresAt = Instant.EPOCH;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
