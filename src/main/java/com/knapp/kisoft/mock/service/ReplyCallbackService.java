package com.knapp.kisoft.mock.service;

import com.knapp.kisoft.mock.api.dto.InboundDeliveryReply;
import com.knapp.kisoft.mock.api.dto.StorageOrderReply;
import com.knapp.kisoft.mock.config.KnappMockProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.Executor;

/**
 * Sends InboundDeliveryReply and StorageOrderReply to the configured callback URL (Option A webhooks).
 * POSTs are done asynchronously so the API response is not blocked.
 */
@Service
public class ReplyCallbackService {

    private static final Logger log = LoggerFactory.getLogger(ReplyCallbackService.class);

    private final KnappMockProperties properties;
    private final RestTemplate restTemplate;
    private final Executor executor;

    public ReplyCallbackService(KnappMockProperties properties, RestTemplate restTemplate,
                                @Qualifier("replyCallbackExecutor") Executor executor) {
        this.properties = properties;
        this.restTemplate = restTemplate;
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
        return headers;
    }

    public void sendInboundDeliveryReply(InboundDeliveryReply reply) {
        String base = properties.getReplyCallbackUrl();
        if (base == null || base.isBlank()) return;
        String url = base.replaceAll("/$", "") + "/inboundDeliveryReply";
        executor.execute(() -> {
            try {
                restTemplate.postForObject(url, new HttpEntity<>(reply, webhookHeaders()), String.class);
                log.debug("Sent InboundDeliveryReply to {}", url);
            } catch (Exception e) {
                log.warn("Failed to send InboundDeliveryReply to {}: {}", url, e.getMessage());
            }
        });
    }

    public void sendStorageOrderReply(StorageOrderReply reply) {
        String base = properties.getReplyCallbackUrl();
        if (base == null || base.isBlank()) return;
        String url = base.replaceAll("/$", "") + "/storageOrderReply";
        executor.execute(() -> {
            try {
                restTemplate.postForObject(url, new HttpEntity<>(reply, webhookHeaders()), String.class);
                log.debug("Sent StorageOrderReply to {}", url);
            } catch (Exception e) {
                log.warn("Failed to send StorageOrderReply to {}: {}", url, e.getMessage());
            }
        });
    }
}
