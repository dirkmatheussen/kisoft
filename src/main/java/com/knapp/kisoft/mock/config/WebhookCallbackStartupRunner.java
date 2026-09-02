package com.knapp.kisoft.mock.config;

import com.knapp.kisoft.mock.service.WebhookOAuthTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Logs outgoing webhook configuration at startup and validates OAuth token acquisition when configured.
 */
@Component
public class WebhookCallbackStartupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(WebhookCallbackStartupRunner.class);

    private final KnappMockProperties properties;
    private final WebhookOAuthTokenService oauthTokenService;

    public WebhookCallbackStartupRunner(KnappMockProperties properties,
                                        WebhookOAuthTokenService oauthTokenService) {
        this.properties = properties;
        this.oauthTokenService = oauthTokenService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.areCallbacksEnabled()) {
            if (!properties.isReplyCallbackEnabled()) {
                log.warn("Outgoing KiSoft → HOST webhooks are DISABLED (knapp.mock.reply-callback-enabled=false)");
            } else {
                log.warn("Outgoing KiSoft → HOST webhooks are DISABLED (knapp.mock.reply-callback-url is blank)");
            }
            return;
        }

        log.info("Outgoing KiSoft → HOST webhooks ENABLED → {}", properties.getReplyCallbackUrl());
        log.info("  Example: POST {}", properties.webhookTargetUrl("inboundDeliveryReply"));
        log.info("  IBM APIC headers: X-IBM-Client-Id={}",
                masked(properties.getWebhookIbmClientId()));
        log.info("  IBM APIC headers: X-IBM-Client-Secret={}",
                properties.getWebhookIbmClientSecret() != null && !properties.getWebhookIbmClientSecret().isBlank()
                        ? "configured" : "missing");

        if (oauthTokenService.isConfigured()) {
            oauthTokenService.getAccessToken()
                    .ifPresentOrElse(
                            token -> log.info("  Entra ID Bearer token: acquired (length={})", token.length()),
                            () -> log.error("  Entra ID Bearer token: FAILED — webhooks will be skipped until token can be obtained"));
        } else {
            log.warn("  Entra ID OAuth not fully configured — webhooks will be sent without Authorization Bearer");
        }
    }

    private static String masked(String value) {
        if (value == null || value.isBlank()) return "missing";
        if (value.length() <= 8) return "configured";
        return value.substring(0, 4) + "…" + value.substring(value.length() - 4);
    }
}
