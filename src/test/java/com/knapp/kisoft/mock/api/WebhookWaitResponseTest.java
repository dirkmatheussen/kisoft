package com.knapp.kisoft.mock.api;

import com.knapp.kisoft.mock.api.dto.CallbackDeliveryResult;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookWaitResponseTest {

    @Test
    void returnsApicHttpStatusOnCallbackFailure() {
        CallbackDeliveryResult callback = new CallbackDeliveryResult(
                "https://api.example/kisoft/oneapi/v1/_webhooks/stockReceived",
                false,
                500,
                "{\"httpCode\":\"500\"}",
                "URL Open error",
                "Could not connect to endpoint",
                "500 URL Open error:\"Could not connect to endpoint\"");

        ResponseEntity<?> response = WebhookWaitResponse.of("ignored", "StockReceived", callback);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isInstanceOf(com.knapp.kisoft.mock.api.dto.OneApiOkResponse.class);
        var body = (com.knapp.kisoft.mock.api.dto.OneApiOkResponse) response.getBody();
        assertThat(body.http()).isEqualTo(500);
        assertThat(body.status()).isEqualTo("CALLBACK_FAILED");
        assertThat(body.message()).contains("Failed to send StockReceived");
        assertThat(body.message()).contains("Could not connect to endpoint");
        assertThat(body.callback().moreInformation()).isEqualTo("Could not connect to endpoint");
    }
}
