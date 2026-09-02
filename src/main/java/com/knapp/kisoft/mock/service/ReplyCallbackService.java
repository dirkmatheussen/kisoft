package com.knapp.kisoft.mock.service;

import com.knapp.kisoft.mock.api.dto.CallbackDeliveryResult;
import com.knapp.kisoft.mock.api.dto.GoodsOutOrderReply;
import com.knapp.kisoft.mock.api.dto.InboundDeliveryReply;
import com.knapp.kisoft.mock.api.dto.InventoryReport;
import com.knapp.kisoft.mock.api.dto.InventoryRequestReply;
import com.knapp.kisoft.mock.api.dto.LoadUnitMoved;
import com.knapp.kisoft.mock.api.dto.StockCorrected;
import com.knapp.kisoft.mock.api.dto.StockLockChanged;
import com.knapp.kisoft.mock.api.dto.StockReceived;
import com.knapp.kisoft.mock.api.dto.StorageCapacityReport;
import com.knapp.kisoft.mock.api.dto.StorageOrderReply;
import com.knapp.kisoft.mock.config.KnappMockProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * Sends the in-scope KiSoft One → HOST reply/event messages (HIS Appendix) to the
 * configured callback URL ({@code knapp.mock.reply-callback-url}). Each message is POSTed
 * to {base}/{messageName}. Lifecycle flows use async delivery; Swagger test endpoints
 * can call {@link #deliverSync(String, Object, String)} to include the APIC response in the HTTP reply.
 */
@Service
public class ReplyCallbackService {

    private static final Logger log = LoggerFactory.getLogger(ReplyCallbackService.class);

    private final KnappMockProperties properties;
    private final RestTemplate restTemplate;
    private final WebhookOAuthTokenService oauthTokenService;
    private final Executor executor;

    public ReplyCallbackService(KnappMockProperties properties, RestTemplate restTemplate,
                                WebhookOAuthTokenService oauthTokenService,
                                @Qualifier("replyCallbackExecutor") Executor executor) {
        this.properties = properties;
        this.restTemplate = restTemplate;
        this.oauthTokenService = oauthTokenService;
        this.executor = executor;
    }

    private HttpHeaders webhookHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String clientId = properties.getWebhookIbmClientId();
        if (clientId != null && !clientId.isBlank()) {
            headers.set("X-IBM-Client-Id", clientId);
        }
        String clientSecret = properties.getWebhookIbmClientSecret();
        if (clientSecret != null && !clientSecret.isBlank()) {
            headers.set("X-IBM-Client-Secret", clientSecret);
        }
        oauthTokenService.getAccessToken().ifPresent(token -> headers.setBearerAuth(token));
        return headers;
    }

    /**
     * POST synchronously and return the APIC response (for Swagger / manual testing).
     * Empty when callbacks are disabled or no target URL is configured.
     */
    public Optional<CallbackDeliveryResult> deliverSync(String path, Object payload, String messageName) {
        if (!properties.areCallbacksEnabled()) {
            return Optional.empty();
        }
        String url = properties.webhookTargetUrl(path);
        if (url == null) {
            return Optional.empty();
        }
        CallbackDeliveryResult result = deliverWithResult(url, payload, messageName);
        if (result.delivered()) {
            log.info("Sent {} to {}", messageName, url);
        } else {
            log.warn("Failed to send {} to {}: {}", messageName, url, result.errorMessage());
        }
        return Optional.of(result);
    }

    /** POST {reply-callback-url}/{path} with the given payload, asynchronously. No-op if callbacks disabled. */
    private void post(String path, Object payload, String messageName) {
        if (!properties.areCallbacksEnabled()) {
            log.trace("Skipping {} — callbacks disabled", messageName);
            return;
        }
        String url = properties.webhookTargetUrl(path);
        if (url == null) {
            return;
        }
        executor.execute(() -> {
            CallbackDeliveryResult result = deliverWithResult(url, payload, messageName);
            if (result.delivered()) {
                log.info("Sent {} to {}", messageName, url);
            } else {
                log.warn("Failed to send {} to {}: {}", messageName, url, result.errorMessage());
            }
        });
    }

    private CallbackDeliveryResult deliverWithResult(String url, Object payload, String messageName) {
        HttpHeaders headers = webhookHeaders();
        if (oauthTokenService.isConfigured() && !headers.containsKey(HttpHeaders.AUTHORIZATION)) {
            String msg = "OAuth is configured but no Bearer token could be obtained";
            log.error("Skipping {} to {} — {}", messageName, url, msg);
            return CallbackDeliveryResult.failure(url, msg);
        }
        try {
            return CallbackDeliveryResult.success(url, postOnce(url, payload, headers));
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED && oauthTokenService.isConfigured()) {
                log.info("{} to {} returned 401 — renewing OAuth token and retrying once", messageName, url);
                oauthTokenService.invalidate();
                HttpHeaders retryHeaders = webhookHeaders();
                if (!retryHeaders.containsKey(HttpHeaders.AUTHORIZATION)) {
                    return CallbackDeliveryResult.failure(url, e);
                }
                try {
                    return CallbackDeliveryResult.success(url, postOnce(url, payload, retryHeaders));
                } catch (HttpStatusCodeException retryEx) {
                    return CallbackDeliveryResult.failure(url, retryEx);
                }
            }
            return CallbackDeliveryResult.failure(url, e);
        } catch (Exception e) {
            return CallbackDeliveryResult.failure(url, e.getMessage());
        }
    }

    private ResponseEntity<String> postOnce(String url, Object payload, HttpHeaders headers) {
        return restTemplate.postForEntity(url, new HttpEntity<>(payload, headers), String.class);
    }

    // --- Goods-in & storage ---

    public void sendInboundDeliveryReply(InboundDeliveryReply reply) {
        post("inboundDeliveryReply", reply, "InboundDeliveryReply");
    }

    public void sendStockReceived(StockReceived event) {
        post("stockReceived", event, "StockReceived");
    }

    public void sendStorageOrderReply(StorageOrderReply reply) {
        post("storageOrderReply", reply, "StorageOrderReply");
    }

    // --- Goods-out ---

    public void sendGoodsOutOrderReply(GoodsOutOrderReply reply) {
        post("goodsOutOrderReply", reply, "GoodsOutOrderReply");
    }

    // --- Inventory ---

    public void sendInventoryRequestReply(InventoryRequestReply reply) {
        post("inventoryRequestReply", reply, "InventoryRequestReply");
    }

    // --- Load unit movement / relocation ---

    public void sendLoadUnitMoved(LoadUnitMoved event) {
        post("loadUnitMoved", event, "LoadUnitMoved");
    }

    // --- Stock changes ---

    public void sendStockCorrected(StockCorrected event) {
        post("stockCorrected", event, "StockCorrected");
    }

    public void sendStockLockChanged(StockLockChanged event) {
        post("stockLockChanged", event, "StockLockChanged");
    }

    // --- Stock & capacity reports ---

    public void sendInventoryReport(InventoryReport report) {
        post("inventoryReport", report, "InventoryReport");
    }

    public void sendStorageCapacityReport(StorageCapacityReport report) {
        post("storageCapacityReport", report, "StorageCapacityReport");
    }
}
