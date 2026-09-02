package com.knapp.kisoft.mock.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KnappMockPropertiesTest {

    @Test
    void webhookTargetUrl_apicPathWithWebhooksPrefix() {
        KnappMockProperties props = new KnappMockProperties();
        props.setReplyCallbackUrl("https://apitest-awe.volvo.com/vgcd/external/plwms5d.srv.volvo.com/wms");

        assertThat(props.webhookTargetUrl("inboundDeliveryReply"))
                .isEqualTo("https://apitest-awe.volvo.com/vgcd/external/plwms5d.srv.volvo.com/wms/oneapi/v1/_webhooks/inboundDeliveryReply");
    }

    @Test
    void webhookTargetUrl_withoutPathPrefix() {
        KnappMockProperties props = new KnappMockProperties();
        props.setReplyCallbackUrl("https://callback.example/host");
        props.setReplyCallbackPathPrefix("");

        assertThat(props.webhookTargetUrl("stockReceived"))
                .isEqualTo("https://callback.example/host/stockReceived");
    }
}
