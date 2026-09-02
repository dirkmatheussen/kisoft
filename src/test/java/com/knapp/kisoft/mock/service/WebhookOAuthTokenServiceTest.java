package com.knapp.kisoft.mock.service;

import com.knapp.kisoft.mock.config.KnappMockProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebhookOAuthTokenServiceTest {

    @Mock
    RestTemplate restTemplate;

    KnappMockProperties properties;
    WebhookOAuthTokenService service;

    @BeforeEach
    void setUp() {
        properties = new KnappMockProperties();
        properties.setWebhookOauthTenantId("tenant-id");
        properties.setWebhookOauthClientId("client-id");
        properties.setWebhookOauthClientSecret("secret");
        properties.setWebhookOauthScope("api://app/.default");
        service = new WebhookOAuthTokenService(properties, restTemplate);
    }

    @Test
    void getAccessToken_cachesUntilExpiry() {
        stubTokenResponse("token-a", 3600);

        assertThat(service.getAccessToken()).contains("token-a");
        assertThat(service.getAccessToken()).contains("token-a");

        verify(restTemplate, times(1)).postForObject(any(String.class), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    void getAccessToken_renewsAfterExpiry() throws Exception {
        stubTokenResponse("token-a", 120);
        assertThat(service.getAccessToken()).contains("token-a");

        setExpiresAt(Instant.now().minusSeconds(1));
        stubTokenResponse("token-b", 3600);

        assertThat(service.getAccessToken()).contains("token-b");
        verify(restTemplate, times(2)).postForObject(any(String.class), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    void invalidate_forcesNewToken() {
        stubTokenResponse("token-a", 3600);
        assertThat(service.getAccessToken()).contains("token-a");

        service.invalidate();
        stubTokenResponse("token-b", 3600);

        assertThat(service.getAccessToken()).contains("token-b");
        verify(restTemplate, times(2)).postForObject(any(String.class), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    void getAccessToken_whenNotConfigured_returnsEmpty() {
        properties.setWebhookOauthClientSecret("");
        assertThat(service.getAccessToken()).isEmpty();
    }

    private void stubTokenResponse(String token, int expiresIn) {
        when(restTemplate.postForObject(any(String.class), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(Map.of("access_token", token, "expires_in", expiresIn));
    }

    private void setExpiresAt(Instant instant) throws Exception {
        Field field = WebhookOAuthTokenService.class.getDeclaredField("expiresAt");
        field.setAccessible(true);
        field.set(service, instant);
    }
}
