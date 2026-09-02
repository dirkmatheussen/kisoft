package com.knapp.kisoft.mock.service;

import com.knapp.kisoft.mock.config.KnappMockProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReplyCallbackServiceTest {

    @Mock
    RestTemplate restTemplate;

    @Mock
    WebhookOAuthTokenService oauthTokenService;

    KnappMockProperties properties;
    ReplyCallbackService service;

    @BeforeEach
    void setUp() {
        properties = new KnappMockProperties();
        properties.setReplyCallbackEnabled(true);
        properties.setReplyCallbackUrl("https://api.example/kisoft");

        Executor directExecutor = Runnable::run;
        service = new ReplyCallbackService(properties, restTemplate, oauthTokenService, directExecutor);
    }

    @Test
    void sendInboundDeliveryReply_retriesOnceAfter401() {
        when(oauthTokenService.isConfigured()).thenReturn(true);
        when(oauthTokenService.getAccessToken())
                .thenReturn(Optional.of("old-token"))
                .thenReturn(Optional.of("new-token"));

        doAnswer(invocation -> {
            HttpEntity<?> entity = invocation.getArgument(1);
            String auth = entity.getHeaders().getFirst("Authorization");
            if ("Bearer old-token".equals(auth)) {
                throw HttpClientErrorException.create(
                        HttpStatus.UNAUTHORIZED, "Unauthorized", null, null, StandardCharsets.UTF_8);
            }
            return ResponseEntity.ok("ok");
        }).when(restTemplate).postForEntity(any(String.class), any(HttpEntity.class), eq(String.class));

        service.sendInboundDeliveryReply(new com.knapp.kisoft.mock.api.dto.InboundDeliveryReply(
                "DEFAULT", "123", null, null, "STARTED", null, null));

        verify(oauthTokenService).invalidate();
        verify(restTemplate, org.mockito.Mockito.times(2))
                .postForEntity(any(String.class), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void deliverSync_returnsApicFailureDetails() {
        when(oauthTokenService.isConfigured()).thenReturn(false);

        HttpClientErrorException forbidden = HttpClientErrorException.create(
                HttpStatus.FORBIDDEN, "Forbidden", null,
                "{\"httpCode\":\"403\",\"httpMessage\":\"Forbidden\",\"moreInformation\":\"Not registered to plan\"}"
                        .getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);
        when(restTemplate.postForEntity(any(String.class), any(HttpEntity.class), eq(String.class)))
                .thenThrow(forbidden);

        var result = service.deliverSync("stockCorrected", Map.of("eventId", "1"), "StockCorrected");

        assertThat(result).isPresent();
        assertThat(result.get().delivered()).isFalse();
        assertThat(result.get().callbackHttpStatus()).isEqualTo(403);
        assertThat(result.get().callbackHttpMessage()).isEqualTo("Forbidden");
        assertThat(result.get().moreInformation()).isEqualTo("Not registered to plan");
        assertThat(result.get().errorMessage()).contains("403").contains("Not registered to plan");
    }

    @Test
    void deliverSync_parsesApic500ErrorBody() {
        when(oauthTokenService.isConfigured()).thenReturn(false);

        byte[] apic500Body = ("{\"httpCode\":\"500\",\"httpMessage\":\"URL Open error\","
                + "\"moreInformation\":\"Could not connect to endpoint\"}")
                .getBytes(StandardCharsets.UTF_8);
        HttpServerErrorException serverError = HttpServerErrorException.create(
                HttpStatus.INTERNAL_SERVER_ERROR, "URL Open error", null,
                apic500Body,
                StandardCharsets.UTF_8);
        when(restTemplate.postForEntity(any(String.class), any(HttpEntity.class), eq(String.class)))
                .thenThrow(serverError);

        var result = service.deliverSync("stockReceived", Map.of("eventId", "1"), "StockReceived");

        assertThat(result).isPresent();
        assertThat(result.get().callbackHttpStatus()).isEqualTo(500);
        assertThat(result.get().callbackHttpMessage()).isEqualTo("URL Open error");
        assertThat(result.get().moreInformation()).isEqualTo("Could not connect to endpoint");
        assertThat(result.get().logLine("StockReceived"))
                .contains("Failed to send StockReceived")
                .contains("Could not connect to endpoint");
    }
}
